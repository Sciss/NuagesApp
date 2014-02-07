# NuagesApp (Wolkenpumpe)

A project which packages the Wolkenpumpe interface for live improvisation as a standalone application. (C)opyright 2010&ndash;2014 by Hanns Holger Rutz. All rights reserved. Covered by the GNU General Public License v2+ (see licenses folder).

The JNITablet currently only works on OS X. Hopefully, the initialization code will be bypassed when launching on other platforms.

## requirements

Builds with sbt 0.13 against Scala 2.10.

## running

On OS X you'll have a double-clickable app package (created through `sbt appbundle`), otherwise a double-clickable jar (created through `sbt assembly`). There is a tiny shell script `./wolkenpumpe` which launches this jar file.

You'll need to edit the `NuagesProc` file to add your own sound modules. The REPL is initialised with the contents of file `"interpreter.txt"` if found in the cwd.

## settings

Upon start, the application reads the configuration file `nuages-settings.xml` in the root directory if found. If not found, the default settings are created and written to this file, so you can edit it and re-run the application. The file format is XML for standard Java properties. The entry key value pairs are as follows:

|**key**           |**value type**         |**description**                          |**default**         |
|------------------|-----------------------|-----------------------------------------|--------------------|
|`basepath`        |path string            |Path to the files used in the session.   |`~/Desktop/Nuages`  |
|`indevice`        |string                 |Name of audio interface to use for input |(defaut sound card) |
|`outdevice`       |string                 |Name of audio interface to use for output|(defaut sound card) |
|`masternumchans`  |integer number         |Number of audio output channels          |`2`                 |
|`masteroffset`    |integer number         |Offset for output channels               |`0`                 |
|`masterchangroups`|space separated tuples `(<name>,<off>,<num>)` |Creates output channel groups with a given name, integer offset and number of channels|none|
|`solonumchans`    |integer number         |Number of channel for soloing            |`2`                 |
|`solooffset`      |integer number         |Offset for solo channels, or `-1` to omit solo function |`-1`|
|`recchangroups`   |space separated tuples `(<name>,<off>,<num>)` |Creates additional output channel groups to be sent to co-players|none|
|`micchangroups`   |space separated tuples `(<name>,<off>,<num>)` |Creates input channel groups listed as microphone inputs|none|
|`peoplechangroups`|space separated tuples `(<name>,<off>,<num>)` |Creates input channel groups for co-player signals|none|
|`collector`       |boolean value          |Whether to use a dedicated master collector node|`false`|

Wolkenpumpe expects or creates the following sub-directories inside the `basepath` directory:

- `tape` contains all the sound files you want to use. The folder will be scanned upon application launch. Tapes can be selected by opening the tape window with `Cmd-T`. When playing a tape process, the currently selected entry in this table will be used.
- `rec` will be used to store recordings made during the session
- `sciss` will be used or temporary buffers for exchange with FScape

## mouse control

The main area of the interface is a zoomable graph of the sound processes, based on Prefuse and an animated force-directed layout.

- double-click on an empty spot brings up the menu for creating a new sound process. Select the generating process in the top part of the menu, and the panorama/spatialization process in the bottom part.
- once processes were created, they can be dragged around by clicking on their component names or the gray boundary area.
- the virtual surface can be panned by clicking and dragging an empty area
- the surface can be auto-zoomed with right-click (?) or cmd-click. mouse wheel to zoom in/out
- each process is made of circle shaped objects. The center object shows the process name. The west sector is the play/stop button, the north sector is the solo button, the east sector shows a volume meter.
- alt-click on a process name to remove/delete it
- around the process, there may be one or several circular control objects. They contain the controller name and a potentiometer which can be adjusted with the mouse
- to insert a filtering process between two processes, double click on the edge that connects an `out` and `in` vertex. A popup with the list of available filters appears

The two sliders on the right hand side control main and solo volume.

The controls on the bottom of the window are, from left to right:

- SuperCollider server status (CPU usage, number of groups, synths, ugens and synthdefs)
- play and stop button to start and stop recording your session to `basepath/rec`.
- a timer indicates that recording is running
- output meters
- input meters
- status text panel
- button to bring up live-coding interpreter (REPL). This allows you to write additional generators and filters on the fly.
- triple button to control fading behaviour for starting and stopping processes or adjusting control parameters: "In" for instantaneous, "Gl" for gliding (e.g. glissando for parameter changes), "X" for cross-fade.
- a slider controlling gliding and cross-fade duration.

On OS X, graphic tablets (e.g. Wacom) are supported. When using a tablet, cross-fading type and cross-fading duration are controlled by certain button combinations and tilt angle (I don't remember, have to look it up).

## keyboard control

- `Cmd-T` brings up tape selection window. To close the window, double click on a tape (sound file) name
- `Cmd-Shift-F` toggles full screen mode

## FScape

Filter processes `>fsc` records a bit of sound from its input and requests FScape to process it offline. For this to work, FScape must be running with OSC enabled upon application launch (use default OSC port and TCP protocol).