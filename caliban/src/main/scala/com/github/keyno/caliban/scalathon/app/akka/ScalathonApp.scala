package com.github.keyno.caliban.scalathon.app.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{getFromResource, path}
import akka.http.scaladsl.server.Directives._
import caliban.interop.circe.AkkaHttpCirceAdapter
import com.github.keyno.caliban.scalathon.common.ScalathonData.{movies, theaters}
import com.github.keyno.caliban.scalathon.common.ScalathonService.ScalathonService
import com.github.keyno.caliban.scalathon.common.{ScalathonApi, ScalathonService}
import com.github.keyno.caliban.scalathon.common.Commons._
import zio.Runtime
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object ScalathonApp extends scala.App with AkkaHttpCirceAdapter {
  implicit val system: ActorSystem                        = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val runtime: Runtime[ScalathonService with Console with Clock] =
    Runtime.unsafeFromLayer(ScalathonService.make(movies, theaters) ++ Console.live ++ Clock.live, Platform.default)
  val interpreter = runtime.unsafeRun(ScalathonApi.api.interpreter)
  val route =
    path("api" / "graphql") {
      adapter.makeHttpService(interpreter)
    } ~ path("graphql") {
      adapter.makeHttpService(interpreter)
    } ~ path("playground") {
      getFromResource("playground.html")
    }
  val bindingFuture = Http().bindAndHandle(route, runHost, runPort)
  println(s"Server online at http://${runHost}:${runPort}/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
