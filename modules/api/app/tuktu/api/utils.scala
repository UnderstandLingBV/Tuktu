package tuktu.api

import java.util.regex.Pattern

object utils {
    val pattern = Pattern.compile("\\$\\{(.*?)\\}")
    
    def evaluateTuktuString(str: String, vars: Map[String, Any]) = {
        // Set up matcher and string buffer
        val matcher = pattern.matcher(str)
        val buff = new StringBuffer(str.length)
        
        // Replace with vars
        while (matcher.find)
            matcher.appendReplacement(buff, vars(matcher.group(1)).toString)
        matcher.appendTail(buff)
        
        // Return buffer
        buff.toString
    }
}