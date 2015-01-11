package tuktu.nosql.util

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Row
import scala.collection.JavaConversions._

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
    
    /**
     * Turns a Cassandra row (result) into a map
     */
    def rowToMap(row: Row): Map[String, String] = {
        // From the row, get the fields that are present
        val columns = row.getColumnDefinitions.asList
        
        // Loop over all the columns
        (for (column <- columns) yield {
            // Get column name and its data type
            val colName = column.getName
            val tn = column.getType.getName.name.toLowerCase
            
            // Determine what to read based on the data type
            val value = tn match {
                case "uuid" => row.getUUID(colName).toString
                case "int" => row.getInt(colName).toString
                case "double" => row.getDouble(colName).toString
                case "float" => row.getFloat(colName).toString
                case "long" => row.getLong(colName).toString
                case "timestamp" => row.getDate(colName).toString
                case _ => {
                    val x = row.getString(colName)
                    if (x == null) "null"
                    else x
                }
            }
            
            // Add to map
            colName -> value
        }).toMap
    }
}