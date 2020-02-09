# Compiler
CC32='C:/MinGW/bin/gcc'
CC64='C:/Program Files/mingw-w64/x86_64-8.1.0-win32-seh-rt_v6-rev0/mingw64/bin/gcc'
# Flags
CFLAGS=-O2 
MKDIR_P=mkdir
APP_NAME=smashlib.dll

all: x86 amd64

	#$(CC) ${CFLAGS} -m32 -c -fPIC -I "C:/MinGW/include/ddk" -I "${JAVA_HOME}/include" -I "${JAVA_HOME}/include/win32" smashlib.c -o ./x86/smashlib.o # MinGw-32 version
x86: 
	$(MKDIR_P) ./x86
	export PATH="C/MinGW/bin/:${PATH}"  
	$(CC32) ${CFLAGS} -m32 -c -fPIC -I "C:/MinGW/include/ddk" -I "${JAVA_HOME}/include" -I "${JAVA_HOME}/include/win32" smashlib.c -o ./smashlib_x86.o
	$(CC32) ${CFLAGS} -shared -o ./x86/${APP_NAME} ./smashlib_x86.o -lsetupapi -lhid -Wl,--add-stdcall-alias

	#$(CC) ${CFLAGS} -m64 -c -fPIC -I "C:/MinGW/include/ddk" -I "${JAVA_HOME}/include" -I "${JAVA_HOME}/include/win32" smashlib.c -o ./amd64/smashlib.o # MinGw-32 version 
amd64:
	$(MKDIR_P) ./amd64
	export PATH="C/Program Files/mingw-w64/x86_64-8.1.0-win32-seh-rt_v6-rev0/mingw64/bin/:${PATH}"
	$(CC64) ${CFLAGS} -m64 -c -fPIC -I "${JAVA_HOME}/include" -I "${JAVA_HOME}/include/win32" smashlib.c -o ./smashlib_amd64.o
	$(CC64) ${CFLAGS} -shared -o ./amd64/${APP_NAME} ./smashlib_amd64.o -lsetupapi -lhid -Wl,--add-stdcall-alias

clean:
	rm -rf ./smashlib_x86.o ./smashlib_amd64.o ./x86 ./amd64

install: x86 amd64
	install ./x86/${APP_NAME} ../../src/main/resources/native/windows/x86/
	install ./amd64/${APP_NAME} ../../src/main/resources/native/windows/amd64/

uninstall: 
	rm ../../src/main/resources/native/windows/x86/${APP_NAME}
    rm ../../src/main/resources/native/windows/amd64/${APP_NAME}
