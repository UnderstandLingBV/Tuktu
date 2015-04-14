EclipseKeys.createSrc := EclipseCreateSrc.All

name := """Modeller"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

javaOptions += "-Xmax-classfile-name 200"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws
)
