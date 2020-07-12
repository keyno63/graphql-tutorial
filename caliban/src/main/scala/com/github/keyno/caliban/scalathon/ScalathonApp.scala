package com.github.keyno.caliban.scalathon

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import caliban.GraphQL.graphQL
import caliban.interop.circe.AkkaHttpCirceAdapter
import caliban.schema.Annotations.GQLDescription
import caliban.schema.GenericSchema
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{GraphQL, RootResolver}
import com.github.keyno.caliban.scalathon.ScalathonData._
import com.github.keyno.caliban.scalathon.ScalathonService.ScalathonService
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.internal.Platform
import zio.{Has, Ref, Runtime, UIO, URIO, ZLayer}

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object ScalathonApp extends scala.App with AkkaHttpCirceAdapter {
  implicit val system: ActorSystem                        = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val runtime: Runtime[ScalathonService with Console with Clock] =
    Runtime.unsafeFromLayer(ScalathonService.make(movies, theaters) ++ Console.live ++ Clock.live, Platform.default)
  val interpreter = runtime.unsafeRun(ScalathonApi.api.interpreter)
  private val runHost = "localhost"
  private val runPort = 8090
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

// データアクセス
object ScalathonService {

  // URIO のために必要
  type ScalathonService = Has[ServiceBase]
  trait ServiceBase {
    def getMovie(id: Int): UIO[Option[Movie]]
    def getMovies(): UIO[List[Movie]]
    def getTheater(id: Int): UIO[Option[Theater]]
    def getTheaters(): UIO[List[Theater]]
  }
  // URIO の引数は case class が必要
  def getMovie(id: Int): URIO[ScalathonService, Option[Movie]] =
    URIO.accessM(_.get.getMovie(id))
  def getMovies(): URIO[ScalathonService, List[Movie]] =
    URIO.accessM(_.get.getMovies())
  def getTheater(id: Int): URIO[ScalathonService, Option[Theater]] =
    URIO.accessM(_.get.getTheater(id))
  def getTheaters(): URIO[ScalathonService, List[Theater]] =
    URIO.accessM(_.get.getTheaters())

  // make. runtime の生成に使用.
  def make(initial: List[Movie], initialTheater: List[Theater]): ZLayer[Any, Nothing, ScalathonService] = ZLayer.fromEffect {
    for {
      movies <- Ref.make(initial)
      theaters <- Ref.make(initialTheater)
    } yield new ServiceBase {
      override def getMovie(id: Int): UIO[Option[Movie]] = movies.get.map(_.find(m => m.id == id))

      override def getMovies(): UIO[List[Movie]] = movies.get // filter しないので全部返す

      override def getTheater(id: Int): UIO[Option[Theater]] = theaters.get.map(_.find(t => t.id == id))

      override def getTheaters(): UIO[List[Theater]] = theaters.get // filter しないので全部返す
    }
  }
}

object ScalathonApi extends GenericSchema[ScalathonService] {
  case class Queries(
                    @GQLDescription("movie")
                    movie: MoviewArgs => URIO[ScalathonService, Option[Movie]],
                    @GQLDescription("movies")
                    movies: MoviewsArgs => URIO[ScalathonService, List[Movie]],
                    @GQLDescription("theater")
                    theater: TheaterArgs => URIO[ScalathonService, Option[Theater]],
                    @GQLDescription("theaters")
                    theaters: TheatersArgs => URIO[ScalathonService, List[Theater]],
                    )

  // データのスキーマ
  implicit val movieSchema: ScalathonApi.Typeclass[Movie] = gen[Movie]
  implicit val theaterSchema: ScalathonApi.Typeclass[Theater] = gen[Theater]
  // 引数のスキーマ
  implicit val movieArgSchema: ScalathonApi.Typeclass[MoviewArgs] = gen[MoviewArgs]
  implicit val moviesArgSchema: ScalathonApi.Typeclass[MoviewsArgs] = gen[MoviewsArgs]
  implicit val theaterArgSchema: ScalathonApi.Typeclass[TheaterArgs] = gen[TheaterArgs]
  implicit val theatersArgSchema: ScalathonApi.Typeclass[TheatersArgs] = gen[TheatersArgs]

  val api: GraphQL[Console with Clock with ScalathonService] =
    graphQL(
      RootResolver(
        Queries(
          args => ScalathonService.getMovie(args.id),
          _ => ScalathonService.getMovies(),
          args => ScalathonService.getTheater(args.id),
          _ => ScalathonService.getTheaters(),
        )
      )
    ) @@ // たぶんオプションやの、チューニングパラメーター
      maxFields(200) @@               // query analyzer that limit query fields
      maxDepth(30) @@                 // query analyzer that limit query depth
      timeout(3 seconds) @@           // wrapper that fails slow queries
      printSlowQueries(500 millis) /*@@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing*/
}

