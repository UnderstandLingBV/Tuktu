package tuktu.nosql.util

import play.api.cache.Cache

import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Iteratee

import play.api.libs.json._
import play.api.libs.json.Json._

import play.api.Play.current

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._

import reactivemongo.api.commands.Command
import reactivemongo.api._
import reactivemongo.api.commands.AggregationFramework

import scala.collection.Set

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

import tuktu.api._

class MongoPipelineTransformer(implicit collection: JSONCollection) {
    def json2task(jobj: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.PipelineOperator = {
        import collection.BatchCommands.AggregationFramework.{
            AggregationResult,
            PipelineOperator,
            Skip,
            Limit,
            Unwind,
            Out,
            Sort,
            SortOrder,
            Match,
            Project,
            Group
        }

        val keys: Set[String] = jobj.keys

        val result: PipelineOperator = keys.head match {
            case "$skip"    => Skip(jobj.\("$skip").as[Int])
            case "$limit"   => Limit(jobj.\("$limit").as[Int])
            case "$unwind"  => Unwind(jobj.\("$unwind").as[String]) // Unwind prepends field names with '$'
            case "$out"     => Out(jobj.\("$out").as[String])
            case "$sort"    => Sort(getSortOrder(jobj.\("$sort").as[JsObject]): _*)
            case "$match"   => Match(jobj.\("$match").as[JsObject])
            case "$project" => Project(jobj.\("$project").as[JsObject])
            case "$group"   => getGroup(jobj.\("$group").as[JsObject])
        }
        return result

    }

    def getGroup(jobj: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.PipelineOperator =
        {
            import collection.BatchCommands.AggregationFramework.{
                AggregationResult,
                Group,
                GroupFunction
            }

            var gArray: Array[(String, Any)] = Array[(String, Any)]()

            var i: Int = 0
            for (key <- jobj.keys) {
                key match {
                    case "_id"       => gArray = gArray :+ ("id", jobj.\("_id"))
                    case key: String => gArray = gArray :+ (key, getExpression(jobj.\(key).as[JsObject]))

                }
            }
            // println( "size : " + gArray.length )
            var test = Group(gArray(0)._2.asInstanceOf[JsString])(handleTail(gArray.drop(1)): _*)
            // println( test )
            return Group(gArray(0)._2.asInstanceOf[JsString])(handleTail(gArray.drop(1)): _*)

        }

    def handleTail(tail: Array[(String, Any)])(implicit collection: JSONCollection): Array[(String, collection.BatchCommands.AggregationFramework.GroupFunction)] =
        {
            import collection.BatchCommands.AggregationFramework.{
                GroupFunction,
                Last,
                First,
                AddToSet,
                Push,
                Max,
                Min,
                Avg
            }
            var result: Array[(String, GroupFunction)] = Array[(String, GroupFunction)]()
            for (el <- tail) {
                result = result :+ (el._1, el._2.asInstanceOf[GroupFunction])
            }
            return result
        }

    def getExpression(expression: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.GroupFunction =
        {
            import collection.BatchCommands.AggregationFramework.{
                GroupFunction,
                Last,
                First,
                AddToSet,
                Push,
                Max,
                Min,
                Avg
            }

            val result: GroupFunction = expression.keys.head match {
                case "$sum"      => getSum(expression)
                case "$avg"      => Avg(expression.\("$avg").as[String])
                case "$min"      => (Min(expression.\("$min").as[String]))
                case "$max"      => (Max(expression.\("$max").as[String]))
                case "$push"     => (Push(expression.\("$push").as[String]))
                case "$addToSet" => (AddToSet(expression.\("$addToSet").as[String]))
                case "$first"    => (First(expression.\("$first").as[String]))
                case "$last"     => (Last(expression.\("$last").as[String]))
            }
            return result

        }

    def getSum(sum: JsObject)(implicit collection: JSONCollection): collection.BatchCommands.AggregationFramework.GroupFunction =
        {
            import collection.BatchCommands.AggregationFramework.{
                GroupFunction,
                SumField,
                SumValue
            }
            val result: GroupFunction = sum.\("$sum") match {
                case t: JsString => SumField(t.as[String])
                case t: JsNumber => SumValue(t.as[Int])
            }
            return result
        }

    def getSortOrder(orders: JsObject)(implicit collection: JSONCollection) =
        {
            import collection.BatchCommands.AggregationFramework.{
                Sort,
                SortOrder,
                Ascending,
                Descending
            }
            var result: Array[SortOrder] = Array()

            for (key <- orders.keys) {
                val param = orders.\(key).as[Int] match {
                    case 1  => result = result :+ Ascending(key)
                    case -1 => result = result :+ Descending(key)
                    case _  => // TODO unsupported and therefore ignored
                }
            }

            result
        }

}