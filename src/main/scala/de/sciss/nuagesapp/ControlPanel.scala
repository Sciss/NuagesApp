package de.sciss.nuagesapp

import java.awt.event.{ComponentEvent, ComponentAdapter, WindowAdapter, ActionListener, ActionEvent}
import de.sciss.gui.{PeakMeterPanel, PeakMeter, PeakMeterGroup}
import Setup._
import de.sciss.scalainterpreter.LogPane
import java.io.PrintStream
import javax.swing.{JComponent, JLabel, WindowConstants, SwingConstants, Box, JToggleButton, BoxLayout, JFrame, JButton, JPanel}
import javax.swing.plaf.basic.BasicToggleButtonUI
import de.sciss.nuages.{BasicToggleButton, BasicPanel, BasicButton}
import de.sciss.synth.proc.ProcTxn
import java.awt.{EventQueue, Point, Font, Color, BorderLayout}

class ControlPanel /* ( tapesPanel: JComponent ) */ extends BasicPanel {
   panel =>

   private val masterMeterPanel  = new PeakMeterPanel()
   private val peopleOffset      = masterBus.numChannels << 1
   private val peopleMeterPanel: Option[ PeakMeterPanel ] =
      if( PEOPLE_CHANGROUPS.nonEmpty ) Some( new PeakMeterPanel() ) else None
   
   private var interpreter : Option[ ScalaInterpreterFrame ] = None

   private val ggClock = new Wallclock

   private def space( width: Int = 8 ) {
      panel.add( Box.createHorizontalStrut( width ))
   }

   {
      panel.setLayout( new BoxLayout( panel, BoxLayout.X_AXIS ))

      val ggRecStart = BasicButton( "\u25B6" ) {
         ProcTxn.spawnAtomic { implicit tx =>
            if( Nuages.startRecorder ) tx.afterCommit( _ => defer {
               ggClock.reset()
               ggClock.start()
            })
         }
      }
      ggRecStart.setBackground( Color.black )
      ggRecStart.setForeground( Color.white )
      panel.add( ggRecStart )
      val ggRecStop = BasicButton( "\u25FC" ) {
         ProcTxn.spawnAtomic { implicit tx =>
            Nuages.stopRecorder
            tx.afterCommit( _ => defer {
               ggClock.stop()
            })
         }
      }
      ggRecStop.setBackground( Color.black )
      ggRecStop.setForeground( Color.white )
      panel.add( ggRecStop )
      panel.add( ggClock )
      space()

//      val ggTapes = new JToggleButton( "Tapes" )
//      ggTapes.setUI( new BasicToggleButtonUI )
//      ggTapes.putClientProperty( "JButton.buttonType", "bevel" )
//      ggTapes.putClientProperty( "JComponent.sizeVariant", "small" )
//      ggTapes.setFocusable( false )
//      ggTapes.addActionListener( new ActionListener {
//         def actionPerformed( e: ActionEvent ) {
//            val sel = ggTapes.isSelected
//            tapesPanel.setVisible( sel )
//            if( sel ) tapesPanel.toFront()
//         }
//      })
//      tapesPanel.addComponentListener( new ComponentAdapter {
//         override def componentHidden( e: ComponentEvent ) {
//            ggTapes.setSelected( false )
//         }
//      })

//      val ggTapes = BasicButton( "Tapes" ) {
//         val p = Nuages.f.panel
//         val x = (p.getWidth - tapesPanel.getWidth) >> 1
//         val y = (p.getHeight - tapesPanel.getHeight) >> 1
//         p.showOverlayPanel( tapesPanel, new Point( x, y ))
//      }
//
////      panel.add( Box.createHorizontalStrut( 4 ))
//      panel.add( ggTapes )
//      panel.add( Box.createHorizontalStrut( 4 ))

//      val m1 = new PeakMeter( SwingConstants.HORIZONTAL )
//      val m2 = new PeakMeter( SwingConstants.HORIZONTAL )
//      val mg = new PeakMeterGroup( Array( m1, m2 ))
//      panel.add( m1 )
//      panel.add( m2 )
      val numCh = masterBus.numChannels
      masterMeterPanel.setOrientation( SwingConstants.HORIZONTAL )
      masterMeterPanel.setNumChannels( numCh )
      masterMeterPanel.setBorder( true )
      val d = masterMeterPanel.getPreferredSize
      val dn = 30 / numCh
      d.height = numCh * dn + 7
      masterMeterPanel.setPreferredSize( d )
      masterMeterPanel.setMaximumSize( d )
      panel.add( masterMeterPanel )
      peopleMeterPanel.foreach { p =>
         p.setOrientation( SwingConstants.HORIZONTAL )
         p.setNumChannels( PEOPLE_CHANGROUPS.size )
         p.setBorder( true )
         val d = p.getPreferredSize
         val dn = 30 / numCh
         d.height = numCh * dn + 7
         p.setPreferredSize( d )
         p.setMaximumSize( d )
         panel.add( p )
      }

      val d1 = logPane.getPreferredSize
      d1.height = d.height
      logPane.setPreferredSize( d1 )
      space()
      panel.add( logPane )
//      space( 16 )

      val glue = Box.createHorizontalGlue()
glue.setBackground( Color.black )
//      glue.setBackground( Color.darkGray )
      panel.add( glue )

      lazy val ggInterp: JToggleButton = BasicToggleButton( "REPL" ) { sel =>
         if( sel ) {
            val f = interpreter.getOrElse {
               val res = new ScalaInterpreterFrame( support /* ntp */ )
               interpreter = Some( res )
               res.setAlwaysOnTop( true )
               res.setDefaultCloseOperation( WindowConstants.HIDE_ON_CLOSE )
               res.addComponentListener( new ComponentAdapter {
                  override def componentHidden( e: ComponentEvent ) {
                     ggInterp.setSelected( false )
                  }
               })
               // for some reason the console is lost,
               // this way restores it
               Console.setErr( System.err )
               Console.setOut( System.out )
               res
            }
            f.setVisible( true )
         } else interpreter.foreach( _.setVisible( false ))
      }
      panel.add( ggInterp )
   }

   private def defer( code: => Unit ) { EventQueue.invokeLater( new Runnable { def run() { code }})}

   def makeWindow : JFrame = makeWindow()
   def makeWindow( undecorated: Boolean = true ) : JFrame = {
      val f = new JFrame( "Nuages Controls" )
      if( undecorated ) f.setUndecorated( true )
//      val cp = f.getContentPane()
//      cp.add( panel, BorderLayout.CENTER )
      f.setContentPane( panel )
      f.pack()
      f
   }

   def meterUpdate( peakRMSPairs: Array[ Float ]) {
      val tim = System.currentTimeMillis 
      masterMeterPanel.meterUpdate( peakRMSPairs, 0, tim )
      peopleMeterPanel.foreach( _.meterUpdate( peakRMSPairs, peopleOffset, tim ))
   }
}