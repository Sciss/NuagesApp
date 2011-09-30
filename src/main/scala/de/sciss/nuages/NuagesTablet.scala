package de.sciss.nuages

import java.awt.event.MouseEvent
import com.jhlabs.jnitablet.{TabletWrapper, TabletListener, TabletProximityEvent, TabletEvent}

object NuagesTablet {
   var debugProximity   = false

   private val sync = new AnyRef
   private var initialized = false

   def init( f: NuagesFrame ) {
      sync.synchronized {
         require( !initialized )
         initialized = true
         val inst = TabletWrapper.getInstance
         inst.addTabletListener( new Listener( f ))
      }
   }

   private final class Listener( f: NuagesFrame ) extends TabletListener {
      var wasInstant = false

      def tabletEvent( e: TabletEvent ) {
         if( !f.isActive ) return

         if( (e.getButtonMask & 0x02) != 0 ) {
            if( e.getID != MouseEvent.MOUSE_RELEASED ) {
               f.transition.setTransition( 2, e.getTiltY * -0.5 + 0.5 )
               wasInstant = false
            }
         } else {
            if( !wasInstant ) {
               f.transition.setTransition( 0, 0 )
               wasInstant = true
            }
         }
      }

      def tabletProximity( e: TabletProximityEvent ) {
         if( debugProximity ) {
            println( "TabletProximityEvent" )
            println( "  capabilityMask             " + e.getCapabilityMask )
            println( "  deviceID                   " + e.getDeviceID )
            println( "  enteringProximity          " + e.isEnteringProximity )
            println( "  pointingDeviceID           " + e.getPointingDeviceID )
            println( "  pointingDeviceSerialNumber " + e.getPointingDeviceSerialNumber )
            println( "  pointingDeviceType         " + e.getPointingDeviceType )
            println( "  systemTabletID             " + e.getSystemTabletID )
            println( "  tabletID                   " + e.getTabletID )
            println( "  uniqueID                   " + e.getUniqueID )
            println( "  vendorID                   " + e.getVendorID )
            println( "  vendorPointingDeviceType   " + e.getVendorPointingDeviceType )
            println()
         }
      }
   }
}