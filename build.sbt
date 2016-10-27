import NativePackagerHelper._

EclipseKeys.createSrc := EclipseCreateSrc.All

mappings in Universal ++= 
    directory("documentation") ++
    directory("images") ++
    (file("modules/modeller/meta").*** pair basic) ++
    (file("configs/analytics/localhost").*** pair basic) ++
    Seq(file("nft.data") -> "nft.data",
        file("configs/crime_tutorial_example.json") -> "configs/crime_tutorial_example.json",       
        file("configs/social_tutorial_example.json") -> "configs/social_tutorial_example.json",
        file("public/images/pixel.gif") -> "public/images/pixel.gif"
    )

lazy val appResolvers = Seq(
    "JCenter" at "http://jcenter.bintray.com/",
    "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository"
)

lazy val modellerDependencies = Seq(
    cache,
    filters,
    "org.webjars" % "jquery" % "1.11.3",
    "org.webjars" % "bootstrap" % "3.3.4",
    "org.webjars" % "raphaeljs" % "2.1.2-1",
    "org.webjars" % "underscorejs" % "1.8.3"
)

lazy val restApiDependencies = Seq(
    cache
)

lazy val awsDependencies = Seq(
    cache,
	"com.amazonaws" % "amazon-kinesis-client" % "1.7.0"
)

lazy val apiDependencies = Seq(
    cache,
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.1",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "org.apache.hadoop" % "hadoop-client" % "2.7.3" excludeAll(ExclusionRule(organization = "org.slf4j")),
    "com.netaporter" %% "scala-uri" % "0.4.7",
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.3.0-akka-2.3.x",
    "org.apache.commons" % "commons-collections4" % "4.1",
    "com.lihaoyi" %% "fastparse" % "0.4.1"
)

lazy val nlpDependencies = Seq(
    cache,
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "org.apache.opennlp" % "opennlp-tools" % "1.5.3",
    "com.github.rholder" % "snowball-stemmer" % "1.3.0.581.1"
)

lazy val csvDependencies = Seq(
    cache,
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "com.opencsv" % "opencsv" % "3.8",
    "org.apache.poi" % "poi" % "3.11-beta2",
    "org.apache.poi" % "poi-ooxml" % "3.11-beta2"
)

lazy val socialDependencies = Seq(
    cache,
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "org.twitter4j" % "twitter4j-core" % "4.0.4",
    "org.twitter4j" % "twitter4j-stream" % "4.0.4",
    "com.github.scribejava" % "scribejava-apis" % "2.3.0",
    "com.googlecode.batchfb" % "batchfb" % "2.1.6"
)

lazy val nosqlDependencies = Seq(
    cache,
    ws,
    jdbc,
    "com.typesafe.play" %% "anorm" % "2.5.2",
    "com.typesafe.play" %% "anorm-iteratee" % "2.5.2",
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "mysql" % "mysql-connector-java" % "5.1.38",
    "org.mariadb.jdbc" % "mariadb-java-client" % "1.3.4",
    "com.h2database" % "h2" % "1.3.176",
    "org.postgresql" % "postgresql" % "9.3-1102-jdbc41",
    "org.xerial" % "sqlite-jdbc" % "3.8.7",
    "org.apache.kafka" %% "kafka" % "0.8.2-beta",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.11.14-play23",
    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.4",
    "org.elasticsearch" % "elasticsearch" % "1.4.4",
    "org.apache.hadoop" % "hadoop-client" % "2.6.0",
    "com.jolbox" % "bonecp" % "0.8.0.RELEASE"
)

