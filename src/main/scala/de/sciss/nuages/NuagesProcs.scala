/*
 *  NuagesProcs.scala
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

import de.sciss.synth.Server
import de.sciss.synth.proc.ProcTxn
import de.sciss.synth
import java.io.File
import synth.io.AudioFile
import collection.breakOut

object NuagesProcs {
   sealed trait SettingsLike {
      def server : Server
      def frame : NuagesFrame
      def controlPanel : Option[ ControlPanel ]

      def numLoops : Int

      /**
       * Maximum duration in seconds.
       */
      def loopDuration : Double
      def audioFilesFolder : Option[ File ]
   }

   object Settings {
      implicit def fromBuilder( b: SettingsBuilder ) : Settings = b.build
   }

   sealed trait Settings extends SettingsLike

   final case class SettingsBuilder() extends SettingsLike {
      var server : Server     = Server.default
      var frame : NuagesFrame = null   // grmpff
      var controlPanel : Option[ ControlPanel ] = None

      private var numLoopsVar : Int = 7
      def numLoops : Int = numLoopsVar
      def numLoops_=( n: Int ) {
         require( n >= 0 )
         numLoopsVar = n
      }

      var loopDuration : Double = 30.0

      var audioFilesFolder : Option[ File ] = None

      def build : Settings = SettingsImpl( server, frame, controlPanel, numLoops, loopDuration, audioFilesFolder )
   }

   private case class SettingsImpl( server: Server, frame: NuagesFrame, controlPanel: Option[ ControlPanel ],
                                    numLoops: Int, loopDuration: Double,
                                    audioFilesFolder: Option[ File ]) extends Settings
}

class NuagesProcs( val settings: NuagesProcs.Settings = NuagesProcs.SettingsBuilder().build ) {
   var tapePath : Option[ String ] = None

