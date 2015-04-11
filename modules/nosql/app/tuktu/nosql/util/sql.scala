package tuktu.nosql.util

import java.sql.DriverManager
import anorm._
import java.util.regex.Pattern

object sql {
    val pattern = Pattern.compile("\\$\\{(.*?)\\}")
    
    case class client(url: String, user: String, password: String, driver: String) {
        // Load the driver, set up the connection
        Class.forName(driver)
        val connection = DriverManager.getConnection(url, user, password)
        
        def queryResult(query: String) = SQL(query).apply()(connection).toList
        def query(query: String) = SQL(query).execute()(connection)
        
        def close() = {
            connection.close()
        }
    }
    
    /**
     * Turns an SQL row into a Map[String, Any]
     */
    def rowToMap(row: Row) = row.asMap
    
    /**
     * Evaluates a string taking into account IN-statements
     */
    def evaluateSqlString(str: String, vars: Map[String, Any]) = {
        // Set up matcher and string buffer
        val matcher = pattern.matcher(str)
        val buff = new StringBuffer(str.length)
        
        // Replace with vars
        while (matcher.find) {
            // See if this is a list or not
            val key = matcher.group(1).split(";")
            
            if (key.size > 0) {
                // See if it's a sequence or single field
                vars(key(0)) match {
                    case seq: Seq[Any] if vars(key(0)).asInstanceOf[Seq[Any]].size > 0 => {
                        // It's some sort of list, do something with it depending on the type
                        vars(key(0)).asInstanceOf[Seq[Any]].head match {
                            case el: Int => matcher.appendReplacement(buff, vars(key(0)).asInstanceOf[Seq[Int]].mkString(key(1)))
                            case el: Double => matcher.appendReplacement(buff, vars(key(0)).asInstanceOf[Seq[Int]].mkString(key(1)))
                            case el: Long => matcher.appendReplacement(buff, vars(key(0)).asInstanceOf[Seq[Int]].mkString(key(1)))
                            case el: Any => {
                                // Here we assume it's string
                                matcher.appendReplacement(buff, vars(key(0)).asInstanceOf[Seq[Any]].map("'" + _.toString + "'").mkString(key(1)))
                            }
                        }
                    }
                    case elem: String => matcher.appendReplacement(buff, "'" + elem + "'")
                    case _ => matcher.appendReplacement(buff, vars(key(0)).toString)
                }
            } else // Just regular string
                matcher.appendReplacement(buff, vars(matcher.group(1)).toString)
        }
        matcher.appendTail(buff)
        
        // Return buffer
        buff.toString
    }
}