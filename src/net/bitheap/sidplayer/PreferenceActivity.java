package net.bitheap.sidplayer;

import android.os.Bundle;

public class PreferenceActivity extends android.preference.PreferenceActivity 
{
  @Override
  public void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
  }
}
