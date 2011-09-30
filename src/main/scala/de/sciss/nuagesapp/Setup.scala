/*
 *  SMC.scala
 *  (NuagesApp)
 *
 *  Copyright (c) 2010 Hanns Holger Rutz. All rights reserved.
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

package de.sciss.nuagesapp

import de.sciss.synth._
import swing.j.JServerStatusPanel
import de.sciss.osc.Message
import proc.ProcDemiurg
import de.sciss.nuages.{TapesPanel, ControlPanel, NuagesConfig, NuagesFrame}

//import de.sciss.freesound.swing.{SearchProgressFrame, SearchResultFrame, SearchQueryFrame, LoginFrame}
//import de.sciss.freesound.{Search, Login, Sample, SampleInfoCache}
import osc.OSCResponder
import java.util.Properties
import java.io.{FileOutputStream, FileInputStream, File}
import java.awt.event.{ActionEvent, KeyEvent}
import java.awt.{Point, Toolkit, EventQueue, GraphicsEnvironment}
import javax.swing.{AbstractAction, KeyStroke, JComponent, Box}
import collection.breakOut

object Setup extends Runnable {
   val fs                  = File.separator
//   val BASE_PATH           = System.getProperty( "user.home" ) + fs + "Desktop" + fs + "CafeConcrete"
   val AUTO_LOGIN          = true
   val NUAGES_ANTIALIAS    = false
//   val MASTER_NUMCHANNELS  = 6
//   val MASTER_CHANGROUPS   = ("M", 0, 2) :: ("J", 2, 4) :: Nil // List[ Tuple3[ String, Int, Int ]] : suffix, offset, numChannels
//   val MASTER_OFFSET       = 0
//   val MIC_OFFSET          = 0
//   val SOLO_OFFSET         = 10      // -1 for solo-off! 10 = MOTU 828 S/PDIF
//   val SOLO_NUMCHANNELS    = 2
//   val LUDGER_OFFSET       = 14     //  14 = MOTU 828 ADAT begin
//   val LUDGER_NUMCHANNELS  = 2
//   val REC_CHANGROUPS      = Some( 14 )   // -1 for none; 14 = MOTU 828 ADAT begin
//   val ROBERT_OFFSET       = 4
   val METERS              = true
   val FREESOUND           = false
   val FREESOUND_OFFLINE   = true
   var masterBus : AudioBus = null

   private val PROP_BASEPATH           = "basepath"
//   private val PROP_INTERNALAUDIO      = "internalaudio"
   private val PROP_INDEVICE           = "indevice"
   private val PROP_OUTDEVICE          = "outdevice"
   private val PROP_MASTERNUMCHANS     = "masternumchans"
   private val PROP_MASTEROFFSET       = "masteroffset"
   private val PROP_MASTERCHANGROUPS   = "masterchangroups"
//   private val PROP_MICOFFSET          = "micoffset"
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
//         prop.setProperty( PROP_INTERNALAUDIO, false.toString )
         prop.setProperty( PROP_INDEVICE, "" )
         prop.setProperty( PROP_OUTDEVICE, "" )
         prop.setProperty( PROP_MASTERNUMCHANS, 2.toString )
         prop.setProperty( PROP_MASTEROFFSET, 0.toString )
         prop.setProperty( PROP_MASTERCHANGROUPS, "" )
//         prop.setProperty( PROP_MICOFFSET, 0.toString )
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

   def decodeGroup( prop: String ) : List[ (String, Int, Int) ] = {
      val s = properties.getProperty( prop, "" )
      val r = """\x28(\w+),(\d+),(\d+)\x29""".r
      try {
         val l = r.findAllIn( s ).toList
         l.map { s0 =>
            val Array( name, offS, chansS ) = s0.substring( 1, s0.length - 1 ).split( ',' )
            (name, offS.toInt, chansS.toInt)
         }
      } catch { case e =>
         println( "Error matching value '" + s + "' for prop '" + prop + "' : " )
         e.printStackTrace()
         Nil
      }
   }

   val BASE_PATH           = properties.getProperty( PROP_BASEPATH )
   val TAPES_PATH          = BASE_PATH + fs + "tapes"
   val REC_PATH            = BASE_PATH + fs + "rec"
//   val INTERNAL_AUDIO      = properties.getProperty( PROP_INTERNALAUDIO, false.toString ).toBoolean
   val MASTER_NUMCHANNELS  = properties.getProperty( PROP_MASTERNUMCHANS, 2.toString ).toInt
   val MASTER_OFFSET       = properties.getProperty( PROP_MASTEROFFSET, 0.toString ).toInt
   val MASTER_CHANGROUPS   = decodeGroup( PROP_MASTERCHANGROUPS )
//   val MIC_OFFSET          = properties.getProperty( PROP_MICOFFSET, 0.toString ).toInt
//   val MIC_NUMCHANNELS     = 2   // XXX should be configurable
   val SOLO_OFFSET         = properties.getProperty( PROP_SOLOOFFSET, (-1).toString ).toInt
   val SOLO_NUMCHANNELS    = properties.getProperty( PROP_SOLONUMCHANS, 2.toString ).toInt
   val REC_CHANGROUPS      = decodeGroup( PROP_RECCHANGROUPS )
   val MIC_CHANGROUPS      = decodeGroup( PROP_MICCHANGROUPS )
   val PEOPLE_CHANGROUPS   = decodeGroup( PROP_PEOPLECHANGROUPS )
   val USE_COLLECTOR       = properties.getProperty( PROP_COLLECTOR, false.toString ).toBoolean

   val USE_TABLET          = true
   val DEBUG_PROXIMITY     = false
   val NUM_LOOPS           = 7
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
      val maxInIdx = (MIC_CHANGROUPS ++ PEOPLE_CHANGROUPS).map( g => g._2 + g._3 ).max

      val maxOutIdx = ((MASTER_OFFSET + MASTER_NUMCHANNELS) :: (if( SOLO_OFFSET >= 0 ) SOLO_OFFSET + SOLO_NUMCHANNELS else 0) ::
         REC_CHANGROUPS.map( g => g._2 + g._3 )).max

      println( "MAX IN " + maxInIdx + " ; MAX OUT " + maxOutIdx )

      o.inputBusChannels   = maxInIdx // 10
      o.outputBusChannels  = maxOutIdx // 10
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
            initNuages( maxX, maxY )

            installTapesPanel
            val ctrlS = ControlPanel.SettingsBuilder()
            ctrlS.numOutputChannels = MASTER_NUMCHANNELS
            ctrlS.numInputChannels  = PEOPLE_CHANGROUPS.size
            val ctrlP = new ControlPanel // ( filesPanel )
            val ctrlB = Nuages.f.bottom
            ctrlB.add( ssp )
            ctrlB.add( Box.createHorizontalStrut( 8 ))
            ctrlB.add( ctrlP )
            ctrlB.add( Box.createHorizontalStrut( 4 ))

            val synPostMID = Nuages.synPostM.id
            OSCResponder.add({
               case Message( "/meters", `synPostMID`, 0, values @ _* ) =>
                  EventQueue.invokeLater( new Runnable { def run() { ctrlP.meterUpdate( values.map( _.asInstanceOf[ Float ])( breakOut ))}})
            }, s )

            FScapeNuages.fsc.connect()( succ => println( if( succ ) "FScape connected." else "!ERROR! : FScape not connected" ))
         }
      }
      Runtime.getRuntime.addShutdownHook( new Thread { override def run() = shutDown() })
//      booting.start
   }

   private def initNuages( maxX: Int, maxY: Int ) {
      val masterChans   = (MASTER_OFFSET until (MASTER_OFFSET + MASTER_NUMCHANNELS ))
      val soloBus       = if( /* !INTERNAL_AUDIO && */ (SOLO_OFFSET >= 0) ) {
         Some( (SOLO_OFFSET until (SOLO_OFFSET + SOLO_NUMCHANNELS)) )
      } else {
         None
      }
