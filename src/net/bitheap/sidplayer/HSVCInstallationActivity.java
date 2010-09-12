package net.bitheap.sidplayer;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.webkit.WebView;

public class HSVCInstallationActivity extends Activity
{
  @Override
  public void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);

    String html = Utils.readHtmlResource(getResources(), R.raw.hvsc);
    
    WebView webview = new WebView(this);
    setContentView(webview);
    webview.loadData(html, "text/html", "utf-8");
  }
}
