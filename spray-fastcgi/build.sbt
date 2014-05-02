name := "spray-fastcgi"

Common.settings

libraryDependencies ++= Seq(
  "de.leanovate.toehold" %% "akka-fastcgi" % version.value,
  "io.spray" % "spray-can" % "1.3.1",
  "io.spray" % "spray-routing" % "1.3.1",
  "org.specs2" %% "specs2" % "2.3.10" % "test"
)

ScoverageSbtPlugin.instrumentSettings
