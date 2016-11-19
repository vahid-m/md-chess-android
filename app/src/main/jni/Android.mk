 LOCAL_PATH := $(call my-dir)
 MY_PATH := $(LOCAL_PATH)
 include $(call all-subdir-makefiles)

 include $(CLEAR_VARS)

LOCAL_PATH := $(MY_PATH)

LOCAL_MODULE    := nativeutil
LOCAL_SRC_FILES := nativeutil.cpp

include $(BUILD_SHARED_LIBRARY)