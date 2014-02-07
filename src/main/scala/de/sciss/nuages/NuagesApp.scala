/*
 *  NuagesApp.scala
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

import de.sciss.synth._
import proc.{Proc, ProcTxn}

import java.util.Properties
import java.io.{FileOutputStream, FileInputStream, File}
import collection.breakOut
import collection.immutable.{IndexedSeq => Vec}
import util.control.NonFatal

object NuagesApp {

  def main(args: Array[String]): Unit = launch()

  val fs                  = File.separator
  val NUAGES_ANTIALIAS    = false
  //   var masterBus : AudioBus = null

  val DEBUG_PROPERTIES    = false

  val METER_MICS          = true
  //   val METER_REC           = true

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

  val properties = {
    val file = new File("nuages-settings.xml")
    val prop = new Properties()
    if (file.isFile) {
      val is = new FileInputStream(file)
      prop.loadFromXML(is)
      is.close()
    } else {
      prop.setProperty(PROP_BASEPATH,
        new File(new File(System.getProperty("user.home"), "Desktop"), "Nuages").getAbsolutePath)
      prop.setProperty(PROP_INDEVICE, "")
      prop.setProperty(PROP_OUTDEVICE, "")
      prop.setProperty(PROP_MASTERNUMCHANS, 2.toString)
      prop.setProperty(PROP_MASTEROFFSET, 0.toString)
      prop.setProperty(PROP_MASTERCHANGROUPS, "")
      prop.setProperty(PROP_MICCHANGROUPS, "")
      prop.setProperty(PROP_SOLOOFFSET, (-1).toString)
      prop.setProperty(PROP_SOLONUMCHANS, 2.toString)
      prop.setProperty(PROP_RECCHANGROUPS, "")
      prop.setProperty(PROP_PEOPLECHANGROUPS, "")
      try {
        val os = new FileOutputStream(file)
        try {
          prop.storeToXML(os, "Nuages Settings")
        } finally {
          os.close()
        }
      } catch {
        case NonFatal(e) => e.printStackTrace()
      }
    }
    prop
  }

  if (DEBUG_PROPERTIES) {
    properties.list(Console.out)
  }

  def decodeGroup(prop: String): Vec[NamedBusConfig] = {
    val s = properties.getProperty(prop, "")
    val r = """\x28(\w+),(\d+),(\d+)\x29""".r
    try {
      val l = r.findAllIn(s).toList
      l.map(s0 => {
        val Array(name, offS, chansS) = s0.substring(1, s0.length - 1).split(',')
        NamedBusConfig(name, offS.toInt, chansS.toInt)
      })(breakOut)
    } catch {
      case NonFatal(e) =>
        println("Error matching value '" + s + "' for prop '" + prop + "' : ")
        e.printStackTrace()
        Vec.empty
    }
  }

  val BASE_PATH           = properties.getProperty(PROP_BASEPATH)
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

  def launch() {
    val cfg = NuagesLauncher.SettingsBuilder()
    cfg.masterChannels = Some(MASTER_OFFSET until (MASTER_OFFSET + MASTER_NUMCHANNELS))
    cfg.soloChannels = if ( /* !INTERNAL_AUDIO && */ (SOLO_OFFSET >= 0)) {
      Some((SOLO_OFFSET until (SOLO_OFFSET + SOLO_NUMCHANNELS)))
    } else {
      None
    }
    cfg.collector        = USE_COLLECTOR
    cfg.antiAliasing     = NUAGES_ANTIALIAS
    cfg.tapeAction       = list => procs.foreach( _.tapePath = list.headOption.map( _.file.getAbsolutePath ))
    cfg.doneAction       = booted _
    cfg.tapeFolder       = Some(new File(TAPES_PATH))
    cfg.recordPath       = Some(/* new File( */ REC_PATH /* ) */)
    cfg.fullScreenKey    = true

    val cSet                = cfg.controlSettings
    cSet.numOutputChannels  = MASTER_NUMCHANNELS // + (if( METER_REC ) REC_CHANGROUPS.size else 0)
    cSet.numInputChannels   = (if( METER_MICS ) MIC_CHANGROUPS.size else 0) + PEOPLE_CHANGROUPS.size

    val replFile = new File("interpreter.txt")
    if (replFile.exists()) try {
      val fin = new FileInputStream(replFile)
      val i = fin.available()
      val arr = new Array[Byte](i)
      fin.read(arr)
      fin.close()
      val txt = new String(arr, "UTF-8")
      cSet.replSettings.text = txt
    } catch {
      case NonFatal(e) => e.printStackTrace()
    }
    cSet.replSettings.imports :+= "de.sciss.nuages.{NuagesApp => app}"

    // server options
    val o = cfg.serverConfig
    val inDevice = properties.getProperty(PROP_INDEVICE, "")
    val outDevice = properties.getProperty(PROP_OUTDEVICE, "")
    if (inDevice == outDevice) {
      if (inDevice != "") o.deviceName = Some(inDevice)
    } else {
      o.deviceNames = Some(inDevice -> outDevice)
    }
    val maxInIdx = ((MIC_CHANGROUPS ++ PEOPLE_CHANGROUPS).map(_.stopOffset) :+ 0).max

    val maxOutIdx = ((MASTER_OFFSET + MASTER_NUMCHANNELS) +: (if (SOLO_OFFSET >= 0) SOLO_OFFSET + SOLO_NUMCHANNELS else 0) +:
      REC_CHANGROUPS.map(_.stopOffset)).max

    o.inputBusChannels = maxInIdx
    o.outputBusChannels = maxOutIdx
    //      println( "MAX IN " + maxInIdx + " ; MAX OUT " + maxOutIdx )

    NuagesLauncher(cfg) // booom!
  }

  private var procs = Option.empty[NuagesProcs] // hmmm... not so nice

  var sum: Proc = _

  private def booted(r: NuagesLauncher.Ready) {
    NuagesFScape.init(r.server, r.frame)
    NuagesFScape.fsc.connect()(succ => println(if (succ) "FScape connected." else "!ERROR! : FScape not connected"))

    val procsS              = NuagesProcs.SettingsBuilder()
    procsS.server           = r.server
    procsS.frame            = r.frame
    procsS.audioFilesFolder = Some(new File(BASE_PATH, "sciss"))
    procsS.controlPanel     = Some(r.controlPanel)
    procsS.lineInputs       = PEOPLE_CHANGROUPS
    procsS.micInputs        = MIC_CHANGROUPS
    procsS.lineOutputs      = REC_CHANGROUPS
    procsS.masterGroups     = MASTER_CHANGROUPS
    val p                   = new NuagesProcs(procsS)

    sum = r.frame.panel.collector.orNull

    ProcTxn.spawnAtomic {
      implicit tx => p.init
    }

    if (USE_TABLET) try {
      NuagesTablet.init(r.frame)
    } catch {
      case NonFatal(e) => e.printStackTrace()
    }

    procs = Some(p)
  }
}