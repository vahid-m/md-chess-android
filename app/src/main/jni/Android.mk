LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := nativeutil
LOCAL_SRC_FILES := nativeutil.cpp

include $(BUILD_SHARED_LIBRARY)

ZPATH := $(LOCAL_PATH)

include $(ZPATH)/stockfish/Android.mk

include $(ZPATH)/gtb/Android.mk

include $(ZPATH)/rtb/Android.mk
