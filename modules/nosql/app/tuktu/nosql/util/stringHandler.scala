package tuktu.nosql.util

object stringHandler {
    /**
     * Evaluates a string taking into account IN-statements
     */
    def evaluateString(str: String, vars: Map[String, Any], quote: String = "'", escape: String = "'") = {
        // The result builder
        val result = new StringBuilder
        // A temporary buffer to determine if we need to replace this
        val buffer = new StringBuilder
        // The prefix length of TuktuStrings "${".length == 2
        val prefixSize = "${".length

        for (currentChar <- str) {
            if (buffer.isEmpty) {
                if (currentChar.equals('$'))
                    buffer.append(currentChar)
                else
                    result.append(currentChar)
            } else if (buffer.length == 1) {
                buffer.append(currentChar)
                if (!currentChar.equals('{')) {
                    result.append(buffer)
                    buffer.clear
                }
            } else {
                if (currentChar.equals('}')) {
                    // Extract key and additional parameters and clear buffer
                    val key = buffer.substring(prefixSize).split(';')
                    buffer.clear

                    // See if it's a sequence or single field
                    vars(key(0)) match {
                        case seq: Seq[Any] if vars(key(0)).asInstanceOf[Seq[Any]].nonEmpty => {
                            // Iterate over the values
                            val variableList = (for (singleVar <- vars(key(0)).asInstanceOf[Seq[Any]]) yield {
                                // Parse depending on type
                                singleVar match {
                                    case el: Int     => el.toString
                                    case el: Double  => el.toString
                                    case el: Long    => el.toString
                                    case el: Boolean => if (el) "1" else "0"
                                    case el: Any => {
                                        // Here we assume it's a String
                                        quote + el.toString.replace(quote, escape + quote) + quote
                                    }
                                }
                            }).mkString(key(1))

                            // Now append it to our string
                            result.append(variableList)
                        }
                        case elem: String => result.append(quote + elem.replace(quote, escape + quote) + quote)
                        case _            => result.append(vars(key(0)).toString)
                    }
                } else {
                    // Not '}' yet, keep appending the buffer
                    buffer.append(currentChar)
                }
            }
        }
        // Add any left overs
        result.append(buffer)
        result.toString
    }
}