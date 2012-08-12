#include "stilpak/stilpak.h"

#include <android/log.h>
#include <jni.h>

///////////////////////////////////////////////////////////////////////////////

#define DEBUG(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, "sid", fmt, ##args)
#define ASSERT(cond) ((cond)?(void)0:__android_log_assert("##cond", "sid", ""))

enum
{
  SID_PATH = 100,
  SID_NAME = 110,
  SID_AUTHOR = 111,
  SID_RELEASED = 112,
  SID_STIL_NAME = 120,
  SID_STIL_TITLE = 121,
  SID_STIL_ARTIST = 122,
  SID_STIL_COMMENT = 123,
};

///////////////////////////////////////////////////////////////////////////////

extern "C"
JNIEXPORT jlong Java_net_bitheap_sidplayer_HvscPak_jniInit(JNIEnv *env, jobject thiz, jstring path)
{
  StilPak *pak = NULL;

  const char *cpath = env->GetStringUTFChars(path, NULL);
  ASSERT(cpath != NULL);

  pak = new StilPak;
  ASSERT(pak != NULL);

  DEBUG("reading %s", cpath);
  if(!pak->readPak(cpath))
  {
    DEBUG("failed to read %s", cpath);
    delete pak;
    pak = NULL;
  }
  else
  {
  }

  env->ReleaseStringUTFChars(path, cpath);

  return (jlong)pak;
}

extern "C"
JNIEXPORT void Java_net_bitheap_sidplayer_HvscPak_jniRelease(JNIEnv *env, jobject thiz, jlong handle)
{
  StilPak *pak = reinterpret_cast<StilPak*>(handle);
  delete pak;
}

extern "C"
JNIEXPORT jint Java_net_bitheap_sidplayer_HvscPak_jniGetSidCount(JNIEnv *env, jobject thiz, jlong handle)
{
  StilPak *pak = reinterpret_cast<StilPak*>(handle);
  return pak->getSidCount();
}

extern "C"
JNIEXPORT jbyteArray Java_net_bitheap_sidplayer_HvscPak_jniGetSidString(JNIEnv *env, jobject thiz, jlong handle, jint sidIndex, jint stringId)
{
  StilPak *pak = reinterpret_cast<StilPak*>(handle);
  jbyteArray array = NULL;
  if(stringId == SID_PATH)
  {
    std::string path = pak->getSidFilepath(sidIndex);
    array = env->NewByteArray(path.size());
    env->SetByteArrayRegion(array, 0, env->GetArrayLength(array), (const jbyte*)path.data());
  }
  else
  {
    const char *str = "";
    switch(stringId)
    {
    case SID_NAME: str = pak->getSidSidName(sidIndex); break;
    case SID_AUTHOR: str = pak->getSidSidAuthor(sidIndex); break;
    case SID_RELEASED: str = pak->getSidSidReleased(sidIndex); break;

    case SID_STIL_NAME: str = pak->getSidStilName(sidIndex); break;
    case SID_STIL_TITLE: str = pak->getSidStilTitle(sidIndex); break;
    case SID_STIL_ARTIST: str = pak->getSidStilArtist(sidIndex); break;
    case SID_STIL_COMMENT: str = pak->getSidStilComment(sidIndex); break;
    }
    array = env->NewByteArray(strlen(str));
    env->SetByteArrayRegion(array, 0, env->GetArrayLength(array), (const jbyte*)str);
  }

  return array;
}

extern "C"
JNIEXPORT jbyteArray Java_net_bitheap_sidplayer_HvscPak_jniGetSidData(JNIEnv *env, jobject thiz, jlong handle, jint sidIndex)
{
  StilPak *pak = reinterpret_cast<StilPak*>(handle);
  std::vector<uint8_t> data = pak->getSidData(sidIndex);

  jbyteArray array = env->NewByteArray(data.size());
  env->SetByteArrayRegion(array, 0, env->GetArrayLength(array), (const jbyte*)&data[0]);
  return array;
}

extern "C"
JNIEXPORT jintArray Java_net_bitheap_sidplayer_HvscPak_jniFindSids(JNIEnv *env, jobject thiz, jlong handle, jstring searchString)
{
  StilPak *pak = reinterpret_cast<StilPak*>(handle);

  jclass stringCls = env->GetObjectClass(searchString);
  ASSERT(stringCls != NULL);

  jmethodID getBytesID = env->GetMethodID(stringCls, "getBytes", "(Ljava/lang/String;)[B");
  ASSERT(getBytesID != NULL);

  jstring codepageString = env->NewStringUTF("ISO-8859-1");
  ASSERT(codepageString != NULL);

  jbyteArray encodedStringArray = (jbyteArray)env->CallObjectMethod(searchString, getBytesID, codepageString);
  ASSERT(encodedStringArray != NULL);

  env->DeleteLocalRef(codepageString);

  // encodedStringArray is not nul terminated, so move to new buffer
  std::string encodedString;
  encodedString.resize(env->GetArrayLength(encodedStringArray));
  env->GetByteArrayRegion(encodedStringArray, 0, encodedString.size(), (jbyte*)&encodedString[0]);

  std::vector<uint> results = pak->findSids(encodedString.c_str());

  jintArray resultsArray = env->NewIntArray(results.size());
  env->SetIntArrayRegion(resultsArray, 0, results.size(), (int*)results.data());

  return resultsArray;
}

extern "C"
JNIEXPORT jint Java_net_bitheap_sidplayer_HvscPak_jniGetSongCount(JNIEnv *env, jobject thiz, jlong handle, jint sidIndex)
{
  StilPak *pak = reinterpret_cast<StilPak*>(handle);
  return pak->getSongCount(sidIndex);
}

extern "C"
JNIEXPORT jint Java_net_bitheap_sidplayer_HvscPak_jniGetSongDuration(JNIEnv *env, jobject thiz, jlong handle, jint sidIndex, jint songIndex)
{
  StilPak *pak = reinterpret_cast<StilPak*>(handle);
  return pak->getSongDuration(sidIndex, songIndex);
}

