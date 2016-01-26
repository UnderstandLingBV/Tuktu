package tuktu.api

import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Enumeratee.CheckDone
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Step
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Cont
import play.api.libs.iteratee.Enumeratee.Grouped
import scala.concurrent.ExecutionContext
import play.api.libs.iteratee.Error
import scala.concurrent.Promise
import scala.concurrent.Future
import play.api.libs.iteratee.Enumerator.TreatCont0
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Bloody buffering helpers with proper back pressure and all that
 */
object BufferEnumeratee {
    trait TreatCont0[E] {

        def apply[A](loop: Iteratee[E, A] => Unit, k: Input[E] => Iteratee[E, A])

    }

    //An abstract Enumerator constructor that doesn't leak ( doesn't use flatMap ), mind that it is more imperative (using Unit)
    def checkContinue0[E](inner: TreatCont0[E]) = new Enumerator[E] {

        def apply[A](it: Iteratee[E, A]): Future[Iteratee[E, A]] = {
            val p = Promise[Iteratee[E, A]]()
            def step(it: Iteratee[E, A]) {
                it.pureFold {
                    case Step.Done(a, e) =>
                        p.success(Done(a, e))
                    case Step.Cont(k) =>
                        inner[A](step, k)
                    case Step.Error(msg, e) =>
                        p.success(Error(msg, e))
                }
            }

            step(it)
            p.future
        }
    }

    def generateM[E](e: => Future[Option[E]]): Enumerator[E] = checkContinue0(new TreatCont0[E] {

        def apply[A](loop: Iteratee[E, A] => Unit, k: Input[E] => Iteratee[E, A]) = e.foreach {
            case Some(e) => loop(k(Input.El(e)))
            case None    => loop(Cont(k))
        }
    })

    def repeat[E](e: => E): Enumerator[E] = checkContinue0(new TreatCont0[E] {

        def apply[A](loop: Iteratee[E, A] => Unit, k: Input[E] => Iteratee[E, A]) = loop(k(Input.El(e)))

    })

    // Buffer if socket not ready
    def buffer[E](maxBuffer: Int): Enumeratee[E, List[E]] = new Enumeratee[E, List[E]] {

        import scala.util.{ Try, Failure, Success }

        import scala.collection.immutable.Queue
        import scala.concurrent.stm._
        import play.api.libs.iteratee.Enumeratee.CheckDone

        def applyOn[A](it: Iteratee[List[E], A]): Iteratee[E, Iteratee[List[E], A]] = {

            val last = Promise[Iteratee[E, Iteratee[List[E], A]]]()

            sealed trait State
            case class Queueing(q: Queue[Input[E]], length: Long) extends State
            case class Waiting(p: scala.concurrent.Promise[Input[List[E]]]) extends State
            case class DoneIt(s: Iteratee[E, Iteratee[List[E], A]]) extends State
            case class InPause(q: Queue[Input[E]], p: Promise[Unit]) extends State

            trait ProducerAction
            case object Continue extends ProducerAction
            case class PauseProducer(p: Future[Unit]) extends ProducerAction
            case class ResumeConsumer(p: Promise[Input[List[E]]]) extends ProducerAction
            case class Finish(it: Iteratee[E, Iteratee[List[E], A]]) extends ProducerAction

            val state: Ref[State] = Ref(Queueing(Queue[Input[E]](), 0))

            def step(ee: Input[E]): Iteratee[E, Iteratee[List[E], A]] = ee match {
                case in @ Input.EOF =>
                    state.single.getAndTransform {
                        case Queueing(q, l) => Queueing(q.enqueue(in), l)

                        case Waiting(p)     => Queueing(Queue(), 0)

                        case d @ DoneIt(it) => d

                        case _              => throw new Exception("illegal state!")

                    } match {
                        case Waiting(p) =>
                            p.success(in)
                        case _ =>
                    }
                    Iteratee.flatten(last.future)

                case other =>
                    val s = atomic { implicit txn =>
                        state() match {
                            case Queueing(q, l) if maxBuffer > 0 && l > maxBuffer =>
                                val p = Promise[Unit]()
                                state() = InPause(q.enqueue(other), p)
                                PauseProducer(p.future)

                            case Queueing(q, l) =>
                                state() = Queueing(q.enqueue(other), l + 1)
                                Continue

                            case Waiting(p) =>
                                state() = Queueing(Queue(), 0)
                                ResumeConsumer(p)

                            case d @ DoneIt(it) =>
                                state() = d
                                Finish(it)

                            case _ => throw new Exception("illegal state")

                        }
                    }
                    s match {
                        case ResumeConsumer(p) =>
                            p.success(other.map(List(_)))
                            Cont(step)
                        case Finish(it)       => it
                        case Continue         => Cont(step)
                        case PauseProducer(f) => Iteratee.flatten(f.map(_ => Cont(step)))

                    }
            }
            def getInputAndRest(q: Queue[Input[E]]): (Input[List[E]], Queue[Input[E]]) = {

                val (els, after) = q.toList.span(_ != Input.EOF)
                if (!els.isEmpty) {
                    val e = Input.El({ val l = els.collect { case Input.El(e) => e }; l })
                    (e, Queue(after: _*))
                } else { ((Input.EOF), Queue()) }

            }

            def moreInput: Future[Input[List[E]]] = {
                val in = atomic { implicit txn =>
                    val current = state()
                    state() match {
                        case Queueing(q, l) =>
                            if (!q.isEmpty) {
                                val (e, q1) = getInputAndRest(q)
                                state() = Queueing(q1, 1)
                                Future.successful(e)
                            } else {
                                val p = Promise[Input[List[E]]]()
                                state() = Waiting(p)
                                p.future
                            }

                        case InPause(q, p) =>
                            p.trySuccess(())
                            state() = Queueing(Queue(), 0)
                            val (els, after) = q.toList.span(_ != Input.EOF)
                            val (e, q1) = getInputAndRest(q)
                            state() = Queueing(q1, 1)
                            Future.successful(e)

                        case _ => throw new Exception("can't get here")
                    }
                }
                in
            }

            (checkContinue0(new TreatCont0[List[E]] {
                def apply[A](loop: Iteratee[List[E], A] => Unit, k: Input[List[E]] => Iteratee[List[E], A]) = moreInput.foreach { in =>
                    loop(k(in))
                }
            }) |>> it).flatMap(_.unflatten).onComplete {
                case Success(it) =>
                    state.single() = DoneIt(Done(it.it, Input.Empty))
                    last.success(Done(it.it, Input.Empty))
                case Failure(e) =>
                    state.single() = DoneIt(Iteratee.flatten(Future.failed[Iteratee[E, Iteratee[List[E], A]]](e)))
                    last.failure(e)

            }
            Cont(step)

        }
    }
}