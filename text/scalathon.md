# scalahton.md  

## caliban の入門(20/06/27)  

### GraphQL ?

APIプロトコル  
HTTP POST、Body のクエリで処理を実行  
Schema 定義によるデータ操作  
必要なレスポンスを取捨選択、一括操作  
REST appとの互換...？キャッシュ...？  

|GraphQL|REST|DB SQL|備考|  
| --- | --- | --- | --- |
|Query|GET|SELECT|副作用なし、データの取得|  
|Mutation|POST/DELETE/PUT...|INSEERT/UPDATE/DELETE|副作用なし、データの取得|  
|Subscribe|?|?|状態の更新通知?|  

### GrpahQL for Scala

* [sangria](https://github.com/sangria-graphql/sangria)
Top of MAJOR、[graphql.org/code](https://graphql.org/code/#scala)  
助けてくれる人募集中とのこと  
* [graphcool](https://github.com/prisma/graphcool-framework)  
最終更新が２年前  
* [caliban](https://github.com/ghostdogpr/caliban)  
2019 release?  
Pure Functional、[ZIO](https://github.com/zio/zio)  

### やったこと
「sangria はすごい。でもボイラーテンプレート感」（[拙作](https://github.com/keyno63/sangria-play-sample/blob/master/app/domain/graphql/SchemaDefinition.scala)）  
akka adaptor を使って、独自定義の Schema のデータ取得実装    
localhost:8090 に Query  

DBクエリ、Play、複数データ取得、Mutation・Subscribe はごめんなさい  
ZIO...

### start
```
caliban/com.github.keyno.caliban.ScalathonApp
```
