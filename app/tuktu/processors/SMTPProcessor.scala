package tuktu.processors

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.util.Timeout
import courier.Envelope
import courier.Mailer
import courier.Multipart
import courier.Text
import javax.mail.internet.InternetAddress
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsObject
import tuktu.api.BaseProcessor
import tuktu.api.DataPacket
import tuktu.api.utils
import play.api.Logger

case class MailerDef(
    serverName: String, port: Int, username: String, password: String, tls: Boolean)

/**
 * Sends e-mail using SMTP
 */
class SMTPProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var serverName: String = _
    var port: Int = _
    var username: String = _
    var password: String = _
    var tls: Boolean = _

    var from: String = _
    var to: String = _
    var cc: String = _
    var bcc: String = _
    var subject: String = _
    var body: String = _

    var contentType: String = _
    var waitForSent: Boolean = _

    val mailers = collection.mutable.Map.empty[MailerDef, Mailer]

    override def initialize(config: JsObject) {
        serverName = (config \ "server_name").as[String]
        port = (config \ "port").as[Int]
        username = (config \ "username").as[String]
        password = (config \ "password").as[String]
        tls = (config \ "tls").asOpt[Boolean].getOrElse(true)

        from = (config \ "from").as[String]
        to = (config \ "to").as[String]
        cc = (config \ "cc").as[String]
        bcc = (config \ "bcc").as[String]
        subject = (config \ "subject").as[String]
        body = (config \ "body").as[String]

        contentType = (config \ "content_type").asOpt[String].getOrElse("html")
        waitForSent = (config \ "wait_for_sent").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM { data =>
        val futs = Future.sequence(data.data.map { datum =>
            // Evaluate all fields
            val md = new MailerDef(
                utils.evaluateTuktuString(serverName, datum),
                utils.evaluateTuktuString(port.toString, datum).toInt,
                utils.evaluateTuktuString(username, datum),
                utils.evaluateTuktuString(password, datum),
                utils.evaluateTuktuString(tls.toString, datum).toBoolean)
            // Get mailer if set
            val mailer = if (mailers.contains(md)) mailers(md) else {
                val m = Mailer(md.serverName, md.port).auth(true).as(md.username, md.password).startTtls(md.tls)()
                mailers += md -> m
                m
            }

            // Set up actual e-mail
            val toList = {
                val e = utils.evaluateTuktuString(to, datum)
                if (e.isEmpty) Array[InternetAddress]() else e.split(",").map(e => new InternetAddress(e))
            }
            val ccList = {
                val e = utils.evaluateTuktuString(cc, datum)
                if (e.isEmpty) Array[InternetAddress]() else e.split(",").map(e => new InternetAddress(e))
            }
            val bccList = {
                val e = utils.evaluateTuktuString(bcc, datum)
                if (e.isEmpty) Array[InternetAddress]() else e.split(",").map(e => new InternetAddress(e))
            }
            val mail = Envelope.from(new InternetAddress(utils.evaluateTuktuString(from, datum)))
                .to(toList: _*)
                .cc(ccList: _*)
                .bcc(bccList: _*)
                .subject(utils.evaluateTuktuString(subject, datum))
                .content(contentType match {
                    case "text" => Text(utils.evaluateTuktuString(body, datum))
                    case _      => Multipart().html(utils.evaluateTuktuString(body, datum))
                })

            // Send it
            val fut = mailer(mail)
            fut.onFailure {
                case e: Throwable => {
                    Logger.warn("Failed to send e-mail using SMTP. Reason: " + e.getMessage)
                }
            }

            fut
        })

        if (waitForSent) Future {
            Await.ready(futs, timeout.duration)
            data
        }
        else Future { data }
    }
}