## README

An application which glues together Wolkenpumpe for live improvisation. (C)opyright 2010&ndash;2012 by Hanns Holger Rutz. All rights reserved. Covered by the GNU General Public License v2+ (see licenses folder).

Due to the inclusion of JNITablet, this currently only works on OS X. Remove the tablet initialization on other platforms.

### requirements

Builds with xsbt (sbt 0.11) against Scala 2.9.2. Depends on [NuagesPompe](http://github.com/Sciss/NuagesPompe) and [FScapeJobs](http://github.com/Sciss/FScapeJobs). Standard sbt targets are `clean`, `update`, `compile`, `package`, `doc`, `publish-local`, as well as bundling the OS X application with `appbundle`.

### running

On OS X you'll have a double-clickable app package (created through `sbt appbundle`), otherwise a double-clickable jar (created through `sbt assembly`).

You'll need to edit the `NuagesProc` file to add your own sound modules. The REPL is initialised with the contents of file `"interpreter.txt"` if found in the cwd.

### creating an IntelliJ IDEA project

To develop the sources, if you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"
    
    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

Then to create the IDEA project, run the following two commands from the xsbt shell:

    > set ideaProjectName := "NuagesApp"
    > gen-idea
