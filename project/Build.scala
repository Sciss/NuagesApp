//import sbt._
//
//object MyBuild extends Build {
///// ---- package-app ----
//   private val camelCaseName          = "NuagesApp"
//   private def appBundleName          = camelCaseName + ".app"
//   private def appBundleContentsPath  = file( appBundleName ) / "Contents"
//   private def appBundleJavaPath      = appBundleContentsPath / "Resources" / "Java"
//
//   private val jarExt                 = ".jar"
//   private val jarFilter: FileFilter  = "*" + jarExt
//
////   private def allJarsPath = (/*publicClasspath +++*/ buildLibraryJar +++ buildCompilerJar +++ jarPath) ** jarFilter
//   private def allJarsPath      = Seq[java.io.File]() // managedClasspath ** jarFilter  // XXX not found: managedClasspath
//
//   val packageApp = TaskKey[ Unit ]( "package-app", "Copies all jars into the OS X app bundle" )
//
//   val packageAppTask = packageApp := {
//      val jarsPath               = allJarsPath
//      val javaPath               = appBundleJavaPath
//      val cleanPaths             = javaPath * jarFilter
//      val versionedNamePattern   = "(.*?)[-_]\\d.*\\.jar".r // thanks to Don Mackenzie
//      IO.delete( cleanPaths.get )
//      for( fromPath <- jarsPath.get ) {
//         val vName = fromPath.asFile.getName
//         if( !vName.contains( "-javadoc" ) && !vName.contains( "-sources" )) {
//            val plainName     = vName match {
//               case versionedNamePattern( name ) => name + jarExt
//               case n => n
//            }
//            val toPath = javaPath / plainName
////            log.log( Level.Info, "Copying to file " + toPath.asFile )
//            IO.copyFile( fromPath, toPath ) //, log
//         }
//      }
//   }
//}