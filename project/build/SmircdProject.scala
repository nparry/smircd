import sbt._

class SmircdProject(info: ProjectInfo) extends DefaultProject(info) {
  val jbossRepo = "JBoss Release Repository" at "http://repository.jboss.org/nexus/content/groups/public/"

  val netty = "org.jboss.netty" % "netty" % "3.2.3.Final"
  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"
}

