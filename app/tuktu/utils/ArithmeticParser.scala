package tuktu.utils

/**
 * An ArithmicParser.
 *
 * Copied from: http://rosettacode.org/wiki/Arithmetic_evaluation#Scala
 */
object ArithmeticParser extends scala.util.parsing.combinator.RegexParsers {

    def readExpression(input: String): Option[() => Double] = {
        parseAll(expr, input) match {
            case Success(result, _) =>
                Some(result)
            case other =>
                println(other)
                None
        }
    }

    private def expr: Parser[() => Double] = {
        (term <~ "+") ~ expr ^^ { case l ~ r => () => l() + r() } |
            (term <~ "-") ~ expr ^^ { case l ~ r => () => l() - r() } |
            term
    }

    private def term: Parser[() => Double] = {
        (factor <~ "*") ~ term ^^ { case l ~ r => () => l() * r() } |
            (factor <~ "/") ~ term ^^ { case l ~ r => () => l() / r() } |
            factor
    }

    private def factor: Parser[() => Double] = {
        "(" ~> expr <~ ")" |
            "[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?".r ^^ { x => () => x.toDouble } |
            failure("Expected a value")
    }
}