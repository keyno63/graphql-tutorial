package com.github.keyno.caliban.scalathon

import caliban.PlayRouter
import caliban.interop.circe.AkkaHttpCirceAdapter
import com.github.keyno.caliban.scalathon.ScalathonData.{movies, theaters}
import com.github.keyno.caliban.scalathon.ScalathonService.ScalathonService
import play.api.Mode
import play.api.mvc.DefaultControllerComponents
import play.core.server.{AkkaHttpServer, ServerConfig}
import zio.Runtime
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform

import scala.io.StdIn.readLine

object ScalathonPlayApp extends scala.App with AkkaHttpCirceAdapter {

  implicit val runtime: Runtime[ScalathonService with Console with Clock] =
    Runtime.unsafeFromLayer(ScalathonService.make(movies, theaters) ++ Console.live ++ Clock.live, Platform.default)

  val interpreter = runtime.unsafeRun(ScalathonApi.api.interpreter)

  private val runHost = "localhost"
  private val runPort = 8090

  val server = AkkaHttpServer.fromRouterWithComponents(
    ServerConfig(
      mode = Mode.Dev,
      port = Some(runPort),
      address = runHost
    )
  ) { components =>
    PlayRouter(
      interpreter,
      DefaultControllerComponents(
        components.defaultActionBuilder,
        components.playBodyParsers,
        components.messagesApi,
        components.langs,
        components.fileMimeTypes,
        components.executionContext
      )
    )(runtime, components.materializer).routes
  }

  println(s"Server online at http://${runHost}:${runPort}/\nPress RETURN to stop...")
  readLine()
  server.stop()
}
