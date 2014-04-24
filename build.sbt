
name := "parent"

Common.settings

lazy val root = project.in(file(".")).settings(publishArtifact := false)
  .aggregate(akkaFastCgi, playFastCgi, phpFpmSbtPlugin, moxie)

lazy val akkaFastCgi = project.in(file("akka-fastcgi"))

lazy val playFastCgi = project.in(file("play-fastcgi")).dependsOn(akkaFastCgi)

lazy val phpFpmSbtPlugin = project.in(file("php-fpm-sbt-plugin"))

lazy val moxie = project.in(file("moxie")).settings(publishArtifact := false).dependsOn(akkaFastCgi)
