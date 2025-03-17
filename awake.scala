//> using scala 2.13
//> using jvm system
//> using javaOpt -Xmx60m -XX:+UseSerialGC
//> using plugin com.olegpy::better-monadic-for:0.3.1
//> using dep com.bot4s::telegram-core:5.8.4
//> using dep org.typelevel::cats-effect:3.5.7
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-cats::3.10.3
//> using dep org.slf4j:slf4j-simple:2.0.17
//> using dep org.typelevel::log4cats-slf4j:2.7.0
//> using dep com.lihaoyi::requests:0.9.0
//> using dep com.lihaoyi::os-lib::0.11.4

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.bot4s.telegram.api.declarative.Action
import com.bot4s.telegram.api.declarative.CommandFilterMagnet
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.cats._
import com.bot4s.telegram.marshalling.CirceEncoders._
import com.bot4s.telegram.methods._
import com.bot4s.telegram.models._
import io.circe.syntax._
import org.asynchttpclient.Dsl.asyncHttpClient
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

import scala.collection.immutable
import com.bot4s.telegram.api.declarative.Callbacks

object Launcher extends IOApp {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  override def run(args: List[String]): IO[ExitCode] = {
    val envs = util.Properties
      .envOrNone("TELEGRAM_TOKEN")
      .zip(util.Properties.envOrNone("MY_TELEGRAM_ID"))
    envs match {
      case Some((token, myTelegramId)) =>
        for {
          _ <- Logger[IO].info("Telegram bot started.")
          r <- sendStartupMessage(token, myTelegramId)
          _ <- Logger[IO].info("startup status code: " + r.statusCode)
          _ <- new MonitorBot[IO](token, myTelegramId.toLong).startPolling()
        } yield ExitCode.Success
      case None =>
        Logger[IO].error("Env variables not defined")
          .map(_ => ExitCode.Error)
    }
  }

  def sendStartupMessage(token: String, myTelegramId: String) = IO {
    val url = s"https://api.telegram.org/bot$token/sendMessage"

    val data = Map(
      "chat_id" -> myTelegramId, 
      "text" -> "startup"
    )

    requests.post(url, data = data)
  }
}

class MonitorBot[F[_]: Async](token: String, myTelegramId: Long)
    extends TelegramBot[F](
      token,
      AsyncHttpClientCatsBackend.usingClient[F](asyncHttpClient())
    )
    with Polling[F]
    with Commands[F]
    with Callbacks[F] {

  implicit def logger[F[_]: Sync]: Logger[F] = Slf4jLogger.getLogger[F]

  def onlyForMyUser(f: => F[Unit])(implicit msg: Message): F[Unit] = {
    msg.from match {
      case None =>
        Logger[F].info(
          s"Received message from no user: <${msg.text.getOrElse("empty")}>"
        )
      case Some(user) if user.id === myTelegramId => f
      case Some(user) =>
        Logger[F].warn(
          s"Received message from ${user.asJson.deepDropNullValues.noSpaces}: <${msg.text.getOrElse("empty")}>"
        )
    }
  }

  onCommand("start") { implicit msg =>
    reply("I am not a public bot.").void
  }

  onCommand("help") { implicit msg =>
    onlyForMyUser {
      replyMdV2(
        """
        |/alive check if I am alive
        |/cpu utilization
        |/temperature cpu temperature
        |""".stripMargin
      ).void
    }
  }

  onCommand("alive") { implicit msg =>
    onlyForMyUser { reply("Yes").void }
  }

  // onCommand("cpu") { implicit msg => 
  //   onlyForMyUser {
  //     reply(os.call("G_OBTAIN_CPU_USAGE").out.trim() + "%").void
  //   }  
  // }

  onCommand("temperature") { implicit msg => 
    onlyForMyUser {
      val temp = os.call(cmd = ("cat","/sys/class/thermal/thermal_zone0/temp"))
        .out
        .trim()
        .toIntOption
        .getOrElse(0) / 1000
      reply(temp + "°C").void
    }  
  }

}
