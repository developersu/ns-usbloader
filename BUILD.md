### How to build this app

Java application:
* Install Maven
* Execute 
`# mvn -B -DskipTests clean package`

**Building JNI libraries section**

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