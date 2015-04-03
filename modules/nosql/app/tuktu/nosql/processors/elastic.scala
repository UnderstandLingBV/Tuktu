package tuktu.nosql.processors

import tuktu.api._
import play.api.libs.json.JsObject
import play.api.libs.iteratee.Enumeratee
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.action.search.SearchType
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.elasticsearch.search.SearchHit
import org.elasticsearch.index.query.FilterBuilders

class ElasticSearchProcessor(resultName: String) extends BaseProcessor(resultName) {
    var client: TransportClient = null
    var append = false
    
    var index = ""
    var typeName = ""
    var fields: Array[String] = Array()
    var scrollTime = 60000
    var termBinding = Map[String, String]()
    
    override def initialize(config: JsObject) = {
        // Get parameters required to set up ES client
        val cluster = (config \ "cluster").as[String]
        val host = (config \ "host").as[String]
        val port = (config \ "port").as[Int]
        
        // Query params
        index = (config \ "index").as[String]
        typeName = (config \ "type").as[String]
        fields = (config \ "fields").as[Array[String]]
        scrollTime = (config \ "scroll_time").asOpt[Int].getOrElse(60000)
        val termJson = (config \ "term_binding").as[List[JsObject]]
        termBinding = termJson.map(binding => (binding \ "name").as[String] -> (binding \ "field").as[String]).toMap
        
        // Set up the client
        val esSettings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build

        val client = new TransportClient(esSettings).addTransportAddress(new InetSocketTransportAddress(host, port))
        
        // Append result or not?
        append = (config \ "append").asOpt[Boolean].getOrElse(false)
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM((data: DataPacket) => Future {
        new DataPacket(for (datum <- data.data) yield {
            // Build term queries
            val queries = termBinding.map(binding => binding match {
                case (term, field) => {
                    QueryBuilders.termQuery(term, utils.evaluateTuktuString(field, datum))
                }
            }).toArray
            
            val response = client.prepareSearch(index)
                .setTypes(typeName)
                .addFields(fields:_*)
                .setSearchType(SearchType.SCAN)
                .setScroll(new TimeValue(scrollTime))
                .setQuery(QueryBuilders.matchAllQuery)
                .setSize(100)
                .execute()
                .actionGet
                
            // Iterate over results
            var hits = Array[SearchHit]()
            var result = collection.mutable.Map[String, Map[String, Any]]()
            hits = response.getHits.getHits
            do {
                
                
                // Next page
                val r = client
                    .prepareSearchScroll(response.getScrollId())
                    .setScroll(new TimeValue(scrollTime))
                    .execute
                    .actionGet
                hits = r.getHits.getHits
            } while (hits.size > 0)
                
            datum
        })
    }) compose Enumeratee.onEOF(() => {
        client.close
    })
}