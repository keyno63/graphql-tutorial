package com.github.keyno.caliban

import java.util.Date

case class Movie (
   id: Int,
   title: String,
   //start: Date
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

  val movies = List(
    Movie(1, "ドクター／ドリトル"/*, new Date("2020/06/19")*/),
    Movie(2, "プロメア"/*, new Date("2020/06/01")*/),
    Movie(3, "はちどり"/*, new Date("2020/06/20")*/),
  )
}