# Volt Utilities
Utilities and code snippets related to interfacing with the Chevy Volt

## Volt Steering Dumper ##
This utility is used to retrieve the steering wheel data from a 2017 Chevy Volt.  This has been tested using a DashLogic DashBridge CX cable.  The compatible J2534 driver can be found on the DashLogic website: <http://www.dashlogic.com/downloads>.  It is assumed that this will also work with the more common Open Port driver, but a compatible cable was not available to the developers.

Usage example:
```java
// create a dumper
VoltSteeringDumper dumper = new VoltSteeringDumper();

// take a reading 15 times per second for 10 seconds
for (int i = 0; i < 10 * 15; i++) {
	Thread.sleep(1000 / 15);
	System.out.println(dumper.getLastReadTimestamp() + ":\t" + dumper.getLastSteeringRead());
}

// kill the dumper
dumper.kill();
```

## Open Source Code ##
These utilities make use of the Rom Raider Java API for J2534 (slightly modified).  Source code is found in the `com.romraider` package.  The RomRaider project can be found at <https://github.com/RomRaider/RomRaider>.
