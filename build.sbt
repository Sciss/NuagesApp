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

seq(ProguardPlugin.proguardSettings :_*)

proguardOptions ++= Seq(
   "-target 1.6",
// "-dontoptimize",
   "-dontobfuscate",
   "-dontshrink",
//   "-dontpreverify",
   "-forceprocessing"
)

// val standalone = proguard
