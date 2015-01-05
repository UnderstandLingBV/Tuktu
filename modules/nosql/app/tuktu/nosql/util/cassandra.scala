package tuktu.nosql.util

import com.datastax.driver.core.Cluster

object cassandra {
    class client(address: String) {
        // Get host and port
        val (host, port) = address.contains(":") match {
            case false => (address, 9042)
            case true => {
                val split = address.split(":")
                (split(0), split(1).toInt)
            }
        }
        
        // Set up connection
        val cluster = Cluster.builder.addContactPoint(host).withPort(port).build
        // Set up session
        val session = cluster.connect
        
        /**
         * Closes the session
         */
        def close() = {
            session.close
        }
        
        /**
         * Runs a query on a Cassandra cluster and returns the resultset
         * @param query The query
         */
        def runQuery(query: String) = {
            session.execute(query)
        }
    }
}