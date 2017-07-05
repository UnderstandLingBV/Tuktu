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
import tuktu.api.utils.evaluateTuktuString
import play.api.Logger

case class MailerDef(
    serverName: String, port: Int, username: String, password: String, tls: Boolean)

/**
 * Sends e-mail using SMTP
 */
class SMTPProcessor(resultName: String) extends BaseProcessor(resultName) {
    implicit val timeout = Timeout(Cache.getAs[Int]("timeout").getOrElse(5) seconds)

    var serverName: evaluateTuktuString.TuktuStringRoot = _
    var port: Int = _
    var username: evaluateTuktuString.TuktuStringRoot = _
    var password: evaluateTuktuString.TuktuStringRoot = _
    var tls: Boolean = _

    var from: evaluateTuktuString.TuktuStringRoot = _
    var to: evaluateTuktuString.TuktuStringRoot = _
    var cc: evaluateTuktuString.TuktuStringRoot = _
    var bcc: evaluateTuktuString.TuktuStringRoot = _
    var subject: evaluateTuktuString.TuktuStringRoot = _
    var body: evaluateTuktuString.TuktuStringRoot = _

    var contentType: evaluateTuktuString.TuktuStringRoot = _
    var waitForSent: Boolean = _

    val mailers = collection.mutable.Map.empty[MailerDef, Mailer]

    override def initialize(config: JsObject) {
        serverName = evaluateTuktuString.prepare((config \ "server_name").as[String])
        port = (config \ "port").as[Int]
        username = evaluateTuktuString.prepare((config \ "username").as[String])
        password = evaluateTuktuString.prepare((config \ "password").as[String])
        tls = (config \ "tls").asOpt[Boolean].getOrElse(true)

        from = evaluateTuktuString.prepare((config \ "from").as[String])
        to = evaluateTuktuString.prepare((config \ "to").as[String])
        cc = evaluateTuktuString.prepare((config \ "cc").as[String])
        bcc = evaluateTuktuString.prepare((config \ "bcc").as[String])
        subject = evaluateTuktuString.prepare((config \ "subject").as[String])
        body = evaluateTuktuString.prepare((config \ "body").as[String])

        contentType = evaluateTuktuString.prepare((config \ "content_type").asOpt[String].getOrElse("html"))
        waitForSent = (config \ "wait_for_sent").asOpt[Boolean].getOrElse(false)
    }

    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM { data =>
        val futs = Future.sequence(data.data.map { datum =>
            // Evaluate all fields
            val md = new MailerDef(
                serverName.evaluate(datum),
                port,
                username.evaluate(datum),
                password.evaluate(datum),
                tls)
            // Get mailer if set
            val mailer = if (mailers.contains(md)) mailers(md) else {
                val m = Mailer(md.serverName, md.port).auth(true).as(md.username, md.password).startTtls(md.tls)()
                mailers += md -> m
                m
            }

            // Set up actual e-mail
            val toList = {
                val e = to.evaluate(datum)
                if (e.isEmpty) Array[InternetAddress]() else e.trim.split("\\s*,\\s*").map(e => new InternetAddress(e))
            }
            val ccList = {
                val e = cc.evaluate(datum)
                if (e.isEmpty) Array[InternetAddress]() else e.trim.split("\\s*,\\s*").map(e => new InternetAddress(e))
            }
            val bccList = {
                val e = bcc.evaluate(datum)
                if (e.isEmpty) Array[InternetAddress]() else e.trim.split("\\s*,\\s*").map(e => new InternetAddress(e))
            }
            val mail = Envelope.from(new InternetAddress(from.evaluate(datum)))
                .to(toList: _*)
                .cc(ccList: _*)
                .bcc(bccList: _*)
                .subject(subject.evaluate(datum))
                .content(contentType.evaluate(datum) match {
                    case "text" => Text(body.evaluate(datum))
                    case _      => Multipart().html(body.evaluate(datum))
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