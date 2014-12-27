lazy val appResolvers = Seq(
    "JCenter" at "http://jcenter.bintray.com/",
    "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository"
)

lazy val nlpDependencies = Seq(
	"nl.et4it" % "LIGA" % "1.0",
    "nl.et4it" % "OpenNLPPOSWrapper" % "1.0",
    "nl.et4it" % "RBEM" % "1.0",
    "nl.et4it" %% "rhetorics" % "1.0"
)

lazy val csvDependencies = Seq(
    "net.sf.opencsv" % "opencsv" % "2.0",
    "org.apache.poi" % "poi" % "3.11-beta2",
    "org.apache.poi" % "poi-ooxml" % "3.11-beta2"
)

lazy val socialDependencies = Seq(
    "org.twitter4j" % "twitter4j-core" % "[4.0,)",
    "org.twitter4j" % "twitter4j-stream" % "[4.0,)",
    "org.scribe" % "scribe" % "1.3.5",
    "com.googlecode.batchfb" % "batchfb" % "2.1.5"
)

lazy val nosqlDependencies = Seq(
    "org.apache.kafka" %% "kafka" % "0.8.2-beta",
    "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23"
)

lazy val coreDependencies = Seq(
    jdbc,
    anorm,
    cache,
    ws,
    "net.sf.opencsv" % "opencsv" % "2.0",
    "org.codehaus.groovy" % "groovy-all" % "2.2.1",
    "com.typesafe.akka" %% "akka-remote" % "2.3.4"
)

lazy val api = (project in file("modules/api"))
    .enablePlugins(PlayScala)
	.settings(name := "Tuktu-api")
	.settings(version := "0.1")
	.settings(scalaVersion := "2.11.1")
	.settings(resolvers ++= appResolvers)
	.settings(EclipseKeys.skipParents in ThisBuild := false)
	
lazy val nlp = (project in file("modules/nlp"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-nlp")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.1")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= nlpDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val csv = (project in file("modules/csv"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-csv")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.1")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= csvDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val social = (project in file("modules/social"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-social")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.1")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= socialDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val nosql = (project in file("modules/nosql"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-nosql")
    .settings(version := "0.1")
    .settings(scalaVersion := "2.11.1")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= nosqlDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)

lazy val root = project
    .in(file("."))
	.enablePlugins(PlayScala)
	.settings(name := "Tuktu")
	.settings(version := "0.1")
	.settings(scalaVersion := "2.11.1")
	.settings(resolvers ++= appResolvers)
	.settings(libraryDependencies ++= coreDependencies)
	.settings(EclipseKeys.skipParents in ThisBuild := false)
	.aggregate(api, nlp, csv, social, nosql)
    .dependsOn(api, nlp, csv, social, nosql)