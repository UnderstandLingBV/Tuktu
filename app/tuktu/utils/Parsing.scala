package tuktu.utils

import fastparse.WhitespaceApi

object ArithmeticParser {
    val White = WhitespaceApi.Wrapper {
        import fastparse.all._
        NoTrace(" ".rep)
    }
    import fastparse.noApi._
    import White._
    
    // Allow all sorts of numbers, negative and scientific notation
    val number: P[Double] = P(CharIn('-' :: '.' :: 'e' :: ('0' to '9').toList).rep(1).!.map(_.toDouble))
    val parens: P[Double] = P("(" ~/ addSub ~ ")")
    val factor: P[Double] = P(number | parens)

    val divMul: P[Double] = P(factor ~ (CharIn("*/").! ~/ factor).rep).map(eval)
    val addSub: P[Double] = P(divMul ~ (CharIn("+-").! ~/ divMul).rep).map(eval)
    val expr: P[Double] = P((addSub | factor) ~ End)

    def eval(tree: (Double, Seq[(String, Double)])) = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "+" => left + right case "-" => left - right
                case "*" => left * right case "/" => left / right
            }
        }
    }

    def apply(str: String) = {
        expr.parse(str).get.value
    }
}

object PredicateParser {
    val White = WhitespaceApi.Wrapper {
        import fastparse.all._
        NoTrace(" ".rep)
    }
    import fastparse.noApi._
    import White._
    
    // Boolean base logic
    val literal: P[Boolean] = P(("true" | "false").!.map(_.toBoolean))
    val parens: P[Boolean] = P("(" ~/ (andOr | arithExpr) ~ ")")
    val neg: P[Boolean] = P(("!(" ~/ andOr ~ ")") | ("!" ~/ literal)).map(!_)
    val factor: P[Boolean] = P(literal | neg | parens | arithExpr | stringExpr)
    // Strings
    val strings: P[String] = P(CharIn(('a' to 'z').toList ++ ('A' to 'Z').toList).rep(1).!.map(_.toString))

    // Evaluate arithmetic expressions on numbers using the ArithmeticParser
    val arithExpr: P[Boolean] = P((ArithmeticParser.addSub | ArithmeticParser.parens) ~/ ("<" | ">" | ">=" | "<=" | "==" | "!=").! ~ (ArithmeticParser.addSub | ArithmeticParser.parens))
        .map {
            case (left, op, right) => op match {
                case "<" => left < right case ">" => left > right
                case "<=" => left <= right case ">=" => left >= right
                case "==" => left == right case "!=" => left != right
            }
        }
    
    // Evaluate string expressions
    val stringExpr: P[Boolean] = P(strings ~/ ("==" | "!=").! ~ strings)
        .map {
            case (left, "==", right) => left == right
            case (left, "!=", right) => left != right
        }

    // Logical and/or
    val andOr: P[Boolean] = P(factor ~ (("&&" | "||").! ~/ factor).rep).map(eval)
    val expr: P[Boolean] = P(andOr ~ End)

    def eval(tree: (Boolean, Seq[(String, Boolean)])) = {
        val (base, ops) = tree
        ops.foldLeft(base) {
            case (left, (op, right)) => op match {
                case "&&" => left && right case "||" => left || right
            }
        }
    }

    def apply(str: String) = {
        expr.parse(str).get.value
    }
}