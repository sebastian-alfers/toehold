name := "example"

organization := "de.leanovate.toehold"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "de.leanovate.toehold" %% "play-fastcgi" % "0.1.9-SNAPSHOT"
)     

play.Project.playScalaSettings

de.leanovate.toehold.sbt.PhpFpmPlugin.phpFpmSettings