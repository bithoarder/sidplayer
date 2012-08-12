package net.bitheap.sidplayer;

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

    String html = Utils.readHtmlResource(getResources(), R.raw.about);
    
    WebView webview = new WebView(this);
    setContentView(webview);
    webview.loadData(html, "text/html", "utf-8");
  }
}
