import sbt._

class SmircdProject(info: ProjectInfo) extends ParentProject(info) {

  lazy val server = project("server", "SmIRCd Server", new SmircdServerProject(_))
  lazy val ui = project("ui", "SmIRCd UI", new DefaultWebProject(_))

  class SmircdServerProject(info: ProjectInfo) extends DefaultProject(info) with ProguardProject {
    val jbossRepo = "JBoss Release Repository" at "http://repository.jboss.org/nexus/content/groups/public/"

    val netty = "org.jboss.netty" % "netty" % "3.2.3.Final"

    val slf4j = "org.slf4j" % "slf4j-api" % "1.6.1"
    val logback = "ch.qos.logback" % "logback-classic" % "0.9.26"
    val grizzled = "org.clapper" %% "grizzled-slf4j" % "0.3.2"

    val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"

    override val mainClass = Some("com.nparry.smircd.Mainline")

    // Until https://github.com/nuttycom/sbt-proguard-plugin/pull/2 is fixed
    val macJdkBase = new java.io.File(System.getProperty("java.home")).getParent()
    def macJdkClassesPath = Path.fromFile(macJdkBase) / "Classes"/ "classes.jar"
    override def proguardLibraryJars = super.proguardLibraryJars +++ (macJdkClassesPath :PathFinder)

    override def proguardInJars = super.proguardInJars +++ scalaLibraryPath
    override def proguardOptions = List(
      proguardKeepMain("com.nparry.smircd.Mainline"),
      "-keep class com.nparry.smircd.**",
      "-dontobfuscate",
      "-dontoptimize",
      "-forceprocessing")
  }
}

