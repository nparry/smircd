import sbt._

class SmircdProject(info: ProjectInfo) extends DefaultProject(info) with ProguardProject {

  val jbossRepo = "JBoss Release Repository" at "http://repository.jboss.org/nexus/content/groups/public/"

  val netty = "org.jboss.netty" % "netty" % "3.2.3.Final"

  val slf4j = "org.slf4j" % "slf4j-api" % "1.6.1"
  val logback = "ch.qos.logback" % "logback-classic" % "0.9.26"
  val grizzled = "org.clapper" %% "grizzled-slf4j" % "0.3.2"

  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test"

  override def managedStyle = ManagedStyle.Maven
  private lazy val repoInfo =
    if (version.toString.endsWith("-SNAPSHOT"))
      ( "nparry snapshots" -> "/home/nparry/repository.nparry.com/snapshots" )
    else
      ( "nparry releases" -> "/home/nparry/repository.nparry.com/releases" )

  lazy val publishTo = Resolver.ssh(
    repoInfo._1,
    "repository.nparry.com",
    repoInfo._2) as(
    System.getProperty("user.name"),
    (Path.userHome / ".ssh" / "id_rsa").asFile)

  override def packageDocsJar = defaultJarPath("-javadoc.jar")
  override def packageSrcJar = defaultJarPath("-sources.jar")

  val sourceArtifact = Artifact.sources(artifactID)
  val docsArtifact = Artifact.javadoc(artifactID)
  val proguardArtifact = Artifact(artifactID, "min")

  override def packageToPublishActions = super.packageToPublishActions ++ Seq(
    proguard,
    packageDocs,
    packageSrc)

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>

  override val mainClass = Some("com.nparry.smircd.Mainline")

  // Until https://github.com/nuttycom/sbt-proguard-plugin/pull/2 is fixed
  val macJdkBase = new java.io.File(System.getProperty("java.home")).getParent()
  def macJdkClassesPath = Path.fromFile(macJdkBase) / "Classes"/ "classes.jar"
  override def proguardLibraryJars = super.proguardLibraryJars +++ (macJdkClassesPath :PathFinder)

  override def minJarName = artifactBaseName + "-min.jar"
  override def proguardInJars = super.proguardInJars +++ scalaLibraryPath
  override def proguardOptions = List(
    proguardKeepMain("com.nparry.smircd.Mainline"),
    "-keep class com.nparry.smircd.**",
    "-dontobfuscate",
    "-dontoptimize",
    "-forceprocessing")
}

