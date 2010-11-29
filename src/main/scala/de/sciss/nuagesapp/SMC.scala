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

import de.sciss.synth.swing.{NodeTreePanel, ServerStatusPanel}
import de.sciss.synth.proc.ProcDemiurg
import collection.breakOut
import collection.immutable.{ IndexedSeq => IIdxSeq }
import de.sciss.synth.io.AudioFile
import de.sciss.nuages.{NuagesConfig, NuagesFrame}
import de.sciss.synth._
import de.sciss.freesound.swing.{SearchProgressFrame, SearchResultFrame, SearchQueryFrame, LoginFrame}
import de.sciss.freesound.{Search, Login, Sample, SampleInfoCache}
import actors.DaemonActor
import osc.OSCResponder
import de.sciss.osc.OSCMessage
import de.sciss.scalainterpreter.LogPane
import java.awt.{Font, EventQueue, GraphicsEnvironment}
import java.io.{PrintStream, FilenameFilter, File, RandomAccessFile}
import javax.swing.{JScrollPane, JComponent, JFrame, Box, WindowConstants}

/**
 *    @version 0.12, 02-Oct-10
 */
object SMC extends Runnable {
   val fs                  = File.separator
   val BASE_PATH           = System.getProperty( "user.home" ) + fs + "Desktop" + fs + "CafeConcrete"
   val TAPES_PATH          = BASE_PATH + fs + "tapes"
   val AUTO_LOGIN          = true
   val NUAGES_ANTIALIAS    = false
   val INTERNAL_AUDIO      = false
   val MASTER_NUMCHANNELS  = 6
   val MASTER_CHANGROUPS   = ("M", 0, 2) :: ("J", 2, 4) :: Nil // List[ Tuple3[ String, Int, Int ]] : suffix, offset, numChannels
   val MASTER_OFFSET       = 0
   val MIC_OFFSET          = 0
   val SOLO_OFFSET         = 10      // -1 for solo-off! 10 = MOTU 828 S/PDIF
   val SOLO_NUMCHANNELS    = 2
   val LUDGER_OFFSET       = 14     //  14 = MOTU 828 ADAT begin
   val LUDGER_NUMCHANNELS  = 2
   val REC_COPY            = Some( 14 )   // 14 = MOTU 828 ADAT begin 
   val ROBERT_OFFSET       = 4
   val METERS              = true
   val FREESOUND           = false
   val FREESOUND_OFFLINE   = true
//   var masterBus : AudioBus = null

   val USE_TABLET          = true
   val DEBUG_PROXIMITY     = false
   val NUM_LOOPS           = 7
   val LOOP_DUR            = 30
   
