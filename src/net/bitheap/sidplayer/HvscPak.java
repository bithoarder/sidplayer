package net.bitheap.sidplayer;

import java.io.UnsupportedEncodingException;

import android.util.Log;

public class HvscPak 
{
  static 
  {
    Log.v("sidplayer", "pre loadLibrary");
    System.loadLibrary("SidPlayerJNI");
    Log.v("sidplayer", "post loadLibrary");
  }
  
  private static int SID_PATH = 100;
  private static int SID_NAME = 110;
  private static int SID_AUTHOR = 111;
  private static int SID_RELEASED = 112;
  private static int SID_STIL_NAME = 120; // Name of the sid.
  private static int SID_STIL_TITLE = 121; // This field is used to denote the title of the sid that a given SID covers.
  private static int SID_STIL_ARTIST = 122; // This field is used to denote the artist of the sid that a given SID covers.
  private static int SID_STIL_COMMENT = 123;

  private long m_nativeObj;
  
  public HvscPak(String pakFilename)
  {
    m_nativeObj = jniInit(pakFilename);
    Log.v("sidplayer", "native hvscpak handle:"+m_nativeObj);
  }

  protected void finalize() throws Throwable
  {
    if(m_nativeObj != 0)
    {
      jniRelease(m_nativeObj);
    }
    m_nativeObj = 0;
  }

  public int getSidCount()
  {
    return jniGetSidCount(m_nativeObj);
  }

  public String getSidFilepath(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_PATH)); }
  public String getSidName(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_NAME)); }
  public String getSidAuthor(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_AUTHOR)); }
  public String getSidReleased(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_RELEASED)); }

  public String getSidStilName(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_STIL_NAME)); }
  public String getSidStilTitle(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_STIL_TITLE)); }
  public String getSidStilArtist(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_STIL_ARTIST)); }
  public String getSidStilComment(int sidIndex) { return convertString(jniGetSidString(m_nativeObj, sidIndex, SID_STIL_COMMENT)); }

  public int[] findSids(String searchString)
  {
    return jniFindSids(m_nativeObj, searchString);
  }
  
  public byte[] getSidData(int sidIndex)
  {
    return jniGetSidData(m_nativeObj, sidIndex);
  }

  public int getSongCount(int sidIndex)
  {
    return jniGetSongCount(m_nativeObj, sidIndex);
  }

  public int getSongDuration(int sidIndex, int songIndex)
  {
    return jniGetSongDuration(m_nativeObj, sidIndex, songIndex);
  }

  private String convertString(byte[] data)
  {
    try
    {
      return new String(data, 0, data.length, "ISO-8859-1");
    }
    catch(UnsupportedEncodingException e)
    {
      return "";
    }
  }
  
  native private long   jniInit(String pakFilename);
  native private void   jniRelease(long handle);
  native private int    jniGetSidCount(long handle);
  native private byte[] jniGetSidString(long handle, int sidIndex, int stringId);
  native private int[]  jniFindSids(long handle, String searchString);
  native private byte[] jniGetSidData(long handle, int sidIndex);
  native private int    jniGetSongCount(long handle, int sidIndex);
  native private int    jniGetSongDuration(long handle, int sidIndex, int songIndex);
}
