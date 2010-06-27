package net.bitheap.sidplayer;

import java.io.UnsupportedEncodingException;

import android.util.Log;

public class SidPlayer
{
  static 
  {
    System.loadLibrary("SidPlayerJNI");
  }

  private long m_nativePlayer;
  private short[] m_audioData;
  private int m_silentFrameCount;

  public SidPlayer(byte[] sid, int audioBufferLength)
  {
    m_audioData = new short[audioBufferLength];
    m_silentFrameCount = 0;
    m_nativePlayer = jniInit(sid);
    Log.v("sid", "native address:"+m_nativePlayer);
  }
  
  protected void finalize() throws Throwable 
  {
    if(m_nativePlayer != 0)
    {
      jniRelease(m_nativePlayer);
    }
    m_nativePlayer = 0;
  }
  
  public short[] getAudioData()
  {
    if(m_nativePlayer != 0)
    {
      if(jniPlay(m_nativePlayer, m_audioData)) m_silentFrameCount += 1;
      else m_silentFrameCount = 0;
      return m_audioData;
    }
    return null;
  }
  
  public int getSongCount()
  {
    return jniGetSongCount(m_nativePlayer);
  }

  public int getCurrentSong()
  {
    return jniGetCurrentSong(m_nativePlayer);
  }

  public int setSong(int song)
  {
    m_silentFrameCount = 0;
    return jniSetSong(m_nativePlayer, song);
  }
  
  public String getInfoString(int stringIndex)
  {
    byte[] data = jniGetInfoString(m_nativePlayer, stringIndex); 
    try
    {
      return new String(data, 0, data.length, "ISO-8859-1");
    }
    catch(UnsupportedEncodingException e)
    {
      return "<?>";
    }
  }
  
  public int getSilentFrameCount()
  {
    return m_silentFrameCount;
  }
  

  native private long jniInit(byte[] data);
  native private void jniRelease(long handle);
  native private boolean jniPlay(long handle, short[] audiodata);
  native private int jniGetSongCount(long handle);
  native private int jniGetCurrentSong(long handle);
  native private int jniSetSong(long handle, int song);
  native private byte[] jniGetInfoString(long handle, int stringindex);
}
