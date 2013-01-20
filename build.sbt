import AssemblyKeys._ // put this at the top of the file

name           := "NuagesApp"

version        := "0.35.0"

organization   := "de.sciss"

homepage       := Some( url( "https://github.com/Sciss/NuagesApp" ))

description    := "Application for improvised electronic music"

licenses       := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion   := "2.10.0"

libraryDependencies ++= Seq(
   "de.sciss" %% "nuagespompe" % "0.35.+",
   "de.sciss" %% "fscapejobs" % "1.2.+"
)

retrieveManaged := true

scalacOptions += "-deprecation"

// ---- packaging ----

seq( assemblySettings: _* )

test in assembly := {}

seq( appbundle.settings: _* )

appbundle.target <<= baseDirectory

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/NuagesApp.git</url>
  <connection>scm:git:git@github.com:Sciss/NuagesApp.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- ls.implicit.ly ----

seq( lsSettings :_* )

(LsKeys.tags in LsKeys.lsync) := Seq( "sound-synthesis", "gui", "sound", "music", "supercollider" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "NuagesApp" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))
