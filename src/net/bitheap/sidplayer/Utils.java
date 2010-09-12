package net.bitheap.sidplayer;

import java.io.IOException;
import java.io.InputStream;

import android.content.res.Resources;

public class Utils 
{
  static public byte[] readRawResource(Resources res, int resourceId)
  {
    try 
    {
      InputStream stream =  res.openRawResource(resourceId);
      byte[] buffer = new byte[stream.available()];
      stream.read(buffer);
      stream.close();
      return buffer;
    }
    catch(IOException e) 
    {
        e.printStackTrace();
    }
    return null;
  }
  
  static public String readHtmlResource(Resources res, int resourceId)
  {
    byte[] buffer = readRawResource(res, resourceId);
    if(buffer == null) return "";
    return new String(buffer).replace("\n", "  ");
  }
}
