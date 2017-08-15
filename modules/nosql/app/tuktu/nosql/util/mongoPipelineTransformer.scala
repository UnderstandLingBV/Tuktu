package tuktu.nosql.util

import play.api.libs.json._
import play.modules.reactivemongo.json.collection._
import tuktu.api._

class MongoPipelineTransformer(implicit collection: JSONCollection) {
    def json2task(jobj: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.PipelineOperator = {
        import collection.BatchCommands.AggregationFramework._

        jobj.value.head match {
            case ("$skip", JsNumber(n))    => Skip(n.intValue)
            case ("$limit", JsNumber(n))   => Limit(n.intValue)
            case ("$unwind", JsString(s))  => Unwind(s) // Unwind prepends field names with '$'
            case ("$out", JsString(s))     => Out(s)
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
            case ("$sum", JsString(s))      => SumField(s)
            case ("$avg", JsString(s))      => Avg(s)
            case ("$min", JsString(s))      => Min(s)
            case ("$max", JsString(s))      => Max(s)
            case ("$push", JsString(s))     => Push(s)
            case ("$addToSet", JsString(s)) => AddToSet(s)
            case ("$first", JsString(s))    => First(s)
            case ("$last", JsString(s))     => Last(s)
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