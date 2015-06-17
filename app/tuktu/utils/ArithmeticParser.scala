package tuktu.utils

/**
 * An ArithmicParser.
 * 
 * Copied from: http://rosettacode.org/wiki/Arithmetic_evaluation#Scala
 */
object ArithmeticParser extends scala.util.parsing.combinator.RegexParsers {
 
  def readExpression(input: String) : Option[()=>Int] = {
    parseAll(expr, input) match {
      case Success(result, _) =>
        Some(result)
      case other =>
        println(other)
        None
    }
  }
 
  private def expr : Parser[()=>Int] = {
    (term<~"+")~expr ^^ { case l~r => () => l() + r() } |
    (term<~"-")~expr ^^ { case l~r => () => l() - r() } |
    term
  }
 
  private def term : Parser[()=>Int] = {
    (factor<~"*")~term ^^ { case l~r => () => l() * r() } |
    (factor<~"/")~term ^^ { case l~r => () => l() / r() } |
    factor
  }
 
  private def factor : Parser[()=>Int] = {
    "("~>expr<~")" |
    "\\d+".r ^^ { x => () => x.toInt } |
    failure("Expected a value")
  }
}