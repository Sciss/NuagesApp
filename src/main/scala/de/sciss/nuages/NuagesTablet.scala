/*
 *  NuagesTablet.scala
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

import java.awt.event.MouseEvent
import com.jhlabs.jnitablet.{TabletWrapper, TabletListener, TabletProximityEvent, TabletEvent}

object NuagesTablet {
  var debugProximity = false

  private val sync = new AnyRef
  private var initialized = false

  def init(f: NuagesFrame): Unit = {
    sync.synchronized {
      require(!initialized)
      initialized = true
      val inst = TabletWrapper.getInstance
      inst.addTabletListener(new Listener(f))
      println("Tablet initialized.")
    }
  }

  private final class Listener(f: NuagesFrame) extends TabletListener {
    var wasInstant = false

     def tabletEvent(e: TabletEvent): Unit = {
       if (!f.isActive) return

       if ((e.getButtonMask & 0x02) != 0) {
         if (e.getID != MouseEvent.MOUSE_RELEASED) {
           f.transition.setTransition(2, e.getTiltY * -0.5 + 0.5)
           wasInstant = false
         }
       } else {
         if (!wasInstant) {
           f.transition.setTransition(0, 0)
           wasInstant = true
         }
       }
     }

    def tabletProximity(e: TabletProximityEvent): Unit = {
      if (debugProximity) {
        println("TabletProximityEvent" )
        println("  capabilityMask             " + e.getCapabilityMask )
        println("  deviceID                   " + e.getDeviceID )
        println("  enteringProximity          " + e.isEnteringProximity )
        println("  pointingDeviceID           " + e.getPointingDeviceID )
        println("  pointingDeviceSerialNumber " + e.getPointingDeviceSerialNumber )
        println("  pointingDeviceType         " + e.getPointingDeviceType )
        println("  systemTabletID             " + e.getSystemTabletID )
        println("  tabletID                   " + e.getTabletID )
        println("  uniqueID                   " + e.getUniqueID )
        println("  vendorID                   " + e.getVendorID )
        println("  vendorPointingDeviceType   " + e.getVendorPointingDeviceType )
        println()
      }
    }
  }
}