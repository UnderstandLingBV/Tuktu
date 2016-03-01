package tuktu.dlib.utils

import play.api.libs.ws.WS
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object lre 
{
  
    def callLRE(query: String, resultOnly: Boolean): Future[Any] = 
    {
        val future = WS.url(query).get
        future.map(response => {
            val res = response.json
            (res \ "ids").asOpt[String] match
            {
                case None => res
                case Some(ids) => resultOnly match
                {
                    case false => res
                    case true => lre.unroll( ids )
                }
            }
        })
    }
  
   /**
     * Unroll (i.e., uncompress) a compressed list of LRE resource identifiers.
     * @param ids: a compressed list of LRE resource identifiers
     * @return A sorted list of LRE resource identifiers
     */
    def unroll( ids: String ): Seq[String] =
    {
        val rangeRegex = """\[\d+,\d+\]""".r
        val listRegex = """\{([^\}]+)\}""".r
        val ranges = rangeRegex.findAllMatchIn(ids).toList.flatMap{ range => expand( range.matched ) }
        val lists = listRegex.findAllMatchIn(ids).toList.flatMap{ list => """\d+""".r.findAllMatchIn(list.matched).toList.map{ el => el.matched } }
        (ranges ++ lists).sorted
    }
    
    private def expand( range: String ): Seq[String] =
    {
        val regex = """\[(\d+),(\d+)\]""".r
        range match{
            case regex( min, max ) => Range( s"$min".toInt, s"$max".toInt ).map{ i => ""+i } 
            case _ => List()
        }
    }
}