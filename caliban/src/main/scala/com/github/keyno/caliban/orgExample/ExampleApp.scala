package com.github.keyno.caliban.orgExample

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import caliban.interop.circe.AkkaHttpCirceAdapter
import com.github.keyno.caliban.orgExample.ExampleService.ExampleService
import com.github.keyno.caliban.orgExample.ExampleData.sampleCharacters
import zio.Runtime
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

// copy from caliban akka sample from github.
object ExampleApp extends scala.App with AkkaHttpCirceAdapter {
  implicit val system: ActorSystem                        = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val runtime: Runtime[ExampleService with Console with Clock] =
    Runtime.unsafeFromLayer(ExampleService.make(sampleCharacters) ++ Console.live ++ Clock.live, Platform.default)

  val interpreter = runtime.unsafeRun(ExampleApi.api.interpreter)

  private val runHost = "localhost"
  private val runPort = 8088
  /**
   * Please see sampleRequest.txt, example request.
   */
  val route =
    path("api" / "graphql") {
      adapter.makeHttpService(interpreter)
    } ~ path("ws" / "graphql") {
      adapter.makeWebSocketService(interpreter)
    } ~ path("graphiql") {
      getFromResource("graphiql.html")
    }

  val bindingFuture = Http().bindAndHandle(route, runHost, runPort)
  println(s"Server online at http://${runHost}:${runPort}/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
