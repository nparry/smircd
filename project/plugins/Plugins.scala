import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val codaRepo = "Coda Hale's Repository" at "http://repo.codahale.com/"

  val mavenSBT = "com.codahale" % "maven-sbt" % "0.1.1"
  val proguard = "org.scala-tools.sbt" % "sbt-proguard-plugin" % "0.0.5.123"

}

