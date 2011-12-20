import AssemblyKeys._ // put this at the top of the file

name           := "nuagesapp"

appbundleName  := "NuagesApp"

version        := "0.30"

organization   := "de.sciss"

scalaVersion   := "2.9.1"

resolvers += "Clojars Repository" at "http://clojars.org/repo"

libraryDependencies ++= Seq(
   "de.sciss" %% "nuagespompe" % "0.10",
   "de.sciss" %% "fscapejobs" % "0.16"
)

retrieveManaged := true

scalacOptions += "-deprecation"

seq( assemblySettings: _* )

test in assembly := {}

// jarName in assembly := name + "-full.jar"

seq( appbundleSettings: _* )
