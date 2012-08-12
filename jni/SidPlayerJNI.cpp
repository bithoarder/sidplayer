#include "SidTune.h"
#include "sidplay2.h"
#include "builders/resid.h"

#include <cstdint>
#include <cstdlib>

#include <jni.h>
#include <android/log.h>

double mixer_value1 = 1.0;
double mixer_value2 = 1.0;
double mixer_value3 = 1.0;
unsigned char sid_registers[ 0x19 ];

struct Player
{
  void *siddata;
  SidTune *sidtune;
  ReSIDBuilder *sidbuilder;
  sidplay2 *sidemu; // engine
};

#define DEBUG(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, "sid", fmt, ##args)
#define ASSERT(cond) ((cond)?(void)0:__android_log_assert("##cond", "sid", ""))

extern "C"
JNIEXPORT jlong Java_net_bitheap_sidplayer_SidPlayer_jniInit(JNIEnv *env, jobject thiz, jbyteArray siddata)
{
  Player *player = (Player*)malloc(sizeof(Player));
  ASSERT(player != NULL);

  jsize size = env->GetArrayLength(siddata);
  jbyte *bytes = env->GetByteArrayElements(siddata, NULL);
  DEBUG("data:%p size:%u", bytes, (int)size);
  player->siddata = malloc(size);
  ASSERT(player->siddata != NULL);
  memcpy(player->siddata, bytes, size);
  env->ReleaseByteArrayElements(siddata, bytes, NULL);

  player->sidtune = new SidTune((uint_least8_t*)player->siddata, size);
  ASSERT(player->sidtune != NULL);
  if(!player->sidtune->getStatus())
  {
    DEBUG("failed to load sidtune");
    delete player->sidtune;
    free(player);
    return 0;
  }

  DEBUG("loaded sidtune!!!");

  const SidTuneInfo &info = player->sidtune->getInfo();
  player->sidtune->selectSong(0);//info.startSong);

  DEBUG("formatString:    \"%s\"\n", info.formatString);
  DEBUG("statusString:    \"%s\"\n", info.statusString);
  DEBUG("speedString:     \"%s\"\n", info.speedString);
  DEBUG("loadAddr:        0x%04x\n", info.loadAddr);
  DEBUG("initAddr:        0x%04x\n", info.initAddr);
  DEBUG("playAddr:        0x%04x\n", info.playAddr);
  DEBUG("songs:           %d\n", info.songs);
  DEBUG("startSong:       %d\n", info.startSong);
  DEBUG("sidChipBase1:    0x%04x\n", info.sidChipBase1);
  DEBUG("sidChipBase2:    0x%04x\n", info.sidChipBase2);
  DEBUG("currentSong:     %d\n", info.currentSong);
  DEBUG("songSpeed:       %d\n", info.songSpeed);
  DEBUG("clockSpeed:      %d\n", info.clockSpeed);
  DEBUG("relocStartPage:  %d\n", info.relocStartPage);
  DEBUG("relocPages:      %d\n", info.relocPages);
  DEBUG("musPlayer:       %d\n", info.musPlayer);
  DEBUG("sidModel:        %d\n", info.sidModel);
  DEBUG("compatibility:   %d\n", info.compatibility);
  DEBUG("fixLoad:         %d\n", info.fixLoad);
  DEBUG("songLength:      %d\n", info.songLength);
  for(uint i=0; i<info.numberOfInfoStrings; i++)
    DEBUG("infoString %d:    \"%s\"\n", i, info.infoString[i]);
  for(uint i=0; i<info.numberOfCommentStrings; i++)
    DEBUG("commentString %d: \"%s\"\n", i, info.commentString[i]);
  DEBUG("dataFileLen:     %d\n", info.dataFileLen);
  DEBUG("c64dataLen:      %d\n", info.c64dataLen);
  DEBUG("path:            \"%s\"\n", info.path);
  DEBUG("dataFileName:    \"%s\"\n", info.dataFileName);
  DEBUG("infoFileName:    \"%s\"\n", info.infoFileName);

  player->sidemu = new sidplay2;
  ASSERT(player->sidemu != NULL);

  sid2_config_t cfg = player->sidemu->config();
  cfg.clockForced  = true;
  cfg.environment  = sid2_envR;
  cfg.forceDualSids= false;
  cfg.emulateStereo= false;

//x  cfg.clockSpeed   = SID2_CLOCK_CORRECT;
//x  cfg.clockDefault = SID2_CLOCK_PAL;
  cfg.frequency    = 44100;

  cfg.playback     = sid2_mono;

  cfg.precision    = 16;
  //cfg.sidModel     = SID2_MODEL_CORRECT;
  cfg.sidDefault   = SID2_MOS6581;
  cfg.sidSamples   = true;
  cfg.optimisation  = SID2_MAX_OPTIMISATION;

  player->sidbuilder = new ReSIDBuilder("ReSID");
  ASSERT(player->sidbuilder != NULL);
  cfg.sidEmulation  = player->sidbuilder;

  player->sidbuilder->create(1);
  player->sidbuilder->filter(false);
  //player->sidbuilder->filter((void*)0);

  int rc = player->sidemu->load(player->sidtune);
  ASSERT(rc == 0);

  player->sidemu->config(cfg);

  return (jlong)player;
}

