package de.sciss.nuages

import java.io.File
import collection.immutable.{IndexedSeq => IIdxSeq}
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import sys.process.Process
import util.control.NonFatal

object SuperJam extends App {
   args.toSeq match {
      case Seq( "--tapes", inputFolder ) => tapeSymlinks( inputFolder )
      case _ => println( """SuperJam Usage:
  --tapes <inputFolder>: generate tape symlinks
""" )
   }

   def tapeSymlinks( inputFolder: String ) {
      val fs = collectAudioFiles( new File( inputFolder )) { spec => import spec._
//         (sampleRate == 44100.0) && (numChannels == 2) && (numFrames > (sampleRate * 20).toLong)
         (sampleRate == 44100.0) && ((numChannels == 1) || (numChannels == 2)) && (numFrames > (sampleRate * 20).toLong)
      }
      fs.foreach { f =>
         val pb = Process(
            Seq( "/bin/ln", "-s", f.getCanonicalPath, new File( NuagesApp.TAPES_PATH, f.getName ).getCanonicalPath ),
            None
         )
         try {
            val res = pb.!
            if( res != 0 ) {
               println( "Failed (" + res + ") for " + f.getCanonicalPath )
            }
         } catch {
            case NonFatal( e ) =>
               println( "Failed (" + e.getClass.getName + ") for " + f.getCanonicalPath )
         }
      }
      println( "\nDone." )
   }

   def collectAudioFiles( dir: File )( filter: AudioFileSpec => Boolean ) : IIdxSeq[ File ] = {
      val b = IIdxSeq.newBuilder[ File ]
      def step( dir0: File ) {
         val fs = dir0.listFiles()
         var i = 0; while( i < fs.length ) {
            val f = fs( i )
            if( f.isDirectory ) step( f ) else try {
               val spec = AudioFile.readSpec( f )
               if( filter( spec )) b += f
            } catch {
               case NonFatal( _ ) => // ignore
            }
         i += 1 }
      }
      step( dir )
      b.result()
   }
}