//NuagesPanel.verbose = true
      config            = NuagesConfig( s, Some( masterChans ), soloBus, Some( REC_PATH ), true, collector = USE_COLLECTOR )
      val f             = new NuagesFrame( config )
      val np            = f.panel
      masterBus         = np.masterBus.get // XXX not so elegant
      val disp          = np.display
      disp.setHighQuality( NUAGES_ANTIALIAS )
      val y0 = SCREEN_BOUNDS.y + 22
      f.setBounds( SCREEN_BOUNDS.x, y0, maxX - SCREEN_BOUNDS.x, maxY - y0 )
      f.setUndecorated( true )
//      f.setAlwaysOnTop( true )
//      disp.zoom( new Point2D.Float( np.getWidth(), np.getHeight() ), 0.5 ) // don't ask me how these coordinates work
      f.setVisible( true )
//      support.nuages = f
      Nuages.init( s, f )

      FScapeNuages.init( s, f )
   }

   private def installTapesPanel : JComponent = {
      val tapes = TapesPanel.fromFolder( new File( TAPES_PATH ))
//      tapes.setDefaultCloseOperation( WindowConstants.HIDE_ON_CLOSE )
//      tapes.setAlwaysOnTop( true )
//      tapes.setLocation( SCREEN_BOUNDS.x + SCREEN_BOUNDS.width - (tapes.getWidth + 256),
//         SCREEN_BOUNDS.y + ((SCREEN_BOUNDS.height - tapes.getHeight) >> 1) )
      tapes.addListener {
         case TapesPanel.SelectionChanged( sel @ _* ) => {
            val pathO = sel.headOption.map( _.file.getAbsolutePath )
//println( "FS PATH = " + pathO )
            Nuages.freesoundFile = pathO
         }
      }

      val p       = Nuages.f.panel
      val imap    = p.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW )
      val amap    = p.getActionMap
      val tpName  = "tapes"
      imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_T, Toolkit.getDefaultToolkit.getMenuShortcutKeyMask ), tpName )
      amap.put( tpName, new AbstractAction( tpName ) {
         def actionPerformed( e: ActionEvent ) {
            val x = (p.getWidth - tapes.getWidth) >> 1
            val y = (p.getHeight - tapes.getHeight) >> 1
            p.showOverlayPanel( tapes, new Point( x, y ))
         }
      })

      tapes
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