   val options          = {
      val o = new ServerOptionsBuilder()
      if( INTERNAL_AUDIO ) {
         o.deviceNames        = Some( "Built-in Microphone" -> "Built-in Output" )
      } else {
         o.deviceName         = Some( "MOTU 828mk2" )
      }
      o.inputBusChannels   = 22 // 10
      o.outputBusChannels  = 22 // 10
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

   val support = new REPLSupport

   val logPane = {
      val res = new LogPane( 2, 30 )
      res.init
      val scroll = res.getComponent( 0 ).asInstanceOf[ JScrollPane ]
      scroll.setBorder( null )
      scroll.getViewport().getView().setFont( new Font( "Menlo", Font.PLAIN, 8 ))
      val printStream = new PrintStream( res.outputStream )
      System.setErr( printStream )
      System.setOut( printStream )
//      ggLog.writer.write( "Make noise.\n" )
      Console.setErr( res.outputStream )
      Console.setOut( res.outputStream )
      res
   }
   
   def run {
      // prevent actor starvation!!!
      // --> http://scala-programming-language.1934581.n4.nabble.com/Scala-Actors-Starvation-td2281657.html
      System.setProperty( "actors.enableForkJoin", "false" )

//      val sif  = new ScalaInterpreterFrame( support /* ntp */ )
      val ssp  = new ServerStatusPanel( ServerStatusPanel.COUNTS )
//      val sspw = ssp.makeWindow( undecorated = true )
//      sspw.pack()

      val maxX = SCREEN_BOUNDS.x + SCREEN_BOUNDS.width - 48
      val maxY = SCREEN_BOUNDS.y + SCREEN_BOUNDS.height - 35 /* sspw.getHeight() + 3 */
//      sspw.setLocation( SCREEN_BOUNDS.x - 3, maxY - 1 )

//      val ntp  = new NodeTreePanel()
//      val ntpw = ntp.makeWindow
//      ntpw.setLocation( sspw.getX, sspw.getY + sspw.getHeight + 32 )
//      sspw.setVisible( true )
//      ntpw.setVisible( true )

//      sif.setLocation( sspw.getX + sspw.getWidth + 32, sif.getY )
//      sif.setVisible( true )
      booting = Server.boot( options = options ) {
         case ServerConnection.Preparing( srv ) => {
            ssp.server = Some( srv )
//            ntp.server = Some( srv )
         }
         case ServerConnection.Running( srv ) => {
            ProcDemiurg.addServer( srv )
            s = srv
            support.s = srv

            // nuages
            initNuages( maxX, maxY )

            // freesound
            val filesFrame = if( FREESOUND ) {
               val cred  = new RandomAccessFile( BASE_PATH + fs + "cred.txt", "r" )
               val credL = cred.readLine().split( ":" )
               cred.close()
               initFreesound( credL( 0 ), credL( 1 ))
            } else {
               newTapesFrame
            }
            val ctrlP = new ControlPanel( filesFrame )
//      val cf = ctrlP.makeWindow
            val ctrlF = new JFrame()
            ctrlF.setUndecorated( true )
            val ctrlB = Box.createHorizontalBox()
            ctrlB.add( ssp )
            ctrlB.add( Box.createHorizontalStrut( 8 ))
            ctrlB.add( ctrlP )
            ctrlB.add( Box.createHorizontalStrut( 4 ))
            ctrlF.setContentPane( ctrlB )
            ctrlF.pack()
            ctrlF.setBounds( SCREEN_BOUNDS.x - 1, SCREEN_BOUNDS.y + SCREEN_BOUNDS.height - ctrlF.getHeight() + 2,
               maxX - SCREEN_BOUNDS.x + 1, ctrlF.getHeight() )
            ctrlF.setVisible( true )

            val synPostMID = SMCNuages.synPostM.id
            OSCResponder.add({
               case OSCMessage( "/meters", `synPostMID`, 0, values @ _* ) =>
                  EventQueue.invokeLater( new Runnable { def run = ctrlP.meterUpdate( values.map( _.asInstanceOf[ Float ]).toArray )})
            }, s )

         }
      }
      Runtime.getRuntime().addShutdownHook( new Thread { override def run = shutDown })
//      booting.start
   }

   private def initNuages( maxX: Int, maxY: Int ) {
      val masterChans  = if( INTERNAL_AUDIO ) {
//         new AudioBus( s, 0, 2 )
         (0 until 2)
      } else {
//         new AudioBus( s, MASTER_OFFSET, MASTER_NUMCHANNELS )
         (MASTER_OFFSET until (MASTER_OFFSET + MASTER_NUMCHANNELS ))
      }
//      val soloBus    = Bus.audio( s, 2 )
//      val soloBus    = if( SOLO_OFFSET >= 0 ) Some( new AudioBus( s, SOLO_OFFSET, SOLO_NUMCHANNELS )) else None
      val soloBus    = if( SOLO_OFFSET >= 0 ) Some( (SOLO_OFFSET until (SOLO_OFFSET + SOLO_NUMCHANNELS)) ) else None
      val recordPath = BASE_PATH + fs + "rec"
      config         = NuagesConfig( s, Some( masterChans ), soloBus, Some( recordPath ), true )
      val f          = new NuagesFrame( config )
      f.panel.display.setHighQuality( NUAGES_ANTIALIAS )
      val y0 = SCREEN_BOUNDS.y + 22
      f.setBounds( SCREEN_BOUNDS.x, y0, maxX - SCREEN_BOUNDS.x, maxY - y0 )
      f.setUndecorated( true )
//      f.setAlwaysOnTop( true )
      f.setVisible( true )
      support.nuages = f
      SMCNuages.init( s, f )
   }

   private def newTapesFrame : JFrame = {
      val srf = TapesFrame.fromFolder( new File( TAPES_PATH ))
//      srf.setLocationRelativeTo( null )
      srf.setDefaultCloseOperation( WindowConstants.HIDE_ON_CLOSE )
      srf.setAlwaysOnTop( true )
      srf.setLocation( SCREEN_BOUNDS.x + SCREEN_BOUNDS.width - (srf.getWidth() + 64),
         SCREEN_BOUNDS.y + ((SCREEN_BOUNDS.height - srf.getHeight) >> 1) )
//      srf.setVisible( true )
      var checked = Set.empty[ String ]
      srf.addListener {
         case TapesFrame.SelectionChanged( sel @ _* ) => {
            val pathO = sel.headOption.map( _.file.getAbsolutePath() ) 
//println( "FS PATH = " + pathO )
            SMCNuages.freesoundFile = pathO
         }
      }
      srf
   }

   private def newFreesoundResultsFrame( title: String, samples: IIdxSeq[ Sample ], login: Option[ Login ],
                                          icache: Option[ SampleInfoCache ], downloadPath: Option[ String ]) : JFrame = {
      val srf = new SearchResultFrame( samples, login, title, icache, downloadPath )
      srf.setLocationRelativeTo( null )
      srf.setVisible( true )
      var checked = Set.empty[ String ]
      srf.addListener {
         case SearchResultFrame.SelectionChanged( sel @ _* ) => {
//println( "SELECTION = " + sel )
            val pathO = sel.headOption.flatMap( _.download.flatMap( path => {
//println( "AQUI " + path )
               if( checked.contains( path )) Some( path ) else {
                  try {
                     val spec = AudioFile.readSpec( path )
                     if( spec.numChannels > 0 && spec.numChannels <= 2 ) {
                        checked += path
                        Some( path )
                     } else None
                  } catch { case e => None }
               }
            }))
println( "FS PATH = " + pathO )
            SMCNuages.freesoundFile = pathO
         }
      }

      srf
   }

   private def initFreesound( username: String, password: String ) : JFrame = {
      val icachePath = BASE_PATH + fs + "infos"
      val icache = Some( SampleInfoCache.persistent( icachePath ))
      val downloadPath = Some( BASE_PATH + fs + "samples" )
      val f = new LoginFrame()

      f.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      f.setLocation( 0, SCREEN_BOUNDS.height - f.getHeight )
      f.setVisible( true )
      f.username  = username
      f.password_=( password )
      f.addListener {
         case LoginFrame.LoggedIn( login ) => {
            support.login = login
            val sqf = new SearchQueryFrame( f, login )
            sqf.setLocationRelativeTo( null )
//            sqf.setLocation( sqf.getX(), 40 )
            sqf.setLocation( f.getX + f.getWidth, SCREEN_BOUNDS.height - sqf.getHeight )
            sqf.setVisible( true )
            sqf.addListener {
               case SearchQueryFrame.NewSearch( idx, search ) => {
                  val title = "Freesound Search #" + idx + " (" + {
                        val kw = search.options.keyword
                        if( kw.size < 24 ) kw else kw.take( 23 ) + "…"
                     } + ")"
                  val spf = new SearchProgressFrame( sqf, search, title )
                  spf.setLocationRelativeTo( null )
                  spf.setVisible( true )
                  spf.addListener {
                     case Search.SearchDone( samples ) => {
                        newFreesoundResultsFrame( title, samples, Some( login ), icache, downloadPath )
                     }
                  }
               }
            }
         }
      }
      if( FREESOUND_OFFLINE ) {
         val samples: IIdxSeq[ Sample ] = new File( icachePath ).listFiles().map({ f =>
            val n = f.getName()
            if( n.startsWith( "info" ) && n.endsWith( ".xml" )) {
               val mid = n.substring( 4, n.length() - 4 )
               try {
                  Some( mid.toLong )
               }
               catch { case e => None }
            } else None
         }).collect({ case Some( id ) => Sample( id )})( breakOut )
         newFreesoundResultsFrame( "Freesound Cache", samples, None, icache, downloadPath )
      }

      if( AUTO_LOGIN ) f.performLogin

      f
   }

   private def shutDown { // sync.synchronized { }
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