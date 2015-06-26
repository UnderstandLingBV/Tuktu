EclipseKeys.createSrc := EclipseCreateSrc.All

javaOptions += "-Xmax-classfile-name 100"

lazy val appResolvers = Seq(
    "JCenter" at "http://jcenter.bintray.com/",
    "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository"
)

lazy val apiDependencies = Seq(
    cache,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "org.apache.hadoop" % "hadoop-client" % "2.6.0",
    "com.netaporter" %% "scala-uri" % "0.4.7"
)

lazy val nlpDependencies = Seq(
    cache,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "nl.et4it" % "LIGA" % "1.0",
    "nl.et4it" % "OpenNLPPOSWrapper" % "1.0",
    "nl.et4it" % "RBEM" % "1.0",
    "org.apache.opennlp" % "opennlp-tools" % "1.5.3"
)

lazy val csvDependencies = Seq(
    cache,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "net.sf.opencsv" % "opencsv" % "2.0",
    "org.apache.poi" % "poi" % "3.11-beta2",
    "org.apache.poi" % "poi-ooxml" % "3.11-beta2"
)

lazy val socialDependencies = Seq(
    cache,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "org.twitter4j" % "twitter4j-core" % "[4.0,)",
    "org.twitter4j" % "twitter4j-stream" % "[4.0,)",
    "org.scribe" % "scribe" % "1.3.5",
    "com.googlecode.batchfb" % "batchfb" % "2.1.5"
)

lazy val nosqlDependencies = Seq(
    jdbc,
    anorm,
    cache,
    ws,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "mysql" % "mysql-connector-java" % "5.1.34",
    "org.mariadb.jdbc" % "mariadb-java-client" % "1.1.7",
    "com.h2database" % "h2" % "1.3.176",
    "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
    "org.xerial" % "sqlite-jdbc" % "3.8.7",
    "org.apache.kafka" %% "kafka" % "0.8.2-beta",
    "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23",
    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.4",
    "org.elasticsearch" % "elasticsearch" % "1.4.4",
    "org.apache.hadoop" % "hadoop-client" % "2.6.0"
)

lazy val mlDependencies = Seq(
    cache,
    "org.apache.commons" % "commons-math3" % "3.5",
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val webDependencies = Seq(
    ws,
    cache,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val coreDependencies = Seq(
    jdbc,
    anorm,
    cache,
    ws,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "net.sf.opencsv" % "opencsv" % "2.0",
    "org.codehaus.groovy" % "groovy-all" % "2.2.1",
    "com.typesafe.akka" %% "akka-remote" % "2.3.4",
    "com.github.nscala-time" %% "nscala-time" % "1.8.0",
    "joda-time" % "joda-time" % "2.7",
    "org.apache.commons" % "commons-math3" % "3.5",
    "org.reflections" % "reflections" % "0.9.10"
)

lazy val tuktuDBDependencies = Seq(
    cache,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val dfsDependencies = Seq(
    cache,
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "com.typesafe.akka" %% "akka-remote" % "2.3.4"
)

lazy val api = (project in file("modules/api"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-api")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= apiDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    
lazy val nlp = (project in file("modules/nlp"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-nlp")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= nlpDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val csv = (project in file("modules/csv"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-csv")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= csvDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val social = (project in file("modules/social"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-social")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= socialDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val nosql = (project in file("modules/nosql"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-nosql")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= nosqlDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val ml = (project in file("modules/ml"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-ml")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= mlDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val web = (project in file("modules/web"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-web")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= webDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val tuktudb = (project in file("modules/tuktudb"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-DB")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= tuktuDBDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val dfs = (project in file("modules/dfs"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-DFS")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= dfsDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)

lazy val root = project
    .in(file("."))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.6")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= coreDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api, nlp, csv, dfs, social, nosql, ml, web, tuktudb)
    .dependsOn(api, nlp, csv, dfs, social, nosql, ml, web, tuktudb)