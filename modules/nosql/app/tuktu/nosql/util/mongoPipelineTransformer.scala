package tuktu.nosql.util

import play.api.libs.json._
import play.modules.reactivemongo.json.collection._
import tuktu.api._

object MongoPipelineTransformer {
    // ReactiveMongo requires $ in front of fields to be dropped
    def convert(s: String): String = if (s.headOption == Some('$')) s.tail else s

    def json2task(jobj: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.PipelineOperator = {
        import collection.BatchCommands.AggregationFramework._

        jobj.value.head match {
            case ("$skip", JsNumber(n))    => Skip(n.intValue)
            case ("$limit", JsNumber(n))   => Limit(n.intValue)
            case ("$unwind", JsString(s))  => Unwind(convert(s)) // Unwind prepends field names with '$'
            case ("$out", JsString(s))     => Out(convert(s))
            case ("$sort", o: JsObject)    => Sort(getSortOrder(o): _*)
            case ("$match", o: JsObject)   => Match(o)
            case ("$project", o: JsObject) => Project(o)
            case ("$group", o: JsObject)   => getGroup(o)
        }
    }

    def getGroup(jobj: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.PipelineOperator = {
        import collection.BatchCommands.AggregationFramework._

        val params = (jobj - "_id").value.mapValues { value => getExpression(value.as[JsObject]) } toSeq

        Group(jobj \ "_id")(params: _*)
    }

    def getExpression(expression: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.GroupFunction = {
        import collection.BatchCommands.AggregationFramework._

        expression.value.head match {
            case ("$sum", JsNumber(n))      => SumValue(n.intValue)
            case ("$sum", JsString(s))      => SumField(convert(s))
            case ("$avg", JsString(s))      => Avg(convert(s))
            case ("$min", JsString(s))      => Min(convert(s))
            case ("$max", JsString(s))      => Max(convert(s))
            case ("$push", JsString(s))     => Push(convert(s))
            case ("$addToSet", JsString(s)) => AddToSet(convert(s))
            case ("$first", JsString(s))    => First(convert(s))
            case ("$last", JsString(s))     => Last(convert(s))
        }
    }

    def getSortOrder(orders: JsObject)(implicit collection: JSONCollection): Seq[collection.BatchCommands.AggregationFramework.SortOrder] = {
        import collection.BatchCommands.AggregationFramework._

        orders.value.map {
            case (key, JsNumber(n)) => n.intValue match {
                case 1  => Ascending(key)
                case -1 => Descending(key)
            }
        } toSeq
    }
}