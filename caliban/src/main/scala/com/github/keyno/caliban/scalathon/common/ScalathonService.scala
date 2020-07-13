package com.github.keyno.caliban.scalathon.common

import zio.{Has, Ref, UIO, URIO, ZLayer}

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

