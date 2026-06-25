# Card communication — adding a new device interface

This package defines how JCProfilerNext talks to a smartcard, regardless of whether it is
reached through a standard PC/SC card reader, the jCardSim simulator, or a dedicated hardware
board like the LEIA board.

## Architecture

```
CardTarget  (interface)
  ├── CardManagerTarget   — PC/SC reader and jCardSim (via rcard CardManager)
  └── LeiaTarget          — LEIA USB smartcard board (via smartleia-java)
```

`CardTarget` is the single abstraction used throughout the rest of the codebase.
`Installer` creates a concrete instance and passes it to `AbstractProfiler`, which
stores it as `protected final CardTarget cardTarget`.  Profilers call only
`cardTarget.transmit()`, `cardTarget.disconnect()`, `cardTarget.getAtr()`, and
`cardTarget.getLastTransmitTimeNano()`.

## The `CardTarget` interface

```java
ResponseAPDU transmit(CommandAPDU apdu) throws CardException;
void         disconnect();
String       getAtr();                 // hex ATR string identifying the connected card
long         getLastTransmitTimeNano(); // board-measured round-trip; 0 if unsupported
```

## Adding a new device interface

### Step 1 — Implement `CardTarget`

Create `MyDeviceTarget.java` in this package:

```java
public class MyDeviceTarget implements CardTarget {

    private final MyDeviceDriver driver;

    public MyDeviceTarget(MyDeviceDriver driver) {
        this.driver = driver;
    }

    @Override
    public ResponseAPDU transmit(CommandAPDU apdu) throws CardException {
        // forward to your driver; wrap non-CardException errors as needed
    }

    @Override
    public void disconnect() {
        driver.close();
    }

    @Override
    public String getAtr() {
        return driver.getAtrHex(); // hex string, e.g. "3B9F96..."
    }

    @Override
    public long getLastTransmitTimeNano() {
        return 0L; // return real timing if the device provides it
    }
}
```

If the device supports only a subset of profiling modes (e.g. only SPA, not memory
profiling), document that in the class Javadoc.  If it needs extra methods beyond
`CardTarget` (trigger strategies, board-specific configuration, etc.), add them
directly to `MyDeviceTarget` — only code that knows it is talking to that specific
device will call them.

### Step 2 — Wire the connection in `Installer`

`Installer` is the single place where a `CardTarget` is constructed.  It has two
public entry points:

| Method | When called |
|---|---|
| `connect(Args, CtClass<?>)` | Normal profiling run — applet already on card |
| `installOnCard(Args, CtClass<?>)` | `--install` flag — installs the CAP first |

Both dispatch on `args.mode`.  Add a branch for the new mode in each method:

```java
// in connect()
if (args.mode == Mode.my_mode)
    return connectToMyDevice(/* select */ true);

// in installOnCard()
if (args.mode == Mode.my_mode) {
    final MyDeviceTarget target = connectToMyDevice(/* select */ false);
    // ... run GPTool if installation is supported, then SELECT the applet ...
    return target;
}
```

Add a private `connectToMyDevice(boolean select)` helper following the same pattern
as `connectToLeiaBoard()`:

```java
private static MyDeviceTarget connectToMyDevice(boolean select) {
    // 1. wait for device to appear
    // 2. wait for card
    // 3. configure protocol
    // 4. optionally SELECT the profiled applet
    return new MyDeviceTarget(driver);
}
```

### Step 3 — Add the mode enum value

Add the new mode to `jcprofiler/util/enums/Mode.java`.  Then add a `case my_mode`
branch in `AbstractProfiler.create()` if the mode requires a custom profiler
subclass (see `SpaTimeProfiler` for an example).  Standard profiling modes
(`time`, `memory`, `custom`) work without any change to `AbstractProfiler` because
they call only `CardTarget` methods.

### Step 4 — Installation support (optional)

If the device does not support applet installation, throw
`UnsupportedOperationException` in the `installOnCard()` branch and document it.
If it does, the device driver needs to implement the `apdu4j.BIBO` interface so
that GlobalPlatformPro can drive it.  See `LeiaTarget.toBIBO()` and
`smartleia.LeiaBIBO` for a reference implementation.

## Timing support

`getLastTransmitTimeNano()` returns `0L` when the device does not measure
round-trip time.  `TimeProfiler` uses this value to record per-APDU latency;
returning `0L` is safe — it just means timing data will be absent for that
device.  If the device does measure timing, return the value in **nanoseconds**.
