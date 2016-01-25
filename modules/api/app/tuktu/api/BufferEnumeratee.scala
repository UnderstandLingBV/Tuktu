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

/**
 * Bloody buffering helpers with proper back pressure and all that
 */
object BufferEnumeratee {
    def batched[T](size: Int)(implicit ec: ExecutionContext): Enumeratee[T, List[T]] = {
        def helper(chunk: List[T] = Nil): Iteratee[T, List[T]] =
            Cont[T, List[T]] {
                case Input.El(data) if chunk.size == (size - 1) =>
                    Done(chunk :+ data)
                case Input.El(data) if chunk.size >= size =>
                    Error(s"Unexpected chunk size ${chunk.size}", Input.El(data))
                case Input.El(data) if chunk.size < size =>
                    helper(chunk :+ data)
                case Input.EOF =>
                    Done(chunk)
                case Input.Empty =>
                    helper(chunk)
            }
        Enumeratee.grouped[T](helper(Nil))
    }
    
    def eof[T]()(implicit ec: ExecutionContext): Enumeratee[T, List[T]] = {
        def helper(chunk: List[T] = Nil): Iteratee[T, List[T]] =
            Cont[T, List[T]] {
                case Input.El(data) =>
                    helper(chunk :+ data)
                case Input.EOF =>
                    Done(chunk)
                case Input.Empty =>
                    helper(chunk)
            }
        Enumeratee.grouped[T](helper(Nil))
    }
}