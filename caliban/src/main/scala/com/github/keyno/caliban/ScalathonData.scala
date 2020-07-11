package com.github.keyno.caliban

import java.time.LocalDate

case class Movie (
   id: Int,
   title: String,
   start: LocalDate
)

case class Theater (
   id: Int,
   name: String,
   address: String,
   movies: List[Movie]
)

// Schema 定義
object ScalathonData {
  case class MoviewsArgs()
  case class MoviewArgs(id: Int)
  case class TheatersArgs()
  case class TheaterArgs(id: Int)

  val movies = List(
    Movie(1, "ドクター／ドリトル", LocalDate.parse("2020-06-19")),
    Movie(2, "プロメア", LocalDate.parse("2020-06-01")),
    Movie(3, "はちどり", LocalDate.parse("2020-06-20")),
  )

  val theaters = List(
    Theater(1, "バルト9", "東京都新宿区新宿３丁目１−２６ 新宿三丁目ビル 9階",
      List(
        Movie(1, "ドクター／ドリトル", LocalDate.parse("2020-06-19")),
        Movie(2, "プロメア", LocalDate.parse("2020-06-01"))
      )
    )
  )
}