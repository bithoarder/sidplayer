package net.bitheap.sidplayer;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import net.bitheap.sidplayer.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.text.Layout.Alignment;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

public class PlayerActivity extends Activity implements OnClickListener 
{
  private static String MODULE = "PlayerActivity";
  
  private TextView m_titleTextView;
  private TextView m_authorTextView;
  private TextView m_releaseTextView;
  private TextView m_SongNumberTextView;
  private ImageButton m_playPauseButton;
  private ImageButton m_nextTuneButton;
  private ImageButton m_prevTuneButton;
  private ImageButton m_nextSongButton;
  private ImageButton m_prevSongButton;

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

  private GoogleAnalyticsTracker m_tracker;
  
  
  @Override
  public void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
    Log.v(MODULE, "action="+getIntent().getAction());

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.player);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    
    m_titleTextView = (TextView)findViewById(R.id.title_text);
    m_authorTextView = (TextView)findViewById(R.id.author_text);
    m_releaseTextView = (TextView)findViewById(R.id.release_text);
    m_SongNumberTextView = (TextView)findViewById(R.id.song_number_text);
    m_playPauseButton = (ImageButton)findViewById(R.id.play_pause_button);
    m_nextTuneButton = (ImageButton)findViewById(R.id.next_tune_button);
    m_prevTuneButton = (ImageButton)findViewById(R.id.prev_tune_button);
    m_nextSongButton = (ImageButton)findViewById(R.id.next_song_button);
    m_prevSongButton = (ImageButton)findViewById(R.id.prev_song_button);

    m_playPauseButton.setOnClickListener(this);
    m_nextTuneButton.setOnClickListener(this);
    m_prevTuneButton.setOnClickListener(this);
    m_nextSongButton.setOnClickListener(this);
    m_prevSongButton.setOnClickListener(this);

    updateSongInfo();
  }
  
  @Override
  protected void onStart()
  {
    super.onStart();

    m_tracker = GoogleAnalyticsTracker.getInstance();
    m_tracker.start("UA-18467147-1", this);
    m_tracker.setProductVersion("ver1", "ver2");
    m_tracker.trackPageView("/PlayerActivity");
    m_tracker.dispatch();

    bindService(new Intent(this, SidPlayerService.class), m_serviceConnection, 0);
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    
    m_tracker.trackPageView("/Unknown");
    m_tracker.dispatch();
    m_tracker.stop();
    m_tracker = null;
    
    if(m_serviceConnection != null)
    {
      unbindService(m_serviceConnection);
    }
  }
  
  //@Override
  public void onClick(View v)
  {
    if(m_service == null) return;

    try
    {
      switch(v.getId())
      {
        case R.id.play_pause_button:
          if(m_service.isPlaying()) m_service.pause();
          else m_service.play();
          break;
        case R.id.next_song_button:
          m_service.setSong(m_service.getCurrentSong()+1);
          break;
        case R.id.prev_song_button:
          m_service.setSong(m_service.getCurrentSong()-1);
          break;
        case R.id.next_tune_button:
          m_service.playPlaylistIndex(m_service.getCurrentPlaylistIndex()+1);
          break;
        case R.id.prev_tune_button:
          m_service.playPlaylistIndex(m_service.getCurrentPlaylistIndex()-1);
          break;
      }
    }
    catch(RemoteException e)
    {
      Log.v(MODULE, "failed to call service"+e);
      finish();
    }

    updateSongInfo();
  }
  
  
  private void updateSongInfo()
  {
    m_playPauseButton.setEnabled(false);
    m_nextTuneButton.setEnabled(false);
    m_prevTuneButton.setEnabled(false);
    m_nextSongButton.setEnabled(false);
    m_prevSongButton.setEnabled(false);
    
    if(m_service == null)
    {
      m_SongNumberTextView.setText("?/?");
    }
    else
    {
      try
      {
        SpannableStringBuilder titleBuilder = new SpannableStringBuilder(m_service.getInfoString(0));
        titleBuilder.setSpan(new AlignmentSpan.Standard(Alignment.ALIGN_CENTER), 0, titleBuilder.length(), 0);
        m_titleTextView.setText(titleBuilder, TextView.BufferType.SPANNABLE);

        SpannableStringBuilder authorBuilder = new SpannableStringBuilder(m_service.getInfoString(1));
        authorBuilder.setSpan(new AlignmentSpan.Standard(Alignment.ALIGN_CENTER), 0, authorBuilder.length(), 0);
        m_authorTextView.setText(authorBuilder, TextView.BufferType.SPANNABLE);

        SpannableStringBuilder releaseBuilder = new SpannableStringBuilder(m_service.getInfoString(2));
        releaseBuilder.setSpan(new AlignmentSpan.Standard(Alignment.ALIGN_CENTER), 0, releaseBuilder.length(), 0);
        m_releaseTextView.setText(releaseBuilder, TextView.BufferType.SPANNABLE);

        int currentSong = m_service.getCurrentSong();
        int songCount = m_service.getSongCount();
        
        m_SongNumberTextView.setText(String.format("%d/%d", currentSong, songCount));
        m_playPauseButton.setImageResource(m_service.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        m_playPauseButton.setEnabled(true);
        if(songCount>1)
        {
          if(currentSong<songCount) m_nextSongButton.setEnabled(true);
          if(currentSong>1) m_prevSongButton.setEnabled(true);
        }
        
        int playlistLength = m_service.getPlaylistLength();
        if(playlistLength > 1)
        {
          int currentIndex = m_service.getCurrentPlaylistIndex();
          if(currentIndex < playlistLength-1) m_nextTuneButton.setEnabled(true);
          if(currentIndex > 0) m_prevTuneButton.setEnabled(true);
        }

      }
      catch(RemoteException e)
      {
        Log.v(MODULE, "failed to call service"+e);
        finish();
      }
    }
  }
}
