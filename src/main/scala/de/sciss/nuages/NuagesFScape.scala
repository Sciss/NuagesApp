/*
 *  FScapeNuages.scala
 *  (NuagesApp)
 *
 *  Copyright (c) 2010-2013 Hanns Holger Rutz. All rights reserved.
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
 */

package de.sciss.nuages

import java.io.File
import java.util.{Timer => UTimer, TimerTask}
import NuagesApp._
import de.sciss.fscape.FScapeJobs
import de.sciss.synth
import synth._
import proc.{LinWarp, ExpWarp, DSL, Proc, ParamSpec, ProcTxn}
import ugen._
import DSL._

object NuagesFScape {
   lazy val fsc = FScapeJobs()

   private var cnt = 0

   def init( s: Server, f: NuagesFrame ) = ProcTxn.atomic { implicit tx =>
      filter( ">fsc" ) {
         /* val palgo = */ pScalar(  "algo", ParamSpec( 0,  3, LinWarp, 1 ), 0 )
         val pstop   = pControl( "stop", ParamSpec( 0,  1, LinWarp, 1 ), 0 )
         /* val ppos = */ pControl( "pos",  ParamSpec( 0, 60, LinWarp, 1 ), 0 )
         graph { in: In =>
//            val cnt0    = cnt
            cnt += 1
            val pathIn  = REC_PATH + fs + "fscin" + cnt + ".aif"
            val pathOut = REC_PATH + fs + /* "_fsc" */ "|fsc" + cnt + ".aif"
            val buf     = bufRecord( pathIn, in.numChannels )
            DiskOut.ar( buf.id, in )
            val me      = Proc.local
            var pos     = 0
            Impulse.kr( 1 ).react{ ProcTxn.spawnAtomic { implicit tx =>
               if( pos < 60 ) {
                  pos += 1
                  me.control( "pos" ).v = pos
               }
            }}
            pstop.kr.react {
               ProcTxn.spawnAtomic { implicit tx =>
                  me.control( "stop" ).v = 0 // hmmm... doesn't work? maybe too early?
                  me.bypass // stop
                  val algo = me.control( "algo" ).v.toInt
                  new UTimer().schedule( new TimerTask {
                     def run() {
                        process( pathIn, pathOut, algo )
                     }
                  }, 2000 )   // XXX bit tricky, we need to make sure that the buffer was closed properly
               }
            }
            in
         }
      }
   }

   private def process( pathIn: String, pathOut: String, algo: Int ) {
      import FScapeJobs._

      val tmpPath = REC_PATH + fs + "fsctmp.aif"
      val doc = algo match {
         case 0 =>
            Wavelet( pathIn, tmpPath, OutputSpec.aiffInt, Gain.normalized, filter = "daub16", trunc = true )
         case 1 =>
            Bleach( pathIn, None, tmpPath, OutputSpec.aiffInt, Gain.normalized, feedback = "-54dB" )
         case 2 =>
            Bleach( pathIn, None, tmpPath, OutputSpec.aiffInt, Gain.normalized, feedback = "-54dB", inverse = true )
         case 3 =>
            StepBack( pathIn, tmpPath, OutputSpec.aiffInt, Gain.normalized, minXFade = "0.05s", maxSpacing = "3.0s" )
         case _ => sys.error( "Illegal algorithm number " + algo )
      }

      val docLp = MakeLoop( tmpPath, pathOut, OutputSpec.aiffInt, Gain.normalized )

      fsc.processChain( "nuages", doc :: docLp :: Nil )( if( _ ) {
         println( "FScape done!" )
         addFactory( pathOut )
      } else {
         println( "FScape failed!" )
      })
   }

   private def addFactory( path: String ) {
      val name0   = new File( path ).getName
      val i       = name0.lastIndexOf( '.' )
      val name    = if( i < 0 ) name0 else name0.substring( 0, i )
      val spec    = audioFileSpec( path )
      ProcTxn.atomic { implicit tx =>
         gen( name ) {
            val pspeed  = pAudio( "speed", ParamSpec( 0.1f, 10, ExpWarp ), 1 )
            val ploop   = pScalar( "loop", ParamSpec( 0, 1, LinWarp, 1 ), 1 )
            val ppos    = pScalar( "pos", ParamSpec( 0, 1 ), 0 )
            graph {
               val numFrames = spec.numFrames
               val startPos  = ppos.v
               val startFr   = ((if( startPos < 1 ) startPos else 0.0) * numFrames).toLong
               val b         = bufCue( path, startFrame = startFr )
               val numCh     = b.numChannels
               val speed     = A2K.kr( pspeed.ar ) * BufRateScale.ir( b.id )
               val lp0        = ploop.v

               // pos feedback
               val framesRead = Integrator.kr( speed ) * (SampleRate.ir / ControlRate.ir)
               val me = Proc.local
               Impulse.kr( 10 ).react( framesRead ) { smp => ProcTxn.spawnAtomic { implicit tx =>
                  val frame  = startFr + smp( 0 )
                  // not sure we can access them in this scope, so just retrieve the controls...
                  val ppos   = me.control( "pos" )
//               val ploop  = me.control( "loop" )
                  if( lp0 == 1 ) {
                     ppos.v = (frame % numFrames).toDouble / numFrames
                  } else {
                     val pos = (frame.toDouble / numFrames).min( 1.0 )
                     ppos.v = pos
                     if( pos == 1.0 ) me.stop
                  }
               }}

               val disk = VDiskIn.ar( numCh, b.id, speed, loop = ploop.ir )
               WrapExtendChannels( 2, disk )
            }
         }
      }
   }
}