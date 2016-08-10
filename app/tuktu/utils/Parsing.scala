package tuktu.utils

import fastparse.all.CharIn
import fastparse.all.End
import fastparse.all.P
import fastparse.all.parserApi
import fastparse.all.wspStr

object ArithmeticParser {
    val number: P[Double] = P(CharIn('.'::('0' to '9').toList).rep(1).!.map(_.toDouble))
    val parens: P[Double] = P("(" ~/ addSub ~ ")")
    val factor: P[Double] = P(number | parens)

    val divMul: P[Double] = P(factor ~ (CharIn("*/").! ~/ factor).rep).map(eval)
    val addSub: P[Double] = P(divMul ~ (CharIn("+-").! ~/ divMul).rep).map(eval)
    val expr: P[Double] = P(addSub ~ End)

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