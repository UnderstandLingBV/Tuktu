package tuktu.processors.bucket.aggregate

import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import tuktu.processors.bucket.BaseBucketProcessor
import tuktu.api._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Get the minimum of a bucket
 */
class MinProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var field = ""

    override def initialize(config: JsObject) = {
        // Get the field to sort on
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        if (data.size == 0) List()
        else {
            // See what type of data it is
            val firstElem = data.head(field)

            // Get the minimum based on type information
            List(
                firstElem match {
                    case a: String => data.minBy(elem => elem(field) match { case b: String => b })
                    case a: Char => data.minBy(elem => elem(field) match { case b: Char => b })
                    case a: Short => data.minBy(elem => elem(field) match { case b: Short => b })
                    case a: Byte => data.minBy(elem => elem(field) match { case b: Byte => b })
                    case a: Int => data.minBy(elem => elem(field) match { case b: Int => b })
                    case a: Integer => data.minBy(elem => elem(field) match { case b: Integer => b })
                    case a: Double => data.minBy(elem => elem(field) match { case b: Double => b })
                    case a: Float => data.minBy(elem => elem(field) match { case b: Float => b })
                    case a: Long => data.minBy(elem => elem(field) match { case b: Long => b })
                    case _ => throw new Exception
                }
            )
        }
    }
}

/**
 * Get the maximum of a bucket
 */
class MaxProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var field = ""

    override def initialize(config: JsObject) = {
        // Get the field to sort on
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        if (data.size == 0) List()
        else {
            // See what type of data it is
            val firstElem = data.head(field)

            // Get the maximum based on type information
            List(
                firstElem match {
                    case a: String => data.maxBy(elem => elem(field) match { case b: String => b })
                    case a: Char => data.maxBy(elem => elem(field) match { case b: Char => b })
                    case a: Short => data.maxBy(elem => elem(field) match { case b: Short => b })
                    case a: Byte => data.maxBy(elem => elem(field) match { case b: Byte => b })
                    case a: Int => data.maxBy(elem => elem(field) match { case b: Int => b })
                    case a: Integer => data.maxBy(elem => elem(field) match { case b: Integer => b })
                    case a: Double => data.maxBy(elem => elem(field) match { case b: Double => b })
                    case a: Float => data.maxBy(elem => elem(field) match { case b: Float => b })
                    case a: Long => data.maxBy(elem => elem(field) match { case b: Long => b })
                    case _ => throw new Exception
                }
            )
        }
    }
}

/**
 * Sums all values of a field in a bucket
 */
class SumProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var field = ""

    override def initialize(config: JsObject) = {
        // Get the field to sort on
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        if (data.size == 0) List()
        else {
            // See what type of data it is
            val firstElem = data.head(field)

            List(Map(field -> {
                firstElem match {
                    case a: String => data.foldLeft[Double](0)(_ + _(field).asInstanceOf[String].toDouble)
                    case a: Int => data.foldLeft[Int](0)(_ + _(field).asInstanceOf[Int])
                    case a: Integer => data.foldLeft[Integer](0: Integer)(_ + _(field).asInstanceOf[Integer])
                    case a: Double => data.foldLeft[Double](0.0)(_ + _(field).asInstanceOf[Double])
                    case a: Long => data.foldLeft[Long](0L)(_ + _(field).asInstanceOf[Long])
                    case a: Float => data.foldLeft[Float](0.0f)(_ + _(field).asInstanceOf[Float])
                    case a: BigDecimal => data.foldLeft[BigDecimal](0)(_ + _(field).asInstanceOf[BigDecimal])
                    case a: List[Any] => sumList(a)
                }
            }))
        }
    }

    def sumList(data: List[Any]) = {
        // See what type of data it is
        data.head match {
            case a: String => data.foldLeft[Double](0)(_ + _.asInstanceOf[String].toDouble)
            case a: Int => data.foldLeft[Int](0)(_ + _.asInstanceOf[Int])
            case a: Integer => data.foldLeft[Integer](0: Integer)(_ + _.asInstanceOf[Integer])
            case a: Double => data.foldLeft[Double](0.0)(_ + _.asInstanceOf[Double])
            case a: Long => data.foldLeft[Long](0L)(_ + _.asInstanceOf[Long])
            case a: Float => data.foldLeft[Float](0.0f)(_ + _.asInstanceOf[Float])
            case a: BigDecimal => data.foldLeft[BigDecimal](0)(_ + _.asInstanceOf[BigDecimal])
        }
    }
}

/**
 * Counts the amount of values in a bucket
 */
class CountProcessor(resultName: String) extends BaseBucketProcessor(resultName) {
    var field = ""

    override def initialize(config: JsObject) = {
        field = (config \ "field").as[String]
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = super.processor

    override def doProcess(data: List[Map[String, Any]]): List[Map[String, Any]] = {
        List(Map(field -> data.size))
    }
}

/**
 * Calculates simple arithmetic
 */
class ArithmeticProcessor(resultName: String) extends BaseProcessor(resultName) {  
    var calculate: String = _
    
    override def initialize(config: JsObject) = {
        calculate = (config \ "calculate").as[String]          
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        Future {
          new DataPacket(for (datum <- data.data) yield {
            val formula = utils.evaluateTuktuString(calculate, datum)
            val result = tuktu.utils.ArithmeticParser.readExpression(formula)
            if (result.isDefined)
              datum + (resultName -> result.get())
            else datum
          })
        }
    })  
}
