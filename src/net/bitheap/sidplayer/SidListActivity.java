package net.bitheap.sidplayer;

import net.bitheap.sidplayer.R;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;


public class SidListActivity extends Activity 
{
  private static String MODULE = "SidListActivity";
  
  private Cursor m_sidCursor;

  private View m_nowplayingView;
  private ImageButton m_playPauseButton;
  private TextView m_titleTextView;
  private TextView m_authorTextView;

  private ISidPlayerService m_service;
  private ServiceConnection m_serviceConnection = new ServiceConnection()
  {
    //@Override
    public void onServiceConnected(ComponentName name, IBinder service)
    {
      Log.v(MODULE, "onServiceConnected");
      m_service = ISidPlayerService.Stub.asInterface(service);
      updateSongInfo();
    }

    //@Override
    public void onServiceDisconnected(ComponentName name)
    {
      Log.v(MODULE, "onServiceDisconnected");
      m_service = null;
      updateSongInfo();
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) 
  {
    Log.v(MODULE, "action="+getIntent().getAction());

    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    
    m_nowplayingView = findViewById(R.id.nowplaying);
    m_playPauseButton = (ImageButton)findViewById(R.id.play_pause_button);
    m_titleTextView = (TextView)findViewById(R.id.title_text);
    m_authorTextView = (TextView)findViewById(R.id.author_text);
    
    final ContentResolver cr = getContentResolver();
    if(getIntent().getAction().equals(Intent.ACTION_SEARCH))
    {
      String query = getIntent().getStringExtra(SearchManager.QUERY);
      Log.v(MODULE, "query="+query);
      setTitle("SID Player : " + query);
      m_sidCursor = cr.query(SidZipContentProvider.CONTENT_URI, null, query, null, null);
    }
    else
    {
      setTitle("SID Player");
      m_sidCursor = cr.query(SidZipContentProvider.CONTENT_URI, null, null, null, null);
    }
    
    ListView listView = (ListView)findViewById(R.id.ListView01);

    int layoutID = android.R.layout.simple_list_item_2;
    SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, layoutID, m_sidCursor, new String[]{"name", "author"}, 
        new int[]{android.R.id.text1, android.R.id.text2} );
    
    listView.setAdapter(adapter);
    
    listView.setOnItemClickListener(new OnItemClickListener()
    {
      //@Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
      {
        Bundle request = new Bundle();
        request.putString("cmd", "allids");
        Bundle reply = m_sidCursor.respond(request);
        
        int[] playlist = reply.getIntArray("ids");
        try
        {
          m_service.setPlaylist(playlist);
          m_service.playPlaylistIndex(arg2);
        }
        catch(RemoteException e)
        {
        }

        updateSongInfo();
      }
    });
    
    findViewById(R.id.play_pause_button).setOnClickListener(new OnClickListener()
    {
      //@Override
      public void onClick(View v)
      {
        try
        {
          if(m_service.isPlaying()) m_service.pause();
          else m_service.play();
        }
        catch(RemoteException e)
        {
        }
        updateSongInfo();
      }
    });

    findViewById(R.id.player_layout).setOnClickListener(new OnClickListener()
    {
      //@Override
      public void onClick(View v)
      {
        Intent intent = new Intent(SidListActivity.this, PlayerActivity.class);
        startActivity(intent);
      }
    });
  }

  @Override
  protected void onStart()
  {
    super.onStart();
    
    Intent playSidIntent = new Intent(this, SidPlayerService.class);
    startService(playSidIntent);
    
    bindService(new Intent(this, SidPlayerService.class), m_serviceConnection, 0);
    updateSongInfo();
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    
    if(m_serviceConnection != null)
    {
      unbindService(m_serviceConnection);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) 
  {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch(item.getItemId()) 
    {
      case R.id.menu_about:
      {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
        return true;
      }
      case R.id.menu_hsvc:
      {
        Intent intent = new Intent(this, HSVCInstallationActivity.class);
        startActivity(intent);
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  private void updateSongInfo()
  {
    try
    {
      if(m_service==null || m_service.getSongCount()==0)
      {
        m_nowplayingView.setVisibility(View.GONE);
      }
      else
      {
        m_nowplayingView.setVisibility(View.VISIBLE);

        m_playPauseButton.setImageResource(m_service.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        m_titleTextView.setText(m_service.getInfoString(0));
        m_authorTextView.setText(m_service.getInfoString(1));
      }
    }
    catch(RemoteException e)
    {
      //Log.v(MODULE, "failed to call service"+e);
      finish();
    }
  }
}
