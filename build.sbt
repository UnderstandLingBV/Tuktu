name := """Modeller"""

version := "1.0-SNAPSHOT"



lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

javaOptions += "-Xmax-classfile-name 200"

libraryDependencies ++= Seq(
  cache,
  filters,
  "org.webjars" % "jquery" % "1.11.3",
  "org.webjars" % "bootstrap" % "3.3.4",
  "org.webjars" % "raphaeljs" % "2.1.2-1",
  "org.webjars" % "underscorejs" % "1.8.3"
)
