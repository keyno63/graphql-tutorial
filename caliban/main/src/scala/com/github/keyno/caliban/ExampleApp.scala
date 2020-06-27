package com.github.keyno.caliban

package com.github.keyno63.caliban

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.io.StdIn
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.github.keyno63.caliban.ExampleData._
import com.github.keyno63.caliban.ExampleService.ExampleService
import com.github.keyno63.caliban.ExampleData.Origin._
import com.github.keyno63.caliban.ExampleData.Role._
import caliban.{GraphQL, RootResolver}
import caliban.interop.circe.AkkaHttpCirceAdapter
import caliban.GraphQL.graphQL
import caliban.schema.Annotations.{GQLDeprecated, GQLDescription}
import caliban.schema.GenericSchema
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import zio.internal.Platform
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream.ZStream

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

object ExampleData {

  sealed trait Origin

  object Origin {
    case object EARTH extends Origin
    case object MARS  extends Origin
    case object BELT  extends Origin
  }

  sealed trait Role

  object Role {
    case class Captain(shipName: String)  extends Role
    case class Pilot(shipName: String)    extends Role
    case class Engineer(shipName: String) extends Role
    case class Mechanic(shipName: String) extends Role
  }

  case class Character(name: String, nicknames: List[String], origin: Origin, role: Option[Role])

  case class CharactersArgs(origin: Option[Origin])
  case class CharacterArgs(name: String)

  val sampleCharacters = List(
    Character("James Holden", List("Jim", "Hoss"), EARTH, Some(Captain("Rocinante"))),
    Character("Naomi Nagata", Nil, BELT, Some(Engineer("Rocinante"))),
    Character("Amos Burton", Nil, EARTH, Some(Mechanic("Rocinante"))),
    Character("Alex Kamal", Nil, MARS, Some(Pilot("Rocinante"))),
    Character("Chrisjen Avasarala", Nil, EARTH, None),
    Character("Josephus Miller", List("Joe"), BELT, None),
    Character("Roberta Draper", List("Bobbie", "Gunny"), MARS, None)
  )
}

object ExampleService {

  type ExampleService = Has[Service]

  trait Service {
    def getCharacters(origin: Option[Origin]): UIO[List[Character]]

    def findCharacter(name: String): UIO[Option[Character]]

    def deleteCharacter(name: String): UIO[Boolean]

    def deletedEvents: ZStream[Any, Nothing, String]
  }

  def getCharacters(origin: Option[Origin]): URIO[ExampleService, List[Character]] =
    URIO.accessM(_.get.getCharacters(origin))

  def findCharacter(name: String): URIO[ExampleService, Option[Character]] =
    URIO.accessM(_.get.findCharacter(name))

  def deleteCharacter(name: String): URIO[ExampleService, Boolean] =
    URIO.accessM(_.get.deleteCharacter(name))

  def deletedEvents: ZStream[ExampleService, Nothing, String] =
    ZStream.accessStream(_.get.deletedEvents)

  def make(initial: List[Character]): ZLayer[Any, Nothing, ExampleService] = ZLayer.fromEffect {
    for {
      characters  <- Ref.make(initial)
      subscribers <- Ref.make(List.empty[Queue[String]])
    } yield new Service {

      def getCharacters(origin: Option[Origin]): UIO[List[Character]] =
        characters.get.map(_.filter(c => origin.forall(c.origin == _)))

      def findCharacter(name: String): UIO[Option[Character]] = characters.get.map(_.find(c => c.name == name))

      def deleteCharacter(name: String): UIO[Boolean] =
        characters
          .modify(list =>
            if (list.exists(_.name == name)) (true, list.filterNot(_.name == name))
            else (false, list)
          )
          .tap(deleted =>
            UIO.when(deleted)(
              subscribers.get.flatMap(
                // add item to all subscribers
                UIO.foreach(_)(queue =>
                  queue
                    .offer(name)
                    .catchSomeCause {
                      case cause if cause.interrupted =>
                        subscribers.update(_.filterNot(_ == queue)).as(false)
                    } // if queue was shutdown, remove from subscribers
                )
              )
            )
          )

      def deletedEvents: ZStream[Any, Nothing, String] = ZStream.unwrap {
        for {
          queue <- Queue.unbounded[String]
          _     <- subscribers.update(queue :: _)
        } yield ZStream.fromQueue(queue).ensuring(queue.shutdown)
      }
    }
  }
}


object ExampleApi extends GenericSchema[ExampleService] {

  case class Queries(
                      @GQLDescription("Return all characters from a given origin")
                      characters: CharactersArgs => URIO[ExampleService, List[Character]],
                      @GQLDeprecated("Use `characters`")
                      character: CharacterArgs => URIO[ExampleService, Option[Character]]
                    )
  case class Mutations(deleteCharacter: CharacterArgs => URIO[ExampleService, Boolean])
  case class Subscriptions(characterDeleted: ZStream[ExampleService, Nothing, String])

  implicit val roleSchema           = gen[Role]
  implicit val characterSchema      = gen[Character]
  implicit val characterArgsSchema  = gen[CharacterArgs]
  implicit val charactersArgsSchema = gen[CharactersArgs]

  val api: GraphQL[Console with Clock with ExampleService] =
    graphQL(
      RootResolver(
        Queries(
          args => ExampleService.getCharacters(args.origin),
          args => ExampleService.findCharacter(args.name)
        ),
        Mutations(args => ExampleService.deleteCharacter(args.name)),
        Subscriptions(ExampleService.deletedEvents)
      )
    ) @@
      maxFields(200) @@               // query analyzer that limit query fields
      maxDepth(30) @@                 // query analyzer that limit query depth
      timeout(3 seconds) @@           // wrapper that fails slow queries
      printSlowQueries(500 millis) @@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing

}
