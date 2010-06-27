#!/bin/bash
set -e

NDK=$HOME/opt/android-ndk
PATH=$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/bin:$PATH
PATH=$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/libexec/gcc/arm-eabi/4.4.0:$PATH

# g++ hack.cpp -c -ohack.o ; ar r libhack.a hack.o

#-march=armv6 - crashes on emu?
#CFLAGS="-nostdlib -fno-short-enums -marm -fomit-frame-pointer -O3 -finline-limit=64 -fno-rtti -fno-exceptions"
CFLAGS="-nostdlib -fno-short-enums -marm -mcpu=arm9 -msoft-float -fomit-frame-pointer -O3 -fno-rtti -fno-exceptions"
CFLAGS="$CFLAGS -I$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/lib/gcc/arm-eabi/4.4.0/include-fixed"
CFLAGS="$CFLAGS -I$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/lib/gcc/arm-eabi/4.4.0/include"
CFLAGS="$CFLAGS -I$NDK/build/platforms/android-5/arch-arm/usr/include"
CFLAGS="$CFLAGS -I$PWD/include"
CPPFLAGS="$CFLAGS -Dstrnicmp=strncasecmp -Dstricmp=strcasecmp"
CPPFLAGS="$CPPFLAGS -I$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/arm-eabi/include/c++/4.4.0"
CPPFLAGS="$CPPFLAGS -I$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/arm-eabi/include/c++/4.4.0/arm-eabi"
LIBS="-L$PWD/lib"
LIBS="$LIBS -L$NDK/build/platforms/android-5/arch-arm/usr/lib"
LIBS="$LIBS -L$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/arm-eabi/lib"
LIBS="$LIBS -L$NDK/build/prebuilt/linux-x86/arm-eabi-4.4.0/lib/gcc/arm-eabi/4.4.0"
LIBS="$LIBS -lc -ldl -lstdc++ -lsupc++ -lgcc -lgcov"
CC=arm-eabi-gcc


if true ; then
rm -rfv libsidplay-2.1.1+20060528.ccr
tar xvfz libsidplay-2.1.1+20060528.ccr.tar.gz
cd libsidplay-2.1.1+20060528.ccr
./configure \
  --host=arm-eabi \
  --prefix=$PWD/.. \
  --disable-shared \
  --enable-static \
  CC=$CC \
  CFLAGS="$CFLAGS" \
  CPPFLAGS="$CPPFLAGS"
make -j install
cd ..
fi

#cp patches/sidconfig.h include/sidplay
#cp patches/sidint.h include/sidplay

if true ; then
rm -rfv resid-0.16.2-ccr
tar xvfz resid-0.16.2-ccr.tar.gz
cd resid-0.16.2-ccr
./configure \
  --host=arm-eabi \
  --prefix=$PWD/../libs \
  --disable-shared \
  --enable-static \
  CC=$CC \
  CFLAGS="$CFLAGS" \
  CPPFLAGS="$CPPFLAGS"
make -j
cd ..
cp resid-0.16.2-ccr/.libs/libresidc.a lib/libresid.a
#cp resid-0.16.2-ccr/libresid.la lib
#mkdir -p include/resid
cp resid-0.16.2-ccr/{sid.h,siddefs.h,voice.h,filter.h,extfilt.h,pot.h,wave.h,envelope.h,spline.h} include
fi

rm -rfv resid-builder-1.0.1+20060528.ccr
tar xvfz resid-builder-1.0.1+20060528.ccr.tar.gz
cd resid-builder-1.0.1+20060528.ccr
#echo "#include <string.h>" >>include/config.h
./configure \
  --host=arm-eabi \
  --prefix=$PWD/.. \
  --disable-shared \
  --enable-static \
  CC=$CC \
  CFLAGS="$CFLAGS" \
  CPPFLAGS="$CPPFLAGS -include string.h" \
  LIBS="$LIBS"
make clean
make -j install
cd ..

