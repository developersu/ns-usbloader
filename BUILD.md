### How to build this app

Java application:
* Install Maven
* Execute 
`# mvn -B -DskipTests clean package`

The build is driven by Maven profiles in a single `pom.xml`:

| Command                                      | Result                                                                                                                                                         |
|----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `mvn -B -DskipTests clean package`           | standard build (all platforms, `usb4java 1.3.0`, `NS-USBloader.exe`)                                                                                           |
| `mvn -B -DskipTests clean package -Plegacy`  | legacy build (`usb4java 1.2.0` with default bundled `libusb4java 1.2.0` (no `ru.redrise.libusb4java`, `ns-usbloader-<version>-legacy.jar`, `NS-USBloader.exe`) |
| `mvn -B -DskipTests clean package -Paarch64` | macOS/Linux aarch64 build (`linux-aarch64`/`mac-aarch64` JFX, aarch64 `libusb4java`, no Windows artifacts)                                                     |


**Building JNI libraries (OPTIONAL)**

First of all install JDK, GCC, make, kernel headers and whatever else (or use Gentoo <3 ).

Generate header (Optional! Already generated.):
```
$ cp NS-USBloader/src/main/java/nsusbloader/Utilities/RcmSmash.java .
$ javac -h . RcmSmash.java
```
**Build for Linux (amd64 Linux host):**
``` 
 $ cd 'NS-USBloader/JNI sources/linux'
 $ make install clean
```
**Build for Raspberry Pi (Raspberry Pi host):**
``` 
 $ cd 'NS-USBloader/JNI sources/linux'
 $ mv Makefile_rapsberry Makefile
 $ make install clean
```

**Build for Windows (on x86_64 host):**

[ This part should be updated ]

Install MinGW, msys (?) MinGW-w64 and JDK. Set JAVA_HOME, set PATH to match MinGW, MSYS, JDK/bin (and other?) environment variables.

x86: Install MinGw to C:\MinGW\

Update sources: set (uncomment) line 'BUILD_FOR_X86'

Set environment variables for MinGw:
* C:\MinGW\bin
* C:\MinGW\msys\1.0\bin
```
 $ cd 'NS-USBloader/JNI sources/windows'
 $ make x86
```

amd64: Install MinGw-w64

Update sources: remove line 'BUILD_FOR_X86'
```
 $ make amd64
```