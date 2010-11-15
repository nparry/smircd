import sbt._

class SmircdProject(info: ProjectInfo) extends DefaultProject(info) {
  val jbossRepo = "JBoss Release Repository" at "http://repository.jboss.org/nexus/content/groups/public/"

  val netty = "org.jboss.netty" % "netty" % "3.2.3.Final"

  val slf4j = "org.slf4j" % "slf4j-api" % "1.6.1"
  val logback = "ch.qos.logback" % "logback-classic" % "0.9.26"
  val grizzled = "org.clapper" %% "grizzled-slf4j" % "0.3.2"

  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"
}

