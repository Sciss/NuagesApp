import AssemblyKeys._ // put this at the top of the file

name           := "NuagesApp"

lazy val appName = "Wolkenpumpe"

version        := "1.0.0"

organization   := "de.sciss"

homepage       := Some(url("https://github.com/Sciss/" + name.value))

description    := "Application for improvised electronic music"

licenses       := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

scalaVersion   := "2.10.3"

libraryDependencies ++= Seq(
   "de.sciss" %% "nuagespompe" % "0.35.+",
   "de.sciss" %% "fscapejobs"  % "1.2.+"
)

mainClass       := Some("de.sciss.nuages.NuagesApp")

retrieveManaged := true

scalacOptions += "-deprecation"

// ---- packaging ----

seq(assemblySettings: _*)

test in assembly := ()

seq(appbundle.settings: _*)

appbundle.target   := baseDirectory.value

appbundle.icon      := Some(file("application.icns"))

appbundle.name      := appName

target in assembly  := baseDirectory.value

jarName in assembly := s"$appName.jar"

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (version.value endsWith "-SNAPSHOT")
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
}

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("sound-synthesis", "gui", "sound", "music", "supercollider")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)
