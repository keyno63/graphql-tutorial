package com.github.keyno.caliban.scalathon.common

import caliban.GraphQL.graphQL
import caliban.{GraphQL, RootResolver}
import caliban.schema.Annotations.GQLDescription
import caliban.schema.GenericSchema
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import com.github.keyno.caliban.scalathon.common.ScalathonData.{MoviewArgs, MoviewsArgs, TheaterArgs, TheatersArgs}
import com.github.keyno.caliban.scalathon.common.ScalathonService.ScalathonService
import zio.URIO
import zio.clock.Clock
import zio.console.Console
import zio.duration._

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

