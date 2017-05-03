/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.github.voltutils.utils;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.romraider.io.j2534.api.ConfigItem;
import com.romraider.io.j2534.api.J2534;
import com.romraider.io.j2534.api.J2534Impl;
import com.romraider.io.j2534.api.J2534Impl.Config;
import com.romraider.io.j2534.api.J2534Impl.Protocol;
import com.romraider.io.j2534.api.J2534Impl.TxFlags;
import com.romraider.io.j2534.api.Version;

/**
 * This class uses the J2534 API to retrieve the steering wheel data from a 2017 Chevy Volt.
 * <p>
 * Example:
 * <p>
 * <pre>
 * // create a dumper
 * VoltSteeringDumper dumper = new VoltSteeringDumper();
 * 
 * // take a reading 15 times per second for 10 seconds
 * for (int i = 0; i < 10 * 15; i++) {
 *     Thread.sleep(1000/15);
 *     System.out.println(dumper.getLastReadTimestamp() + ":\t" + dumper.getLastSteeringRead());
 * }
 *    
 * // kill the dumper
 * dumper.kill();
 * </pre>
 * <p>
 * Due to the J2534 drivers being 32-bit, this program requires a 32-bit JRE.  Using a 64-bit JRE will result in a 
 * {@link UnsatisfiedLinkError} when attempting to load the driver.
 */
public class VoltSteeringDumper {
	
	// use Open Port driver
//    private static final J2534 api = new J2534Impl(Protocol.CAN, "C:/Program Files (x86)/OpenECU/OpenPort 2.0/drivers/openport 2.0/op20pt32.dll");
	
	// use DashBridge driver
    private static final J2534 api = new J2534Impl(Protocol.CAN, "C:/Program Files (x86)/DashLogic/DashBridge CX/dbcx32.dll");
    
    /** Initial value for steering reading, indicating that it has not yet read a value. */
    public static final short NOT_READ_YET = Short.MIN_VALUE;
    
    /** Constant to tell the interface to use both 11-bit and 29-bit CAN messages. */
    private static final int CAN_ID_BOTH = 0x00000800;
    
    /** Mask that returns whatever it gets masked with. */
    private static final byte[] ALL_MASK = new byte[] { (byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff };
    
    /** Pattern that matches against the location containing the steering bytes. */
    private static final byte[] STEERING_PATTERN = new byte[] { 0,0,0x01,(byte)0xe5 };
    
    /** Device handle. */
    private int deviceId;
    /** Channel handle. */
    private int channelId;
    /** 11-bit CAN message handle. */
    private int msgId;
    /** 29-bit CAN message handle. */
    private int msgId2;
    
    /** Flag that keeps us alive. */
    private boolean dead = false;
    
    /** Holds the last steering wheel read. */
    private volatile short lastSteeringRead = NOT_READ_YET;
    /** 
     * The time of the last steering wheel read.
     * <p>
     * NOTE: The {@link #lastSteeringRead} is updated before the {@link #lastReadTimestamp} in a non-atomic way, so 
     * it's possible for there to be a (very) small window where the {@code lastReadTimestamp} is out of sync with 
     * the {@code lastSteeringRead}.
     */
    private volatile long lastReadTimestamp = 0;

    /**
     * Creates a new dumper and starts reading immediately.
     */
    public VoltSteeringDumper() {
    	init();
    }
    
    /**
     * Creates the connection to the Volt and kicks off the read-loop thread.
     */
    private void init() {
    	
    	// connect to the device
        this.deviceId = api.open();
        
        // open up a channel
        this.channelId = api.connect(deviceId, CAN_ID_BOTH, 500000);
                
    	// for 11-bit CAN messages
        this.msgId = api.startPassMsgFilter(channelId, ALL_MASK, STEERING_PATTERN);
        // for 29-bit CAN messages
        this.msgId2 = api.startPassMsgFilter(channelId, ALL_MASK, STEERING_PATTERN, TxFlags.CAN_29BIT_ID);
        
        // set our configuration
        setConfig(this.channelId);
        // print out the version (quick connection test)
        version(this.deviceId);
        
        // start up our main loop
        new Thread(new Runnable() {
			@Override
			public void run() {
                try {
                	// do our read rounds
                	while (!dead) {
                		// get the next message
                		byte[] response = api.readMsg(channelId, 0x01, 9999999);
                		// set our last read timestamp
        				VoltSteeringDumper.this.lastReadTimestamp = System.currentTimeMillis();
        				// grab the two bytes that contain the steering data and convert to a Short
        				VoltSteeringDumper.this.lastSteeringRead = 
        					ByteBuffer.wrap(Arrays.copyOfRange(response, 5, 7)).getShort();
                	}

                // try to clean up the connection when we're done
				} finally {
                    api.stopMsgFilter(channelId, msgId);
                    api.stopMsgFilter(channelId, msgId2);
                    api.disconnect(channelId);
                    api.close(deviceId);
                }
			}
        }).start(); // start the thread
    }
    
    /** Sets a flag the signals the main loop to stop.  Call this to stop the dumper. */
    public void kill() {
    	// set ourself as dead and let the thread clean up after itself
    	this.dead = true;
    }
    
    /** Queries the device for the version information and prints it to {@code STDOUT}. */
    private static void version(int deviceId) {
        Version version = api.readVersion(deviceId);
        System.out.printf("Version => Firmware:[%s], DLL:[%s], API:[%s]%n",
                version.firmware, version.dll, version.api);
    }

    /** Enables loopback on the device. */
    private static void setConfig(int channelId) {
        ConfigItem loopback = new ConfigItem(Config.LOOPBACK.getValue(), 1);
        api.setConfig(channelId, loopback);
    }
    
    /** Gets the last steering dump read.  Negative values are right turns, positive values are left turns.  The 
     *  absolute value of the returned value indicates how far the wheel is turned in its direction. */
	public short getLastSteeringRead() {
		return lastSteeringRead;
	}

	/** Gets an approximate read time of the last steering dump read. */ 
	public long getLastReadTimestamp() {
		return lastReadTimestamp;
	}

	/** Connects, gets a reading, prints it out, and terminates. */
	public static void main(String args[]) throws InterruptedException{

		// create a dumper
		VoltSteeringDumper dumper = new VoltSteeringDumper();

		// take a reading 15 times per second for 10 seconds
		for (int i = 0; i < 10 * 15; i++) {
			Thread.sleep(1000 / 15);
			System.out.println(dumper.getLastReadTimestamp() + ":\t" + dumper.getLastSteeringRead());
		}

		// kill the dumper
		dumper.kill();
    }
}
