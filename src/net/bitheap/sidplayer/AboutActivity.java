package net.bitheap.sidplayer;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.webkit.WebView;

public class AboutActivity extends Activity
{
  @Override
  public void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);

    String html = readHtml();
    
    WebView webview = new WebView(this);
    setContentView(webview);
    webview.loadData(html, "text/html", "utf-8");
  }

  private String readHtml()
  {
    String html = "";
    try 
    {
        InputStream stream = getAssets().open("about.html");
        byte[] buffer = new byte[stream.available()];
        stream.read(buffer);
        stream.close();
        html = new String(buffer).replace("\n", "  ");
    } 
    catch(IOException e) 
    {
        e.printStackTrace();
    }
    return html;
  }
}
