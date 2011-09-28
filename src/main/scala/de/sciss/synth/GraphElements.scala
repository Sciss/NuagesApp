package de.sciss.synth

import collection.immutable.{IndexedSeq => IIdxSeq}

//final case class ForceStereo( sig: GE ) extends GE.Lazy {
////      val sig  = disk.outputs.take(2)
////      if( numCh == 1 ) List( sig( 0 ), sig( 0 )) else sig
//}

final case class WrapExtendChannels( numOutputs: Int, sig: GE ) extends GE.Lazy {
   def rate = sig.rate
   def displayName = "WrapExtendChannels"

   def makeUGens : UGenInLike = {
      val exp     = sig.expand
      val sz      = exp.outputs.size
      UGenInGroup( IIdxSeq.tabulate( sz )( exp.unwrap( _ )))
   }
}

//final case class ZipChannels( a: GE, b: GE ) extends GE.Lazy {
//   def numOutputs = a.numOutputs + b.numOutputs // XXX bad
//   def rate = MaybeRate.max_?( a, b )  // XXX bad
//}