   def init( implicit tx: ProcTxn ) {
      import synth._
      import ugen._
      import proc._
      import DSL._

      val masterBusOption = settings.frame.panel.masterBus

      // -------------- GENERATORS --------------

//      val loopFrames = (LOOP_DUR * s.sampleRate).toInt
//      val loopBufs   = Array.fill[ Buffer ]( NUM_LOOPS )( Buffer.alloc( s, loopFrames, 2 ))
//      val loopBufIDs: Seq[ Int ] = loopBufs.map( _.id )( breakOut )

      gen( "tape" ) {
         val pspeed  = pAudio( "speed", ParamSpec( 0.1f, 10, ExpWarp ), 1 )
         val ploop   = pScalar( "loop", ParamSpec( 0, 1, LinWarp, 1 ), 0 )
         val ppos    = pScalar( "pos", ParamSpec( 0, 1 ), 0 )
         graph {
            val path      = tapePath.getOrElse( sys.error( "No audiofile selected" ))
            val spec      = audioFileSpec( path )
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

      settings.audioFilesFolder.foreach { folder =>
         val audioFiles = folder.listFiles()
         if( audioFiles != null ) audioFiles.filter( AudioFile.identify( _ ).isDefined ).foreach( f => {
            val name0   = f.getName
            val i       = name0.indexOf( '.' )
            val name    = if( i >= 0 ) name0.substring( 0, i ) else name0
            val path    = f.getCanonicalPath

            gen( name ) {
               val p1  = pAudio( "speed", ParamSpec( 0.1f, 10f, ExpWarp ), 1 )
               graph {
                  val b    = bufCue( path )
                  val disk = VDiskIn.ar( b.numChannels, b.id, p1.ar * BufRateScale.ir( b.id ), loop = 1 )
                  // HPF.ar( disk, 30 )
                  disk
               }
            }
         })
      }

      masterBusOption.foreach { masterBus =>
         gen( "(test)" ) {
            val pamp    = pControl( "amp",  ParamSpec( 0.01, 1.0, ExpWarp ), 1 )
            val psig    = pControl( "sig",  ParamSpec( 0, 1, LinWarp, 1 ), 0 )
            val pfreq   = pControl( "freq", ParamSpec( 0.1, 10, ExpWarp ), 1 )
            graph {
               val idx = Stepper.kr( Impulse.kr( pfreq.kr ), lo = 0, hi = masterBus.numChannels )
               val sigs: GE = Seq( WhiteNoise.ar( 1 ), SinOsc.ar( 441 ))
               val sig = Select.ar( psig.kr, sigs ) * pamp.kr
               val sigOut: GE = IndexedSeq.tabulate( masterBus.numChannels )( ch => sig * (1 - (ch - idx).abs.min( 1 )))
               sigOut
            }
         }
      }

      val loopFrames = (settings.loopDuration * sampleRate).toInt
      val loopBufs = Array.fill[ Buffer ]( settings.numLoops )( Buffer.alloc( settings.server, loopFrames, 2 ))
      val loopBufIDs: Seq[ Int ] = loopBufs.map( _.id )( breakOut )

      if( settings.numLoops > 0 ) {
         gen( "loop" ) {
            val pbuf    = pControl( "buf",   ParamSpec( 0, settings.numLoops - 1, LinWarp, 1 ), 0 )
            val pspeed  = pAudio( "speed", ParamSpec( 0.125, 2.3511, ExpWarp ), 1 )
            val pstart  = pControl( "start", ParamSpec( 0, 1 ), 0 )
            val pdur    = pControl( "dur",   ParamSpec( 0, 1 ), 1 )
            graph {
               val trig1	   = LocalIn.kr( 1 )
               val gateTrig1	= PulseDivider.kr( trig = trig1, div = 2, start = 1 )
               val gateTrig2	= PulseDivider.kr( trig = trig1, div = 2, start = 0 )
               val startFrame = pstart.kr * loopFrames
               val numFrames  = pdur.kr * (loopFrames - startFrame)
               val lOffset	   = Latch.kr( in = startFrame, trig = trig1 )
               val lLength	   = Latch.kr( in = numFrames,  trig = trig1 )
               val speed      = A2K.kr( pspeed.ar )
               val duration	= lLength / (speed * SampleRate.ir) - 2
               val gate1	   = Trig1.kr( in = gateTrig1, dur = duration )
               val env		   = Env.asr( 2, 1, 2, linShape )	// \sin
               val bufID      = Select.kr( pbuf.kr, loopBufIDs )
               val play1	   = PlayBuf.ar( 2, bufID, speed, gateTrig1, lOffset, loop = 0 )
               val play2	   = PlayBuf.ar( 2, bufID, speed, gateTrig2, lOffset, loop = 0 )
               val amp0		   = EnvGen.kr( env, gate1 )  // 0.999 = bug fix !!!
               val amp2		   = 1.0 - amp0.squared
               val amp1		   = 1.0 - (1.0 - amp0).squared
               val sig     	= (play1 * amp1) + (play2 * amp2)
               LocalOut.kr( Impulse.kr( 1.0 / duration.max( 0.1 )))
               sig
            }
         }

         masterBusOption.foreach { masterBus =>
            gen( "sum_rec" ) {
               val pbuf    = pControl( "buf",  ParamSpec( 0, settings.numLoops - 1, LinWarp, 1 ), 0 )
               val pfeed   = pControl( "feed", ParamSpec( 0, 1 ), 0 )
               val ploop   = pScalar( "loop", ParamSpec( 0, 1, LinWarp, 1 ), 0 )
               /* val ppos = */ pControl( "pos", ParamSpec( 0, 1 ), 0 )
               graph {
                  val in      = InFeedback.ar( masterBus.index, masterBus.numChannels )
                  val w       = 2.0 / in.numChannels // numOutputs
                  val sig     = SplayAz.ar( 2, in )

                  val sig1    = LeakDC.ar( Limiter.ar( sig /* .toSeq */ * w ))
                  val bufID   = Select.kr( pbuf.kr, loopBufIDs )
                  val feed    = pfeed.kr
                  val prelvl  = feed.sqrt
                  val reclvl  = (1 - feed).sqrt
                  val loop    = ploop.ir
                  val rec     = RecordBuf.ar( sig1, bufID, recLevel = reclvl, preLevel = prelvl, loop = loop )

                  // pos feedback
                  val bufFr   = BufFrames.kr( bufID )
                  val pos     = Phasor.kr( 1, SampleRate.ir/ControlRate.ir, 0, bufFr * 2 ) / bufFr // BufDur.kr( bufID ).reciprocal
                  val me      = Proc.local
                  val lp0     = ploop.v
                  Impulse.kr( 10 ).react( pos ) { smp => ProcTxn.spawnAtomic { implicit tx =>
                     val pos0 = smp( 0 )
                     // not sure we can access them in this scope, so just retrieve the controls...
                     val ppos = me.control( "pos" )
                     ppos.v   = if( lp0 == 1 ) (pos0 % 1.0) else pos0.min( 1.0 )
                  }}

                  Done.kr( rec ).react { ProcTxn.spawnAtomic { implicit tx => me.stop }}

                  Silent.ar( 2 )// dummy thru
               }
            }
         }
      }

      gen( "~dust" ) {
         val pfreq  = pAudio( "freq",  ParamSpec( 0.01, 1000, ExpWarp ), 0.1 /* 1 */)
         val pdecay = pAudio( "decay", ParamSpec( 0.1, 10, ExpWarp ), 0.1 /* 1 */)

          graph {
             val freq = pfreq.ar
             Decay.ar( Dust.ar( freq :: freq :: Nil ), pdecay.ar )
          }
      }

      gen( "~gray" ) {
         val pamp = pAudio( "amp", ParamSpec( 0.01, 1, ExpWarp ), 0.1 )
         graph {
            GrayNoise.ar( 1 :: 1 :: Nil ) * pamp.ar
         }
      }

      gen( "~sin" ) {
         val pfreq1 = pAudio( "freq", ParamSpec( 0.1, 10000, ExpWarp ), 15 /* 1 */)
         val pfreq2 = pAudio( "freq-fact", ParamSpec( 0.01, 100, ExpWarp ), 1 /* 1 */)
         val pamp   = pAudio( "amp", ParamSpec( 0.01, 1, ExpWarp ), 0.1 )

          graph {
             val f1 = pfreq1.ar
             val f2 = f1 * pfreq2.ar
             SinOsc.ar( f1 :: f2 :: Nil ) * pamp.ar
          }
      }

      gen( "~pulse" ) {
         val pfreq1 = pAudio( "freq", ParamSpec( 0.1, 10000, ExpWarp ), 15 /* 1 */)
         val pfreq2 = pAudio( "freq-fact", ParamSpec( 0.01, 100, ExpWarp ), 1 /* 1 */)
         val pw1    = pAudio( "width1", ParamSpec( 0.0, 1.0 ), 0.5 )
         val pw2    = pAudio( "width2", ParamSpec( 0.0, 1.0 ), 0.5 )
         val pamp   = pAudio( "amp", ParamSpec( 0.01, 1, ExpWarp ), 0.1 )

          graph {
             val f1 = pfreq1.ar
             val f2 = f1 * pfreq2.ar
             val w1 = pw1.ar
             val w2 = pw2.ar
             Pulse.ar( f1 :: f2 :: Nil, w1 :: w2 :: Nil ) * pamp.ar
          }
      }

      // -------------- FILTERS --------------

      def mix( in: GE, flt: GE, mix: ProcParamAudio ) : GE = LinXFade2.ar( in, flt, mix.ar * 2 - 1 )
      def pMix = pAudio( "mix", ParamSpec( 0, 1 ), 1 )

      // NuagesUHilbert

      if( settings.numLoops > 0 ) {
         filter( ">rec" ) {
            val pbuf    = pControl( "buf",  ParamSpec( 0, settings.numLoops - 1, LinWarp, 1 ), 0 )
            val pfeed   = pControl( "feed", ParamSpec( 0, 1 ), 0 )
            val ploop   = pScalar( "loop", ParamSpec( 0, 1, LinWarp, 1 ), 0 )
            /* val ppos = */ pScalar( "pos", ParamSpec( 0, 1 ), 0 )
            graph { in: In =>
               val bufID   = Select.kr( pbuf.kr, loopBufIDs )
               val feed    = pfeed.kr
               val prelvl  = feed.sqrt
               val reclvl  = (1 - feed).sqrt
               val loop    = ploop.ir
               val sig     = LeakDC.ar( Limiter.ar( in ))
               val rec     = RecordBuf.ar( sig /* in */, bufID, recLevel = reclvl, preLevel = prelvl, loop = loop )

               // pos feedback
   //            val pos     = Line.kr( 0, 1, BufDur.kr( bufID ))
               val bufFr   = BufFrames.kr( bufID )
               val pos     = Phasor.kr( 1, SampleRate.ir/ControlRate.ir, 0, bufFr * 2 ) / bufFr // BufDur.kr( bufID ).reciprocal
               val me      = Proc.local
               val lp0     = ploop.v
               Impulse.kr( 10 ).react( pos ) { smp => ProcTxn.spawnAtomic { implicit tx =>
                  val pos0 = smp( 0 )
                  // not sure we can access them in this scope, so just retrieve the controls...
                  val ppos = me.control( "pos" )
                  ppos.v   = if( lp0 == 1 ) (pos0 % 1.0) else pos0.min( 1.0 )
               }}

               Done.kr( rec ).react { ProcTxn.spawnAtomic { implicit tx => me.bypass }}

               in  // dummy thru
            }
         }
      }

      filter( "delay" ) {
         val ptime   = pAudio( "time", ParamSpec( 0.3,  30.0, ExpWarp ), 10 )
         val pfeed   = pAudio( "feed", ParamSpec( 0.001, 1.0, ExpWarp ), 0.001 )
         val pmix    = pMix
         graph { in: In =>
            val numFrames  = (sampleRate * 30).toInt
            val numChannels= in.numChannels // numOutputs
            val buf        = bufEmpty( numFrames, numChannels )
            val bufID      = buf.id
            val time       = ptime.ar
            val lin        = LocalIn.ar( numChannels )
            val feed       = pfeed.ar
            val wDry       = (1 - feed).sqrt
            val wWet       = feed.sqrt
            val flt0       = BufDelayL.ar( bufID, (in * wDry) + (lin * wWet), time )
            val flt        = LeakDC.ar( flt0 )
            LocalOut.ar( flt )

            mix( in, flt, pmix )
         }
      }

      filter( "mantissa" ) {
         val pbits   = pAudio( "bits", ParamSpec( 2, 14, LinWarp, 1 ), 14 )
         val pmix    = pMix

         graph { in: In =>
            val flt  = MantissaMask.ar( in, pbits.ar )
            mix( in, flt, pmix )
         }
      }

      filter( "achil") {
         val pspeed  = pAudio( "speed", ParamSpec( 0.125, 2.3511, ExpWarp ), 0.5 )
         val pmix    = pMix

         graph { in: In =>
println( "WARNING: Achilles currently not working ?!" )
            val speed	   = Lag.ar( pspeed.ar, 0.1 )
            val numFrames  = sampleRate.toInt
            val numChannels= in.numChannels // numOutputs
            val buf        = bufEmpty( numFrames, numChannels )
            val bufID      = buf.id
            val writeRate  = BufRateScale.kr( bufID )
            val readRate   = writeRate * speed
            val readPhasor = Phasor.ar( 0, readRate, 0, numFrames )
            val read       = BufRd.ar( numChannels, bufID, readPhasor, 0, 4 )
            val writePhasor= Phasor.ar( 0, writeRate, 0, numFrames )
            val old        = BufRd.ar( numChannels, bufID, writePhasor, 0, 1 )
            val wet0       = SinOsc.ar( 0, ((readPhasor - writePhasor).abs / numFrames * math.Pi) )
            val dry        = 1 - wet0.squared
            val wet        = 1 - (1 - wet0).squared
            BufWr.ar( (old * dry) + (in * wet), bufID, writePhasor )
            mix( in, read, pmix )
         }
      }

      filter( "a-gate" ) {
         val pamt = pAudio( "amt", ParamSpec( 0, 1 ), 1 )
         val pmix = pMix
         graph { in: In =>
            val amount = Lag.ar( pamt.ar, 0.1 )
            val flt = Compander.ar( in, in, Amplitude.ar( in * (1 - amount ) * 5 ), 20, 1, 0.01, 0.001 )
            mix( in, flt, pmix )
         }
      }

// XXX TODO
//      filter( "a-hilb" ) {
//         val pmix = pMix
//         graph { in: In =>
//            var flt: GE = List.fill( in.numOutputs )( 0.0 )
//            in.outputs foreach { ch =>
//               val hlb  = Hilbert.ar( DelayN.ar( ch, 0.01, 0.01 ))
//               val hlb2 = Hilbert.ar( Normalizer.ar( ch, dur = 0.02 ))
//               flt     += (hlb \ 0) * (hlb2 \ 0) - (hlb \ 1 * hlb2 \ 1)
//            }
//            mix( in, flt, pmix )
//         }
//      }

      filter( "hilbert" ) {
         val pfreq   = pAudio( "freq", ParamSpec( -1, 1 ), 0.0 )
         val pmix    = pMix
         graph { in: In =>
            val freq    = pfreq.ar
            val freqHz  = freq.abs.linexp( 0, 1, 20, 12000 ) * freq.signum
            val flt     = FreqShift.ar( in, freqHz )
            mix( in, flt, pmix )
         }
      }

      filter( "reso" ) {
//         val pfreq   = pAudio( "freq", ParamSpec( -1, 1 ), 0.54 ) // check dis out other time -- interesting stuff going on :)
         val pfreq   = pAudio( "freq", ParamSpec( 30, 13000, ExpWarp ), 400 )  // beware of the upper frequency
         val pfreq2  = pAudio( "freq-fact", ParamSpec( 0.5, 2, ExpWarp ), 1 )
         val pq      = pAudio( "q", ParamSpec( 0.5, 50, ExpWarp ), 1 )
         val pmix    = pMix

         graph { in: In =>
            val freq0   = pfreq.ar
            val freq    = freq0 :: (freq0 * pfreq2.ar).max( 30 ).min( 13000 ) :: Nil
            val rq      = pq.ar.reciprocal
            val makeUp  = (rq + 0.5).pow( 1.41 ) // rq.max( 1 ) // .sqrt
            val flt     = Resonz.ar( in, freq, rq ) * makeUp
            mix( in, flt, pmix )
         }
      }

      filter( "notch" ) {
         val pfreq   = pAudio( "freq", ParamSpec( 30, 16000, ExpWarp ), 400 )
         val pfreq2  = pAudio( "freq-fact", ParamSpec( 0.5, 2, ExpWarp ), 1 )
         val pq      = pAudio( "q", ParamSpec( 1, 50, ExpWarp ), 1 )       // beware of the lower q
         val pmix    = pMix

         graph { in: In =>
            val freq0   = pfreq.ar
            val freq    = freq0 :: (freq0 * pfreq2.ar).max( 30 ).min( 16000 ) :: Nil
            val rq      = pq.ar.reciprocal
//            val makeUp  = (rq + 0.5).pow( 1.41 ) // rq.max( 1 ) // .sqrt
            val flt     = BRF.ar( in, freq, rq )
            mix( in, flt, pmix )
         }
      }

      filter( "filt" ) {
         val pfreq   = pAudio( "freq", ParamSpec( -1, 1 ), 0.54 )
         val pmix    = pMix

         graph { in: In =>
            val normFreq	= pfreq.ar
            val lowFreqN	= Lag.ar( Clip.ar( normFreq, -1, 0 ))
            val highFreqN	= Lag.ar( Clip.ar( normFreq,  0, 1 ))
            val lowFreq		= LinExp.ar( lowFreqN, -1, 0, 30, 20000 )
            val highFreq	= LinExp.ar( highFreqN, 0, 1, 30, 20000 )
            val lowMix		= Clip.ar( lowFreqN * -10.0, 0, 1 )
            val highMix		= Clip.ar( highFreqN * 10.0, 0, 1 )
            val dryMix		= 1 - (lowMix + highMix)
            val lpf			= LPF.ar( in, lowFreq ) * lowMix
            val hpf			= HPF.ar( in, highFreq ) * highMix
            val dry			= in * dryMix
            val flt			= dry + lpf + hpf
            mix( in, flt, pmix )
         }
      }

      filter( "frgmnt" ) {
   		val pspeed  = pAudio(   "speed", ParamSpec( 0.125, 2.3511, ExpWarp ), 1 )
		   val pgrain  = pControl( "grain", ParamSpec( 0, 1 ), 0.5 )
		   val pfeed   = pAudio(   "fb",    ParamSpec( 0, 1 ), 0 )
         val pmix    = pMix

         graph { in: In =>
            val bufDur        = 4.0
            val numFrames     = (bufDur * sampleRate).toInt
            val numChannels   = in.numChannels // numOutputs
            val buf           = bufEmpty( numFrames, numChannels )
            val bufID         = buf.id

            val feedBack      = Lag.ar( pfeed.ar, 0.1 )
            val grain         = pgrain.kr // Lag.kr( grainAttr.kr, 0.1 )
            val maxDur        = LinExp.kr( grain, 0, 0.5, 0.01, 1.0 )
            val minDur        = LinExp.kr( grain, 0.5, 1, 0.01, 1.0 )
            val fade          = LinExp.kr( grain, 0, 1, 0.25, 4 )
            val rec           = (1 - feedBack).sqrt
            val pre           = feedBack.sqrt
            val trig          = LocalIn.kr( 1 )
            val white         = TRand.kr( 0, 1, trig )
            val dur           = LinExp.kr( white, 0, 1, minDur, maxDur )
            val off0          = numFrames * white
            val off           = off0 - (off0 % 1.0)
            val gate          = trig
            val lFade         = Latch.kr( fade, trig )
            val fadeIn        = lFade * 0.05
            val fadeOut       = lFade * 0.15
            val env           = EnvGen.ar( Env.linen( fadeIn, dur, fadeOut, 1, sinShape ), gate, doneAction = 0 )
            val recLevel0     = env.sqrt
            val preLevel0     = (1 - env).sqrt
            val recLevel      = recLevel0 * rec
            val preLevel      = preLevel0 * (1 - pre) + pre
            val run           = recLevel > 0
            RecordBuf.ar( in, bufID, off, recLevel, preLevel, run, 1 )
            LocalOut.kr( Impulse.kr( 1.0 / (dur + fadeIn + fadeOut ).max( 0.01 )))

      	   val speed         = pspeed.ar
			   val play          = PlayBuf.ar( numChannels, bufID, speed, loop = 1 )
            mix( in, play, pmix )
      	}
      }

      filter( "*" ) {
         val pmix = pMix
         val bin2  = pAudioIn( "in2" )
         graph { in: In =>
            val in2  = bin2.ar
            val flt  = in * in2
            mix( in, flt, pmix )
         }
      }

      filter( "gain" ) {
         val pgain   = pAudio( "gain", ParamSpec( -30, 30 ), 0 )
         val pmix = pMix
         graph { in: In =>
            val amp  = pgain.ar.dbamp
            val flt  = in * amp
            mix( in, flt, pmix )
         }
      }

      filter( "gendy" ) {
         val pamt = pAudio( "amt", ParamSpec( 0, 1 ), 1 )
         val pmix = pMix
         graph { in: In =>
            val amt     = Lag.ar( pamt.ar, 0.1 )
            val minFreq	= amt * 69 + 12;
            val scale	= amt * 13 + 0.146;
            val gendy   = Gendy1.ar( 2, 3, 1, 1,
               minFreq = minFreq, maxFreq = minFreq * 8,
               ampScale = scale, durScale = scale,
               initCPs = 7, kNum = 7 ) * in
            val flt	   = Compander.ar( gendy, gendy, 0.7, 1, 0.1, 0.001, 0.02 )
            mix( in, flt, pmix )
         }
      }

      filter( "~skew" ) {
         val plo  = pAudio( "lo", ParamSpec( 0, 1 ), 0 )
         val phi  = pAudio( "hi", ParamSpec( 0, 1 ), 1 )
         val ppow = pAudio( "pow", ParamSpec( 0.125, 8, ExpWarp ), 1 )
         val prnd = pAudio( "rnd", ParamSpec( 0, 1 ), 0 )

         val pmix = pMix
         graph { in: In =>
            val sig = in.clip2( 1 ).linlin( -1, 1, plo.ar, phi.ar ).pow( ppow.ar ).round( prnd.ar ) * 2 - 1
            mix( in, sig, pmix )
         }
      }

      filter( "~onsets" ) {
         val pthresh = pControl( "thresh", ParamSpec( 0, 1 ), 0.5 )
         val pdecay  = pAudio( "decay",  ParamSpec( 0, 1 ), 0 )

         val pmix = pMix
         graph { in: In =>
            val numChannels   = in.numChannels // numOutputs
            val bufIDs        = Seq.fill( numChannels )( bufEmpty( 1024 ).id )
            val chain1        = FFT( bufIDs, in )
            val onsets        = Onsets.kr( chain1, pthresh.kr )
            val sig           = Decay.ar( Trig1.ar( onsets, SampleDur.ir ), pdecay.ar ).min( 1 ) // * 2 - 1
            mix( in, sig, pmix )
         }
      }

      filter( "m-above" ) {
         val pthresh = pAudio( "thresh", ParamSpec( 1.0e-3, 1.0e-1, ExpWarp ), 1.0e-2 )
         val pmix = pMix
         graph { in: In =>
            val numChannels   = in.numChannels // numOutputs
            val thresh		   = A2K.kr( pthresh.ar )
            val env			   = Env( 0.0, Seq( Env.Seg( 0.2, 0.0, stepShape ), Env.Seg( 0.2, 1.0, linShape )))
            val ramp			   = EnvGen.kr( env )
            val volume		   = LinLin.kr( thresh, 1.0e-3, 1.0e-1, 32, 4 )
            val bufIDs        = List.fill( numChannels )( bufEmpty( 1024 ).id )
            val chain1 		   = FFT( bufIDs, HPZ1.ar( in ))
            val chain2        = PV_MagAbove( chain1, thresh )
            val flt			   = LPZ1.ar( volume * IFFT( chain2 )) * ramp

            // account for initial dly
            val env2          = Env( 0.0, Seq( Env.Seg( BufDur.kr( bufIDs ) * 2, 0.0, stepShape ), Env.Seg( 0.2, 1, linShape )))
            val wet			   = EnvGen.kr( env2 )
            val sig			   = (in * (1 - wet).sqrt) + (flt * wet)
            mix( in, sig, pmix )
         }
      }

      filter( "m-below" ) {
         val pthresh = pAudio( "thresh", ParamSpec( 1.0e-2, 1.0e-0, ExpWarp ), 1.0e-1 )
         val pmix = pMix
         graph { in: In =>
            val numChannels   = in.numChannels // numOutputs
            val thresh		   = A2K.kr( pthresh.ar )
            val env			   = Env( 0.0, Seq( Env.Seg( 0.2, 0.0, stepShape ), Env.Seg( 0.2, 1.0, linShape )))
            val ramp			   = EnvGen.kr( env )
            val volume		   = LinLin.kr( thresh, 1.0e-2, 1.0e-0, 4, 1 )
            val bufIDs        = List.fill( numChannels )( bufEmpty( 1024 ).id )
            val chain1 		   = FFT( bufIDs, in )
            val chain2        = PV_MagBelow( chain1, thresh )
            val flt			   = volume * IFFT( chain2 )

            // account for initial dly
            val env2          = Env( 0.0, Seq( Env.Seg( BufDur.kr( bufIDs ) * 2, 0.0, stepShape ), Env.Seg( 0.2, 1, linShape )))
            val wet			   = EnvGen.kr( env2 )
            val sig			   = (in * (1 - wet).sqrt) + (flt * wet)
            mix( in, sig, pmix )
         }
      }


//      val dfPostM = SynthDef( "post-master" ) {
//         val sig = In.ar( masterBus.index, masterBus.numChannels )
//         // externe recorder
//         REC_CHANGROUPS.foreach { group =>
//            val (name, off, numOut) = group
//            val numIn   = masterBus.numChannels
//            val sig1: GE = if( numOut == numIn ) {
//               sig
//            } else if( numIn == 1 ) {
//               Seq.fill[ GE ]( numOut )( sig )
//            } else {
//               val sigOut = SplayAz.ar( numOut, sig )
//               Limiter.ar( sigOut, (-0.2).dbamp )
//            }
////            assert( sig1.numOutputs == numOut )
//            Out.ar( off, sig1 )
//         }
//         // master + people meters
//         val meterTr    = Impulse.kr( 20 )
//         val (peoplePeak, peopleRMS) = {
//            val res = PEOPLE_CHANGROUPS.map { group =>
//               val (_, off, numIn)  = group
//               val pSig       = In.ar( NumOutputBuses.ir + off, numIn )
//               val peak       = Peak.kr( pSig, meterTr ) // .outputs
//               val peakM      = Reduce.max( peak )
//               val rms        = A2K.kr( Lag.ar( pSig.squared, 0.1 ))
//               val rmsM       = Mix.mono( rms ) / numIn
//               (peakM, rmsM)
//            }
//            (res.map( _._1 ): GE) -> (res.map( _._2 ): GE)  // elegant it's not
//         }
//         val masterPeak    = Peak.kr( sig, meterTr )
//         val masterRMS     = A2K.kr( Lag.ar( sig.squared, 0.1 ))
//         val peak: GE      = Flatten( Seq( masterPeak, peoplePeak ))
//         val rms: GE       = Flatten( Seq( masterRMS, peopleRMS ))
//         val meterData     = Zip( peak, rms )  // XXX correct?
//         SendReply.kr( meterTr, meterData, "/meters" )
//      }
//      synPostM = dfPostM.play( s, addAction = addToTail )
//            val synPostMID = NuagesProcs.synPostM.id
//            OSCResponder.add({
//               case Message( "/meters", `synPostMID`, 0, values @ _* ) =>
//                  EventQueue.invokeLater( new Runnable { def run() { ctrl.meterUpdate( values.map( _.asInstanceOf[ Float ])( breakOut ))}})
//            }, s )
   }
}