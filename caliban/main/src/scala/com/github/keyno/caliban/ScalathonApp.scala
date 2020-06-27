package com.github.keyno.caliban

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import caliban.{GraphQL, RootResolver}
import caliban.GraphQL.graphQL
import caliban.interop.circe.AkkaHttpCirceAdapter
import caliban.schema.Annotations.GQLDescription
import caliban.schema.GenericSchema
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import com.github.keyno.caliban.ScalathonData.{MoviewArgs, MoviewsArgs}
import com.github.keyno.caliban.ScalathonService.ScalathonService
import zio.clock.Clock
import zio.console.Console
import zio.{Has, Ref, Runtime, UIO, URIO, ZLayer}
import zio.duration._
import caliban.wrappers.ApolloTracing.apolloTracing

import scala.concurrent.ExecutionContextExecutor
import com.github.keyno.caliban.ScalathonData.movies
import zio.internal.Platform

import scala.io.StdIn

object ScalathonApp extends scala.App with AkkaHttpCirceAdapter {
  implicit val system: ActorSystem                        = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val runtime: Runtime[ScalathonService with Console with Clock] =
    Runtime.unsafeFromLayer(ScalathonService.make(movies) ++ Console.live ++ Clock.live, Platform.default)
  val interpreter = runtime.unsafeRun(ScalathonApi.api.interpreter)
  private val runHost = "localhost"
  private val runPort = 8090
  val route =
    path("api" / "graphql") {
      adapter.makeHttpService(interpreter)
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

// データアクセス
object ScalathonService {

  // URIO のために必要
  type ScalathonService = Has[ServiceBase]
  trait ServiceBase {
    def getMovie(id: Int): UIO[Option[Movie]]
    def getMovies(): UIO[List[Movie]]
  }
  // URIO の引数は case class が必要
  def getMovie(id: Int): URIO[ScalathonService, Option[Movie]] =
    URIO.accessM(_.get.getMovie(id))
  def getMovies(): URIO[ScalathonService, List[Movie]] =
    URIO.accessM(_.get.getMovies())

  // make
  def make(initial: List[Movie]): ZLayer[Any, Nothing, ScalathonService] = ZLayer.fromEffect {
    for {
      movies <- Ref.make(initial)
    } yield new ServiceBase {
      override def getMovie(id: Int): UIO[Option[Movie]] = movies.get.map(_.find(m => m.id == id))

      override def getMovies(): UIO[List[Movie]] = movies.get // filter しないので全部返す
    }
  }
}

object ScalathonApi extends GenericSchema[ScalathonService] {
  case class Queries(
                    @GQLDescription("movie")
                    movie: MoviewArgs => URIO[ScalathonService, Option[Movie]],
                    @GQLDescription("movies")
                    movies: MoviewsArgs => URIO[ScalathonService, List[Movie]]
                    )

  // データのスキーマ
  implicit val movieSchema = gen[Movie]
  // 引数のスキーマ
  implicit val movieArgSchema = gen[MoviewArgs]
  implicit val moviesArgSchema = gen[MoviewsArgs]

  val api: GraphQL[Console with Clock with ScalathonService] =
    graphQL(
      RootResolver(
        Queries(
          args => ScalathonService.getMovie(args.id),
          args => ScalathonService.getMovies()
        )
      )
    ) @@ // たぶんオプションやの、チューニングパラメーター
      maxFields(200) @@               // query analyzer that limit query fields
      maxDepth(30) @@                 // query analyzer that limit query depth
      timeout(3 seconds) @@           // wrapper that fails slow queries
      printSlowQueries(500 millis) /*@@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing*/
}

