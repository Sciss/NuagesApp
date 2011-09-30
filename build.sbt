import AssemblyKeys._ // put this at the top of the file

name := "nuagesapp"

version := "0.30-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
   "de.sciss" %% "nuagespompe" % "0.10-SNAPSHOT",
   "de.sciss" %% "fscapejobs" % "0.16"
)

retrieveManaged := true

scalacOptions += "-deprecation"

// ---- proguard ----
// NOTE: the resulting jar is 31 MB, maybe because of scala-test
// that should be removed.

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

test in assembly := {}

// jarName in assembly := name + "-full.jar"
