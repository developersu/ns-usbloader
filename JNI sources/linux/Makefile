# Compiler
CC=gcc
# Flags
CFLAGS=-O2
MKDIR_P = mkdir -p
APP_NAME = smashlib.so

all: x86 amd64

x86:
	$(MKDIR_P) ./x86
	$(CC) ${CFLAGS} -m32 -c -fPIC -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" smashlib.c -o smashlib_x86.o
	$(CC) ${CFLAGS} -m32 -shared -fPIC -o ./x86/${APP_NAME} smashlib_x86.o -lc

amd64:
	$(MKDIR_P) ./amd64
	$(CC) ${CFLAGS} -m64 -c -fPIC -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" smashlib.c -o smashlib_amd64.o
	$(CC) ${CFLAGS} -m64 -shared -fPIC -o ./amd64/${APP_NAME} smashlib_amd64.o -lc

clean:
	rm -rf \
		smashlib_amd64.o \
		smashlib_x86.o \
		./x86 \
		./amd64 
		
install: x86 amd64
	install ./x86/${APP_NAME} ../../src/main/resources/native/linux/x86/
	install ./amd64/${APP_NAME} ../../src/main/resources/native/linux/amd64/

uninstall: 
	rm ../../src/main/resources/native/linux/x86/${APP_NAME}
	rm ../../src/main/resources/native/linux/amd64/${APP_NAME}