extern "C"
JNIEXPORT void Java_net_bitheap_sidplayer_SidPlayer_jniRelease(JNIEnv *env, jobject thiz, jlong handle)
{
  DEBUG("release");

  Player *player = (Player*)handle;

  delete player->sidemu;
  delete player->sidbuilder;
  delete player->sidtune;
  free(player->siddata);
  free(player);
}

extern "C"
JNIEXPORT jboolean Java_net_bitheap_sidplayer_SidPlayer_jniPlay(JNIEnv *env, jobject thiz, jlong handle, jshortArray audiodata)
{
  Player *player = (Player*)handle;

  jsize size = env->GetArrayLength(audiodata);
  jshort *data = env->GetShortArrayElements(audiodata, NULL);
  //DEBUG("audio data:%p size:%u", bytes, (int)size);

  player->sidemu->play(data, size*2);

  // detect silence (
  int peak = 0;
  for(int i=1; i<size/2; i++)
  {
    int delta = abs(data[i+1]-data[i]);
    if(delta > peak) peak = delta;
  }

  env->ReleaseShortArrayElements(audiodata, data, NULL);

  return peak < 100; // return true if silent (or close to it)
}

extern "C"
JNIEXPORT jint Java_net_bitheap_sidplayer_SidPlayer_jniGetSongCount(JNIEnv *env, jobject thiz, jlong handle)
{
  Player *player = (Player*)handle;
  const SidTuneInfo &info = player->sidtune->getInfo();
  return info.songs;
}

extern "C"
JNIEXPORT jint Java_net_bitheap_sidplayer_SidPlayer_jniGetCurrentSong(JNIEnv *env, jobject thiz, jlong handle)
{
  Player *player = (Player*)handle;
  const SidTuneInfo &info = player->sidtune->getInfo();
  return info.currentSong;
}

extern "C"
JNIEXPORT void Java_net_bitheap_sidplayer_SidPlayer_jniSetSong(JNIEnv *env, jobject thiz, jlong handle, jint song)
{
  Player *player = (Player*)handle;
  const SidTuneInfo &info = player->sidtune->getInfo();
  if(song>=1 && song<=info.songs)
  {
	  player->sidtune->selectSong(song);
	  player->sidemu->load(player->sidtune);
  }
}

extern "C"
JNIEXPORT jbyteArray Java_net_bitheap_sidplayer_SidPlayer_jniGetInfoString(JNIEnv *env, jobject thiz, jlong handle, jint stringindex)
{
  Player *player = (Player*)handle;
  const SidTuneInfo &info = player->sidtune->getInfo();
  const char *string = stringindex>=0 && stringindex<info.numberOfInfoStrings ? info.infoString[stringindex] : "<?>";

  //return env->NewStringUTF(string);

  jbyteArray array = env->NewByteArray(strlen(string));
  env->SetByteArrayRegion(array, 0, env->GetArrayLength(array), (const jbyte*)string);
  return array;
}
