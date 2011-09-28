import AssemblyKeys._ // put this at the top of the file

name := "nuagesapp"

version := "0.30-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
   "de.sciss" %% "wolkenpumpe" % "0.30-SNAPSHOT",
   "de.sciss" %% "fscapejobs" % "0.16"
)

retrieveManaged := true

scalacOptions += "-deprecation"

// ---- proguard ----
// NOTE: the resulting jar is 31 MB, maybe because of scala-test
// that should be removed. It also doesn't launch  --
// cheez, this all worked in sbt 0.7 :-(

// seq(ProguardPlugin.proguardSettings :_*)

// proguardOptions ++= Seq(
//    "-target 1.6",
// // "-dontoptimize",
//    "-dontobfuscate",
//    "-dontshrink",
//    "-dontpreverify",
//    "-forceprocessing"
// )

// val standalone = proguard

seq(assemblySettings: _*)

// jarName in assembly := name + "-full.jar"