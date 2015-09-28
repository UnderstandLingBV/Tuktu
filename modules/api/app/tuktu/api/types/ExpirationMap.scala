package tuktu.api.types

/**
 * Courtesy of http://higher-state.blogspot.co.uk/2013/02/expiration-map.html
 */

trait ExpirationMap[A, +B] {

    def expirationInterval: Long

    def apply(key: A)(implicit current: Long): B

    def get(key: A)(implicit current: Long): Option[B]

    def contains(key: A)(implicit current: Long): Boolean

    def expire(key: A)(implicit current: Long): Unit

    def partitioned(implicit current: Long): (Map[A, B], Map[A, B])

    def toMap(implicit current: Long): Map[A, B]

    def +[B1 >: B](kv: (A, B1))(implicit current: Long): ExpirationMap[A, B1]

    def -(key: A)(implicit current: Long): ExpirationMap[A, B]

    def mapValues[C](f: (B) ⇒ C)(implicit current: Long): ExpirationMap[A, C]
}

object ExpirationMap {

    def apply[A, B](expirationInterval: Long): ExpirationMap[A, B] = ExpirationMapImpl[A, B](expirationInterval, Map.empty)
}

private case class ExpirationTuple[+B](value: B, var expiration: Option[Long]) {

    def expire(expirationInterval: Long)(implicit current: Long) = expiration = Some(current + expirationInterval)
}

private case class ExpirationMapImpl[A, +B](expirationInterval: Long, keyValueExpiry: Map[A, ExpirationTuple[B]]) extends ExpirationMap[A, B] {

    def apply(key: A)(implicit current: Long): B =
        get(key).get

    def get(key: A)(implicit current: Long): Option[B] =
        keyValueExpiry.get(key) collect {
            case et: ExpirationTuple[B] if (et.expiration.map(expiry => expiry > current).getOrElse(true)) => et.value
        }

    def contains(key: A)(implicit current: Long): Boolean =
        get(key) != None

    def expire(key: A)(implicit current: Long) {
        keyValueExpiry.get(key) collect {
            case et: ExpirationTuple[B] => et.expire(expirationInterval)
        }
    }

    def partitioned(implicit current: Long): (Map[A, B], Map[A, B]) = {
        val (running, finished) = clearExpired.partition(t => t._2.expiration == None)
        (running.mapValues(_.value), finished.mapValues(_.value))
    }

    def toMap(implicit current: Long): Map[A, B] = clearExpired.mapValues(_.value)

    def +[B1 >: B](kv: (A, B1))(implicit current: Long): ExpirationMap[A, B1] = {
        val newMap = (clearExpired + (kv._1 -> new ExpirationTuple(kv._2, None)))
        ExpirationMapImpl(expirationInterval, newMap)
    }

    def -(key: A)(implicit current: Long): ExpirationMap[A, B] = {
        val cleared = clearExpired
        if (cleared.contains(key))
            ExpirationMapImpl(expirationInterval, cleared - key)
        else
            this
    }

    def mapValues[C](f: (B) ⇒ C)(implicit current: Long): ExpirationMap[A, C] =
        ExpirationMapImpl(expirationInterval, clearExpired.mapValues(v => new ExpirationTuple(f(v.value), v.expiration)))

    private def clearExpired(implicit current: Long): Map[A, ExpirationTuple[B]] =
        keyValueExpiry.filter(_._2.expiration.map(expiry => expiry > current).getOrElse(true))
}