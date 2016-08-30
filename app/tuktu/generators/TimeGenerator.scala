package tuktu.generators

import akka.actor._
import akka.pattern.ask
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsValue
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import tuktu.api._

case class NeverEndingPacket( startingTime: DateTime )
case class ForwardPacket( startingTime: DateTime, endingTime: DateTime )
case class BackwardPacket( startingTime: DateTime, endingTime: DateTime )


/**
 * Actor that runs between two datetimes by interval steps 
 */
class TimerActor(parentActor: ActorRef, startingTime: DateTime, endingTime: Option[DateTime], formatter: DateTimeFormatter, interval: TimeInterval) extends Actor with ActorLogging 
{
    def receive() = 
    {   
        case ip: InitPacket => 
        {
            val anyPacket = endingTime match {
              case None => NeverEndingPacket( startingTime )
              case Some( etime ) =>  
                  startingTime.isBefore( etime ) match
                  {
                    case true => ForwardPacket( startingTime, etime )
                    case false => BackwardPacket( startingTime, etime )
                  }
            }
                       
            // Start processing
            self ! anyPacket
        }
        case stop: StopPacket => 
        {
            // stop
            parentActor ! new StopPacket
            self ! PoisonPill
        }
        case never: NeverEndingPacket => 
        {
            val time = formatter.print( never.startingTime )
            val newStartingTime = interval.addIntervalTo( never.startingTime )

            // Send back to parent for pushing into channel
            parentActor ! time

            // Continue with next time point
            self ! new NeverEndingPacket( newStartingTime )
        }
        case forward: ForwardPacket =>
        {
          
            forward.endingTime.isBefore( forward.startingTime ) match
            {
                case true => self ! new StopPacket
                case false => {
                    val time = formatter.print( forward.startingTime )
                    val newStartingTime = interval.addIntervalTo( forward.startingTime )
                  
                    // Send back to parent for pushing into channel
                    parentActor ! time

                    // Continue with next time point
                    self ! new ForwardPacket( newStartingTime, forward.endingTime )
                }
            }
        }
        case backward: BackwardPacket =>
        {
          
            backward.endingTime.isAfter( backward.startingTime ) match
            {
                case true => self ! new StopPacket
                case false => {
                    val time = formatter.print( backward.startingTime )
                    val newStartingTime = interval.subtractIntervalTo( backward.startingTime )
                  
                    // Send back to parent for pushing into channel
                    parentActor ! time

                    // Continue with next time point
                    self ! new BackwardPacket( newStartingTime, backward.endingTime )
                }
            }
        }
    }
}


/**
 * Generates points of time by adding (or substracting) a certain amount of time to a starting time.
 */
class TimeGenerator( resultName: String, processors: List[Enumeratee[DataPacket, DataPacket]], senderActor: Option[ActorRef] ) 
    extends BaseGenerator(resultName, processors, senderActor) 
{
    override def _receive = 
    {
        case config: JsValue => 
        {
            val format = (config \ "format").asOpt[String].getOrElse("")
            val locale = (config \ "locale").asOpt[String].getOrElse("US")
            val stime = (config \ "starting_time").as[String]
            val etime = (config \ "ending_time").asOpt[String]
            val years = (config \ "years").asOpt[String].getOrElse("0")
            val months = (config \ "months").asOpt[String].getOrElse("0")
            val weeks = (config \ "weeks").asOpt[String].getOrElse("0")
            val days = (config \ "days").asOpt[String].getOrElse("0")
            val hours = (config \ "hours").asOpt[String].getOrElse("0")
            val minutes = (config \ "minutes").asOpt[String].getOrElse("0")
            val seconds = (config \ "seconds").asOpt[String].getOrElse("0")
            
            val formatter = DateTimeFormat.forPattern(format).withLocale(Locale.forLanguageTag(locale))
            val interval = new TimeInterval( years.toInt, months.toInt, weeks.toInt, days.toInt, hours.toInt, minutes.toInt, seconds.toInt )
            val startingTime = stime match {
              case "now()" => DateTime.now()
              case st: String => DateTime.parse(stime, DateTimeFormat.forPattern(format).withLocale(Locale.forLanguageTag(locale)))
            }
            val endingTime: Option[DateTime] = etime match {
              case None => None
              case Some( et ) => et match {
                case "now()" => Option( DateTime.now() )
                case et: String => Option(DateTime.parse( et, DateTimeFormat.forPattern(format).withLocale(Locale.forLanguageTag(locale))))
              }
            }
            // Create actor and kickstart
            val timerActor = Akka.system.actorOf(Props(classOf[TimerActor], self, startingTime, endingTime, formatter, interval))
            timerActor ! new InitPacket()
        }
        case time: String => channel.push(new DataPacket(List(Map(resultName -> time))))
    }
}

class TimeInterval( years: Int, months: Int, weeks: Int, days: Int, hours: Int, minutes: Int, seconds: Int )
{
    def addIntervalTo( time: DateTime ): DateTime =
    {
        time.plusYears( years )
            .plusMonths( months )
            .plusWeeks( weeks )
            .plusDays( days )
            .plusHours( hours )
            .plusMinutes( minutes )
            .plusSeconds( seconds )
    }
    
    def subtractIntervalTo( time: DateTime ): DateTime =
    {
        time.minusYears( years )
            .minusMonths( months )
            .minusWeeks( weeks )
            .minusDays( days )
            .minusHours( hours )
            .minusMinutes( minutes )
            .minusSeconds( seconds )
    }
}
