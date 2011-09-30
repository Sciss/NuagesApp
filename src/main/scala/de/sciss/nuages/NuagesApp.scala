/*
 *  NuagesApp.scala
 *  (NuagesApp)
 *
 *  Copyright (c) 2010-2011 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.nuages

import de.sciss.synth._
import proc.{RichBus, ProcTxn, ProcDemiurg}
import swing.j.JServerStatusPanel

import java.util.Properties
import java.io.{FileOutputStream, FileInputStream, File}
import java.awt.{EventQueue, GraphicsEnvironment}
import javax.swing.Box
import collection.breakOut
import collection.immutable.{IndexedSeq => IIdxSeq}

object NuagesApp extends Runnable {
   def main( args: Array[ String ]) {
      EventQueue.invokeLater( this )
   }

   val fs                  = File.separator
   val NUAGES_ANTIALIAS    = false
   var masterBus : AudioBus = null

   private val PROP_BASEPATH           = "basepath"
   private val PROP_INDEVICE           = "indevice"
   private val PROP_OUTDEVICE          = "outdevice"
   private val PROP_MASTERNUMCHANS     = "masternumchans"
   private val PROP_MASTEROFFSET       = "masteroffset"
   private val PROP_MASTERCHANGROUPS   = "masterchangroups"
   private val PROP_SOLOOFFSET         = "solooffset"
   private val PROP_SOLONUMCHANS       = "solonumchans"
   private val PROP_RECCHANGROUPS      = "recchangroups"
   private val PROP_PEOPLECHANGROUPS   = "peoplechangroups"
   private val PROP_MICCHANGROUPS      = "micchangroups"
   private val PROP_COLLECTOR          = "collector"

   val properties          = {
      val file = new File( "nuages-settings.xml" )
      val prop = new Properties()
      if( file.isFile ) {
         val is = new FileInputStream( file )
         prop.loadFromXML( is )
         is.close()
      } else {
         prop.setProperty( PROP_BASEPATH,
            new File( new File( System.getProperty( "user.home" ), "Desktop" ), "Nuages" ).getAbsolutePath )
         prop.setProperty( PROP_INDEVICE, "" )
         prop.setProperty( PROP_OUTDEVICE, "" )
         prop.setProperty( PROP_MASTERNUMCHANS, 2.toString )
         prop.setProperty( PROP_MASTEROFFSET, 0.toString )
         prop.setProperty( PROP_MASTERCHANGROUPS, "" )
         prop.setProperty( PROP_MICCHANGROUPS, "" )
         prop.setProperty( PROP_SOLOOFFSET, (-1).toString )
         prop.setProperty( PROP_SOLONUMCHANS, 2.toString )
         prop.setProperty( PROP_RECCHANGROUPS, "" )
         prop.setProperty( PROP_PEOPLECHANGROUPS, "" )
         val os = new FileOutputStream( file )
         prop.storeToXML( os, "Nuages Settings" )
         os.close()
      }
      prop
   }

   def decodeGroup( prop: String ) : IIdxSeq[ NamedBusConfig ] = {
      val s = properties.getProperty( prop, "" )
      val r = """\x28(\w+),(\d+),(\d+)\x29""".r
      try {
         val l = r.findAllIn( s ).toList
         l.map( s0 => {
            val Array( name, offS, chansS ) = s0.substring( 1, s0.length - 1 ).split( ',' )
            NamedBusConfig( name, offS.toInt, chansS.toInt )
         })( breakOut )
      } catch { case e =>
         println( "Error matching value '" + s + "' for prop '" + prop + "' : " )
         e.printStackTrace()
         IIdxSeq.empty
      }
   }

   val BASE_PATH           = properties.getProperty( PROP_BASEPATH )
   val TAPES_PATH          = BASE_PATH + fs + "tapes"
   val REC_PATH            = BASE_PATH + fs + "rec"
   val MASTER_NUMCHANNELS  = properties.getProperty( PROP_MASTERNUMCHANS, 2.toString ).toInt
   val MASTER_OFFSET       = properties.getProperty( PROP_MASTEROFFSET, 0.toString ).toInt
   val MASTER_CHANGROUPS   = decodeGroup( PROP_MASTERCHANGROUPS )
   val SOLO_OFFSET         = properties.getProperty( PROP_SOLOOFFSET, (-1).toString ).toInt
   val SOLO_NUMCHANNELS    = properties.getProperty( PROP_SOLONUMCHANS, 2.toString ).toInt
   val REC_CHANGROUPS      = decodeGroup( PROP_RECCHANGROUPS )
   val MIC_CHANGROUPS      = decodeGroup( PROP_MICCHANGROUPS )
   val PEOPLE_CHANGROUPS   = decodeGroup( PROP_PEOPLECHANGROUPS )
   val USE_COLLECTOR       = properties.getProperty( PROP_COLLECTOR, false.toString ).toBoolean

   val USE_TABLET          = true
   val DEBUG_PROXIMITY     = false
   val LOOP_DUR            = 30
   
   val options          = {
      val o = new ServerOptionsBuilder()
      val inDevice   = properties.getProperty( PROP_INDEVICE, "" )
      val outDevice  = properties.getProperty( PROP_OUTDEVICE, "" )
      if( inDevice == outDevice ) {
         if( inDevice != "" ) o.deviceName = Some( inDevice )
      } else {
         o.deviceNames = Some( inDevice -> outDevice )
      }
      val maxInIdx = (MIC_CHANGROUPS ++ PEOPLE_CHANGROUPS).map( _.stopOffset ).max

      val maxOutIdx = ((MASTER_OFFSET + MASTER_NUMCHANNELS) +: (if( SOLO_OFFSET >= 0 ) SOLO_OFFSET + SOLO_NUMCHANNELS else 0) +:
         REC_CHANGROUPS.map( _.stopOffset )).max

//      println( "MAX IN " + maxInIdx + " ; MAX OUT " + maxOutIdx )

      o.inputBusChannels   = maxInIdx
      o.outputBusChannels  = maxOutIdx
      o.audioBusChannels   = 512
      o.loadSynthDefs      = false
      o.memorySize         = 65536
      o.zeroConf           = false
      o.build
   }

   lazy val SCREEN_BOUNDS =
      GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration.getBounds

   @volatile var s: Server       = _
   @volatile var booting: ServerConnection = _
   @volatile var config: NuagesConfig = _

//   val support = new REPLSupport

   def run() {
      // prevent actor starvation!!!
      // --> http://scala-programming-language.1934581.n4.nabble.com/Scala-Actors-Starvation-td2281657.html
      System.setProperty( "actors.enableForkJoin", "false" )

      val ssp  = new JServerStatusPanel( JServerStatusPanel.COUNTS )
      val maxX = SCREEN_BOUNDS.x + SCREEN_BOUNDS.width - 48
      val maxY = SCREEN_BOUNDS.y + SCREEN_BOUNDS.height - 35 /* sspw.getHeight() + 3 */

      booting = Server.boot( options = options ) {
         case ServerConnection.Preparing( srv ) => {
            ssp.server = Some( srv )
//            ntp.server = Some( srv )
         }
         case ServerConnection.Running( srv ) => {
            ProcDemiurg.addServer( srv )
            s = srv
//            support.s = srv

            // nuages
            val f       = initNuages( maxX, maxY )
            initFScape( s, f )

            val recS    = NuagesRecorder.SettingsBuilder()
            recS.folder = new File( REC_PATH )
            require( if( recS.folder.isDirectory ) recS.folder.canWrite else recS.folder.mkdirs(),
               "Can't access live recording folder: " + REC_PATH )
            recS.bus    = /* RichBus.wrap( */ masterBus /* ) */
            val rec     = NuagesRecorder( recS )

            val ctrlS = ControlPanel.SettingsBuilder()
            ctrlS.numOutputChannels = MASTER_NUMCHANNELS
            ctrlS.numInputChannels  = PEOPLE_CHANGROUPS.size
            ctrlS.clockAction = (on, fun) => ProcTxn.spawnAtomic { implicit tx =>
               val succ = if( on ) rec.start else rec.stop
               if( succ ) tx.afterCommit( _ => fun() )
            }
            val ctrl = ControlPanel( ctrlS )
            val ctrlB = f.bottom
            ctrlB.add( ssp )
            ctrlB.add( Box.createHorizontalStrut( 8 ))
            ctrlB.add( ctrl )
            ctrlB.add( Box.createHorizontalStrut( 4 ))

            val procsS              = NuagesProcs.SettingsBuilder()
            procsS.server           = s
            procsS.frame            = f
            procsS.audioFilesFolder = Some( new File( BASE_PATH, "sciss" ))
            procsS.controlPanel     = Some( ctrl )
            procsS.lineInputs       = PEOPLE_CHANGROUPS
            procsS.micInputs        = MIC_CHANGROUPS
            procsS.lineOutputs      = REC_CHANGROUPS
            procsS.masterGroups     = MASTER_CHANGROUPS
            val procs               = new NuagesProcs( procsS )
            val tapes = TapesPanel.fromFolder( new File( TAPES_PATH ))
            tapes.installOn( f ) { list => procs.tapePath = list.headOption.map( _.file.getAbsolutePath )}

            ProcTxn.atomic { implicit tx => procs.init }
         }
      }
      Runtime.getRuntime.addShutdownHook( new Thread { override def run() = shutDown() })
   }

   private def initNuages( maxX: Int, maxY: Int ) : NuagesFrame = {
      val masterChans   = (MASTER_OFFSET until (MASTER_OFFSET + MASTER_NUMCHANNELS ))
      val soloBus       = if( /* !INTERNAL_AUDIO && */ (SOLO_OFFSET >= 0) ) {
         Some( (SOLO_OFFSET until (SOLO_OFFSET + SOLO_NUMCHANNELS)) )
      } else {
         None
      }
      config            = NuagesConfig( s, Some( masterChans ), soloBus, Some( REC_PATH ), true, collector = USE_COLLECTOR )
      val f             = new NuagesFrame( config )
      val np            = f.panel
      masterBus         = np.masterBus.get // XXX not so elegant
      val disp          = np.display
      disp.setHighQuality( NUAGES_ANTIALIAS )
      val y0 = SCREEN_BOUNDS.y + 22
      f.setBounds( SCREEN_BOUNDS.x, y0, maxX - SCREEN_BOUNDS.x, maxY - y0 )
      f.setUndecorated( true )
      f.setVisible( true )
//      support.nuages = f

      if( USE_TABLET ) NuagesTablet.init( f )

      f
   }

   private def initFScape( server: Server, frame: NuagesFrame ) {
      NuagesFScape.init( server, frame )
      NuagesFScape.fsc.connect()( succ => println( if( succ ) "FScape connected." else "!ERROR! : FScape not connected" ))
   }

   private def shutDown() { // sync.synchronized { }
      if( (s != null) && (s.condition != Server.Offline) ) {
         s.quit
         s = null
      }
      if( booting != null ) {
         booting.abort
         booting = null
      }
   }
}