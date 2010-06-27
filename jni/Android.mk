LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE	:= SidPlayerJNI
LOCAL_SRC_FILES	:= SidPlayerJNI.cpp
LOCAL_CPPFLAGS	+= -I$(LOCAL_PATH)/external/include 
LOCAL_CPPFLAGS	+= -I$(LOCAL_PATH)/external/include/sidplay
LOCAL_CPPFLAGS	+= -I$(LOCAL_PATH)/external/include/sidplay/headers
LOCAL_LDLIBS	+= -L$(LOCAL_PATH)/external/lib/sidplay/builders -lresid-builder
LOCAL_LDLIBS	+= -L$(LOCAL_PATH)/external/lib -lsidplay2 
LOCAL_LDLIBS	+= -L$(LOCAL_PATH)/external/lib -lresid
LOCAL_LDLIBS	+= -lstdc++ -llog 

include $(BUILD_SHARED_LIBRARY)
