import AssemblyKeys._ // put this at the top of the file

name           := "NuagesApp"

version        := "0.34-SNAPSHOT"

organization   := "de.sciss"

scalaVersion   := "2.9.1"

resolvers += "Clojars Repository" at "http://clojars.org/repo"

libraryDependencies ++= Seq(
   "de.sciss" %% "nuagespompe" % "0.34-SNAPSHOT",
   "de.sciss" %% "fscapejobs" % "0.17"
)

retrieveManaged := true

scalacOptions += "-deprecation"

seq( assemblySettings: _* )

test in assembly := {}

seq( appbundle.settings: _* )