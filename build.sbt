import AssemblyKeys._

organization := "com.nparry"

name := "smircd"

version := "1.0-SNAPSHOT"

description := "Scalarific minimal IRC daemon: A basic low-feature IRC daemon written in Scala"

homepage := Some(url("https://github.com/nparry/smircd"))

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0" )

scmInfo := Some(ScmInfo(url("https://github.com/nparry/smircd.git"),
  "git@github.com:nparry/smircd.git"))

pomExtra := (
  <developers>
    <developer>
      <id>nparry</id>
      <name>Nathan Parry</name>
      <url>http://nparry.com</url>
    </developer>
  </developers>
)

libraryDependencies ++= Seq(
  "io.netty" % "netty" % "3.9.4.Final",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.clapper" %% "grizzled-slf4j" % "1.0.2",
  "org.scala-lang" % "scala-actors" % scalaVersion.value,
  "org.specs2" %% "specs2" % "2.4" % "test"
)

scalaVersion := "2.11.2"

publishMavenStyle := true

pomIncludeRepository := { _ => false }

mainClass in Compile := Some("com.nparry.smircd.Mainline")

assemblySettings
