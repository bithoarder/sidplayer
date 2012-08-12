package net.bitheap.sidplayer;

import net.bitheap.sidplayer.downloader.HVSCDownloadActivity;

import com.google.android.vending.expansion.downloader.Helpers;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity 
{
  private static String MODULE = "MainActivity";

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    
    Log.v(MODULE, "action="+getIntent().getAction());
    
    // Check if expansion files are available before going any further
    String apkexpPath = Helpers.getExpansionAPKFileName(this, true, 4);
    Log.v(MODULE, "apk expansion path "+apkexpPath);

    if(!Helpers.doesFileExist(this, apkexpPath, 52619714, false))
    {
      startActivity(new Intent(this, HVSCDownloadActivity.class));
    }
    else
    {
      startActivity(new Intent(this, SidListActivity.class));
    }

    finish();
    return;
  }
}
