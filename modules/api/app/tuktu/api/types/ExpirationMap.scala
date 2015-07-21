package tuktu.api.types

/**
 * Courtesy of http://higher-state.blogspot.co.uk/2013/02/expiration-map.html
 */

import collection.immutable.Queue

trait ExpirationMap[A, +B] {

    def expirationInterval: Long

    def apply(key: A)(implicit current: Long): B

    def get(key: A)(implicit current: Long): Option[B]

    def iterator(implicit current: Long): Iterator[(A, B)]

    def toMap(implicit current: Long): Map[A, B]

    def +[B1 >: B](kv: (A, B1))(implicit current: Long): ExpirationMap[A, B1]

    def -(key: A)(implicit current: Long): ExpirationMap[A, B]

    def mapValues[C](f: (B) ⇒ C)(implicit current: Long): ExpirationMap[A, C]
}

object ExpirationMap {

    def apply[A, B](expirationInterval: Long): ExpirationMap[A, B] = ExpirationMapImpl[A, B](expirationInterval, Queue.empty, Map.empty)
}

private case class ExpirationMapImpl[A, +B](expirationInterval: Long, expirationQueue: Queue[(A, Long)], keyValueExpiry: Map[A, (B, Long)]) extends ExpirationMap[A, B] {

    def apply(key: A)(implicit current: Long): B =
        get(key).get

    def get(key: A)(implicit datetime: Long): Option[B] =
        keyValueExpiry.get(key) collect {
            case (value, expiry) if (expiry > datetime) => value
        }

    def iterator(implicit current: Long): Iterator[(A, B)] = keyValueExpiry.iterator.filter(_._2._2 > current).map(p => (p._1, p._2._1))

    def toMap(implicit current: Long): Map[A, B] = keyValueExpiry.filter(_._2._2 > current).mapValues(_._1)

    def +[B1 >: B](kv: (A, B1))(implicit current: Long): ExpirationMap[A, B1] = {
        val cleared = clearedExpired(current)
        val newQueue =
            if (cleared.keyValueExpiry.contains(kv._1)) cleared.expirationQueue
            else cleared.expirationQueue.enqueue((kv._1, current + expirationInterval))
        val newMap = cleared.keyValueExpiry + (kv._1 -> (kv._2, current + expirationInterval))
        ExpirationMapImpl(this.expirationInterval, newQueue, newMap)
    }

    def -(key: A)(implicit current: Long): ExpirationMap[A, B] = {
        val cleared = clearedExpired(current)
        if (cleared.keyValueExpiry.contains(key)) ExpirationMapImpl(this.expirationInterval, cleared.expirationQueue, cleared.keyValueExpiry - key)
        else cleared
    }

    def mapValues[C](f: (B) ⇒ C)(implicit current: Long): ExpirationMap[A, C] = {
        val cleared = clearedExpired(current)
        ExpirationMapImpl(this.expirationInterval, cleared.expirationQueue, cleared.keyValueExpiry.mapValues(v => (f(v._1), v._2)))
    }

    private def clearedExpired(current: Long): ExpirationMapImpl[A, B] = clearedExpired(current, expirationQueue, keyValueExpiry)
    private def clearedExpired[C >: B](current: Long, expirationQueue: Queue[(A, Long)], keyValueExpiry: Map[A, (C, Long)]): ExpirationMapImpl[A, C] = {
        expirationQueue.headOption collect {
            case (key, expiry) if (expiry < current) => keyValueExpiry.get(key) map {
                case (_, expiry) if (expiry < current) => clearedExpired(current, expirationQueue.dequeue._2, keyValueExpiry - key)
                case _                                 => clearedExpired(current, expirationQueue.dequeue._2.enqueue((key, expiry)), keyValueExpiry)
            } getOrElse (clearedExpired(current, expirationQueue.dequeue._2, keyValueExpiry))
        } getOrElse (ExpirationMapImpl(this.expirationInterval, expirationQueue, keyValueExpiry))
    }
}