lazy val mlDependencies = Seq(
    cache,
    "org.apache.commons" % "commons-math3" % "3.5",
    "com.thoughtworks.xstream" % "xstream" % "1.4.8",
    "com.github.haifengl" % "smile-core" % "1.0.4",
    "org.scalanlp" %% "breeze" % "0.10",
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val dlDependencies = Seq(
    cache,
    "org.scalanlp" %% "breeze" % "0.10",
    "org.deeplearning4j" % "deeplearning4j-core" % "0.6.0" excludeAll(ExclusionRule(organization = "org.spark-project"),ExclusionRule(organization = "org.spark-project.akka"), ExclusionRule(organization = "io.netty"), ExclusionRule(organization = "com.typesafe.akka"), ExclusionRule(artifact = "akka-remote")),
    "org.deeplearning4j" % "deeplearning4j-nlp" % "0.6.0" excludeAll(ExclusionRule(organization = "org.spark-project"),ExclusionRule(organization = "org.spark-project.akka"), ExclusionRule(organization = "io.netty"), ExclusionRule(organization = "com.typesafe.akka"), ExclusionRule(artifact = "akka-remote"), ExclusionRule("org.deeplearning4j","spark")),
    "org.nd4j" % "nd4j-native-platform" % "0.6.0",
    "org.nd4j" % "nd4j-native" % "0.6.0",
    "org.nd4j" % "nd4j-native" % "0.6.0" classifier "windows-x86_64",
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val dlibDependencies = Seq(
    ws,
    cache,
    "commons-io" % "commons-io" % "2.4",
    "org.json" % "json" % "20151123",
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val webDependencies = Seq(
    ws,
    cache,
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val crawlerDependencies = Seq(
    ws,
    cache,
    "net.sourceforge.htmlunit" % "htmlunit" % "2.18"
)

lazy val vizDependencies = Seq(
    ws,
    cache,
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val coreDependencies = Seq(
    filters,
    jdbc,
    cache,
    ws,
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "org.codehaus.groovy" % "groovy-all" % "2.2.1",
    "com.typesafe.akka" %% "akka-remote" % "2.3.6",
    "com.typesafe.akka" %% "akka-testkit" % "2.3.6",
    "com.github.nscala-time" %% "nscala-time" % "1.8.0",
    "joda-time" % "joda-time" % "2.7",
    "org.apache.commons" % "commons-math3" % "3.5",
    "org.reflections" % "reflections" % "0.9.10",
    "com.github.lucarosellini.rJava" % "JRIEngine" % "0.9-7",
    "com.github.lucarosellini.rJava" % "JRI" % "0.9-7"
)

lazy val tuktuDBDependencies = Seq(
    cache,
    "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

lazy val dfsDependencies = Seq(
    cache,
    "org.scalatestplus" %% "play" % "1.2.0" % "test",
    "com.typesafe.akka" %% "akka-remote" % "2.3.6"
)

lazy val api = (project in file("modules/api"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-api")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= apiDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    
lazy val aws = (project in file("modules/aws"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-aws")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= awsDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
 
lazy val modeller = (project in file("modules/modeller"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-Modeller")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= modellerDependencies)
    .settings(includeFilter in (Assets, LessKeys.less) := "main_modeller.less")
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api) 
    
lazy val csv = (project in file("modules/csv"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-csv")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= csvDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val social = (project in file("modules/social"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-social")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= socialDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val nosql = (project in file("modules/nosql"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-nosql")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= nosqlDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val ml = (project in file("modules/ml"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-ml")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= mlDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val dl = (project in file("modules/deeplearn"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-DeepLearn")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= dlDependencies)
    .settings(dependencyOverrides += "io.netty" % "netty" % "3.9.3.Final")
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api, ml)
    .dependsOn(api, ml)
    
lazy val nlp = (project in file("modules/nlp"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-nlp")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= nlpDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api, ml, dl)
    .dependsOn(api, ml, dl)

lazy val dlib = (project in file("modules/dlib"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-dlib")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= dlibDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val web = (project in file("modules/web"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-web")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= webDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val crawler = (project in file("modules/crawler"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-crawler")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= crawlerDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val viz = (project in file("modules/viz"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-viz")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= vizDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val tuktudb = (project in file("modules/tuktudb"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-DB")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= tuktuDBDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val dfs = (project in file("modules/dfs"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-DFS")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= dfsDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api)
    .dependsOn(api)
    
lazy val restapi = (project in file("modules/restapi"))
    .enablePlugins(PlayScala)
    .settings(name := "Tuktu-RESTAPI")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= restApiDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api, dfs)
    .dependsOn(api, dfs)

lazy val root = project
    .in(file("."))
    .enablePlugins(PlayScala, LauncherJarPlugin)
    .settings(name := "Tuktu")
    .settings(version := "1.3")
    .settings(scalaVersion := "2.11.8")
    .settings(resolvers ++= appResolvers)
    .settings(libraryDependencies ++= coreDependencies)
    .settings(EclipseKeys.skipParents in ThisBuild := false)
    .aggregate(api, aws, restapi, nlp, csv, dfs, dl, social, nosql, ml, web, tuktudb, crawler, modeller, viz, dlib)
    .dependsOn(api, aws, restapi, nlp, csv, dfs, dl, social, nosql, ml, web, tuktudb, crawler, modeller, viz, dlib)

scalaVersion := "2.11.8"
