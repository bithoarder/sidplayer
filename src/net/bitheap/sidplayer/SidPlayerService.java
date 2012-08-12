package net.bitheap.sidplayer;

import net.bitheap.sidplayer.hvscprovider.HVSCContentProvider;
import net.bitheap.sidplayer.hvscprovider.SongCursor;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SidPlayerService extends Service implements Runnable
{
  public static String PLAY_SID = "PlaySid";

  private static String MODULE = "SidPlayerService";
  private static int NOTIFICATION_ID = 1;
  
  //private volatile AudioTrack m_audio;
  private int m_audioBufferSize;
  private int[] m_playlist;
  private int m_playlistIndex;
  //private volatile SidPlayer m_sidPlayer;
  private volatile Thread m_thread;
  private WakeLock m_wakeLock;

  //private long m_sidPlayingStartedAt;
  private long m_songPlayingStartedAt;
  //private String m_playingName;

  private String m_cachedSidTitle = "<?>";
  private String m_cachedSidAuthor = "<?>";
  private String m_cachedSidCopyright = "<?>";
  private int m_cachedSongDuration = -1;
  private int m_cachedSongCount = 0;
  private int m_cachedCurrentSongIndex = -1;
  
  private boolean m_advanceSongBeforeSid = false;

  private final ISidPlayerService.Stub m_binder = new ISidPlayerService.Stub()
  {
    @Override
    public void setPlaylist(int[] ids) throws RemoteException
    {
      Log.v(MODULE, "got playlist:" + ids.length);
      stopThread();
      synchronized(SidPlayerService.this)
      {
        m_playlist = ids;
        m_playlistIndex = 0;
      }
    }

    @Override
    public void playPlaylistIndex(int playlistIndex) throws RemoteException
    {
      Log.v(MODULE, "play playlist:" + playlistIndex);
      
      stopThread();

      boolean startThread = false;
      if(m_playlist!=null && playlistIndex>=0 && playlistIndex<m_playlist.length)
      {
        m_playlistIndex = playlistIndex;
        startThread = true;
        m_cachedCurrentSongIndex= -1; // play default song in sid
        m_advanceSongBeforeSid = false;
      }

      if(startThread)
      {
        startThread();
      }
    }

    @Override
    public int getPlaylistLength() throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_playlist!=null ? m_playlist.length : 0;
      }
    }
    
    @Override
    public int getCurrentPlaylistIndex() throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_playlistIndex;
      }
    }
    
    @Override
    public String getInfoString(int stringIndex) throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        if(stringIndex == 0) return m_cachedSidTitle;
        if(stringIndex == 1) return m_cachedSidAuthor;
        if(stringIndex == 2) return m_cachedSidCopyright;
        return "<?>";
      }
    }
    
    @Override
    public void play() throws RemoteException
    {
      startThread();
    }

    @Override
    public void pause() throws RemoteException
    {
      stopThread();
    }
    
    @Override
    public boolean isPlaying() throws RemoteException
    {
      return m_thread!=null && m_thread.isAlive();
    }

    @Override
    public int getSongCount() throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_cachedSongCount;
        //return m_sidPlayer==null ? 0 : m_sidPlayer.getSongCount();
      }
    }

    @Override
    public int getCurrentSong() throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_cachedCurrentSongIndex;
        //return m_sidPlayer==null ? 0 : m_sidPlayer.getCurrentSong();
      }
    }

    @Override
    public void setSong(int song) throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        m_cachedCurrentSongIndex = song;
        m_advanceSongBeforeSid = true;
        startThread();
        // fixme!
//        if(m_sidPlayer != null) 
//        {
//          m_sidPlayer.setSong(song);
//          m_songPlayingStartedAt = java.lang.System.currentTimeMillis();
//          updateSongCache();
//          startThread();
//        }
      }
    }

    @Override
    public int getCurrentSongTime() throws RemoteException
    {
      long dt = (java.lang.System.currentTimeMillis() - m_songPlayingStartedAt)/1000;
      return (int)dt;
    }

    @Override
    public int getCurrentSongDuration() throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_cachedSongDuration;
      }
    }
  };
  
  private final PhoneStateListener m_phoneStateListener = new PhoneStateListener()
  {
    public void onCallStateChanged(int state, String incomingNumber)
    {
      switch(state)
      {
      case TelephonyManager.CALL_STATE_RINGING:
      case TelephonyManager.CALL_STATE_OFFHOOK:
        stopThread();
        break;
      }
    }
  };

  private GoogleAnalyticsTracker m_tracker;

  private SharedPreferences m_prefs;

  private Intent m_songTimeUpdateBroadcast;
  private Intent m_sidUpdateBroadcast;

  @Override
  public IBinder onBind(Intent intent)
  {
    Log.d(MODULE, "onBind(intent="+intent+") called");
    return m_binder;
  }

  @Override
  public void onCreate()
  {
    Log.d(MODULE, "OnCreate called");

    m_prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    
    // todo: move this to the application instance 
    m_tracker = GoogleAnalyticsTracker.getInstance();
    m_tracker.startNewSession("UA-18467147-1", this);
    //m_tracker.setProductVersion("ver1", "ver2");
    // nothing is send back to google before .dispatch() is called, so no tracking is happening yet.
    
    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
    
    TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
    tm.listen(m_phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    
    PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
    m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SidPlayerService");

//    int minBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_CONFIGURATION_MONO);
//    Log.d(MODULE, "minBufferSize="+minBufferSize);
//    m_audio = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_CONFIGURATION_MONO , AudioFormat.ENCODING_PCM_16BIT, Math.max(minBufferSize, 32*1024), AudioTrack.MODE_STREAM);
    m_audioBufferSize = 1024;

    m_songTimeUpdateBroadcast = new Intent();
    m_songTimeUpdateBroadcast.setAction("net.bitheap.sidplayer.SONG_TIME_UPDATE");
    m_sidUpdateBroadcast = new Intent();
    m_sidUpdateBroadcast.setAction("net.bitheap.sidplayer.SID_UPDATE");

  }
  
  @Override
  public void onStart(Intent intent, int startId)
  {
    super.onStart(intent, startId);
    Log.d(MODULE, "onStart(intent="+intent+", startId="+startId+") called");
    
    if(intent != null)
    {
      String action = intent.getAction();
      if(action!=null && action.equals(PLAY_SID))
      {
        int sid_index = intent.getIntExtra("sid_index", -1);
        if(sid_index >= 0)
        {
          stopThread();
          synchronized(this)
          {
            m_playlist = new int[]{ sid_index };
            m_playlistIndex = 0;
            m_cachedCurrentSongIndex= -1; // play default song in sid
            m_advanceSongBeforeSid = false;
          }
          startThread();
        }
      }
    }
//        byte[] sid = intent.getByteArrayExtra("sid");
//        if(sid != null)
//        {
//          m_sidPlayer = null;
//          stopThread();
//          m_sidPlayer = new SidPlayer(sid, m_audioBufferSize);
//          startThread();
//        }
//      }  
//    }
//    Log.d(MODULE, "onStart returning");
  }
  
  private void showSlowCpuNotification(PendingIntent contentIntent)
  {
    Log.d(MODULE, "showSlowCpuNotification");

    String title = "Failed to play SID"; // + m_sidPlayer.getInfoString(0);
    String info = "";

    Notification notification = new Notification(R.drawable.statusbar_icon, title, System.currentTimeMillis());            
    notification.setLatestEventInfo(SidPlayerService.this, title, info, contentIntent);
    notification.flags = Notification.FLAG_AUTO_CANCEL;
    
    NotificationManager manager = (NotificationManager)SidPlayerService.this.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.notify(NOTIFICATION_ID, notification);
  }
  

  private synchronized void startThread()
  {
    if(m_thread==null || !m_thread.isAlive())
    {
      m_thread = new Thread(this);
      m_thread.start();
    }

    //    if(m_sidPlayer == null)
//    {
//      // check for playlist instead
//      if(m_playlist != null)
//      {
//        ContentResolver cr = getContentResolver();
//        int id = m_playlist[m_playlistIndex]; 
//        Cursor sidDataRow = cr.query(ContentUris.withAppendedId(HVSCContentProvider.SIDDATA_CONTENT_URI, id), null, null, null, null);
//        sidDataRow.moveToFirst();
//        byte[] siddata = sidDataRow.getBlob(1);
//        if(siddata != null)
//        {
//          m_sidPlayer = new SidPlayer(siddata, m_audioBufferSize);
//        }
//      }
//    }
//    
//    if((m_thread==null || !m_thread.isAlive()) && m_sidPlayer!=null)
//    {
//      m_thread = new Thread(this);
//      m_thread.start();
//      
//      m_sidPlayingStartedAt = java.lang.System.currentTimeMillis();
//      m_playingName = m_sidPlayer.getInfoString(0);
//    }
  }

  private void stopThread()
  {
    Thread thread;    
    synchronized(SidPlayerService.this)
    {
      thread = m_thread;
      m_thread = null;
    }
    
    if(thread != null)
    {
      thread.interrupt();
      try
      {
        thread.join();
      }
      catch(InterruptedException e)
      {
        Log.d(MODULE, "stopping thread: InterruptedException: "+e);
      }
      Log.d(MODULE, "thread stopped");
    }
  }

  public void run()
  {
    Log.d(MODULE+":thread", "enter");

    long sidPlayingStartedAt = java.lang.System.currentTimeMillis(); // used to track analytics

    m_wakeLock.acquire();
    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

    int minBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_CONFIGURATION_MONO);
    Log.d(MODULE, "minBufferSize="+minBufferSize);
    AudioTrack m_audio = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_CONFIGURATION_MONO , AudioFormat.ENCODING_PCM_16BIT, Math.max(minBufferSize, 32*1024), AudioTrack.MODE_STREAM);
    m_audio.play();
    
    String ns = Context.NOTIFICATION_SERVICE;
    NotificationManager notificationManager = (NotificationManager)getSystemService(ns);
    PendingIntent notificationIntent = PendingIntent.getActivity(SidPlayerService.this, 0, new Intent(SidPlayerService.this, PlayerActivity.class), 0);
    Notification notification = null;
    
    boolean keepRunning = true;
    boolean interrupted = false;
    while(keepRunning && !(interrupted|=Thread.interrupted()))
    {
      keepRunning = false;

      int sidIndex = -1;
      synchronized(SidPlayerService.this)
      {
        if(m_playlist != null)
        {
          sidIndex = m_playlist[m_playlistIndex];
        }
      }
      
      if(sidIndex < 0)
        break;
      
      ContentResolver cr = getContentResolver();
      Cursor sidDataRow = cr.query(ContentUris.withAppendedId(HVSCContentProvider.SIDDATA_CONTENT_URI, sidIndex), null, null, null, null);
      sidDataRow.moveToFirst();
      byte[] siddata = sidDataRow.getBlob(1);
      
      if(siddata.length == 0)
        break;

      SidPlayer sidPlayer = new SidPlayer(siddata, m_audioBufferSize);
      if(m_cachedCurrentSongIndex >= 0)
      {
        sidPlayer.setSong(m_cachedCurrentSongIndex);
      }
      updateSongCache(sidPlayer, sidIndex);
      int lastRequestedSongIndex = m_cachedCurrentSongIndex;

      String title = String.format("Playing %s", sidPlayer.getInfoString(0));
      String info = String.format("%s (%s)", sidPlayer.getInfoString(1), sidPlayer.getInfoString(2));
      
      if(notification == null)
      {
        notification = new Notification(R.drawable.statusbar_icon, title, System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_ONGOING_EVENT;
      }
      notification.setLatestEventInfo(this, title, info, notificationIntent);
      startForeground(NOTIFICATION_ID, notification);
      
      long lastRealTime = java.lang.System.currentTimeMillis();
      long lastThreadTime = android.os.Debug.threadCpuTimeNanos();
      int framesSinceLastTime = 0;
      int lateFramesCount = 0;
  
      m_songPlayingStartedAt = java.lang.System.currentTimeMillis();
      long lastBroadcastTime = m_songPlayingStartedAt; 

      sendBroadcast(m_sidUpdateBroadcast);

      while(!(interrupted|=Thread.interrupted()) && lateFramesCount<5 /* && m_audio.getState()==AudioTrack.PLAYSTATE_PLAYING*/)
      {
        short[] samples = sidPlayer.getAudioData();
        if(samples == null) break;
        m_audio.write(samples, 0, samples.length);
  
        if(sidPlayer.getSilentFrameCount() > 10*44100/m_audioBufferSize)
        {
          // its been silent for a number of seconds, stop burning cycles...
          break;
        }
        
        if(lastRequestedSongIndex != m_cachedCurrentSongIndex)
        {
          sidPlayer.setSong(m_cachedCurrentSongIndex);
          updateSongCache(sidPlayer, sidIndex);
          lastRequestedSongIndex = m_cachedCurrentSongIndex;
          m_songPlayingStartedAt = java.lang.System.currentTimeMillis();
          sendBroadcast(m_sidUpdateBroadcast);
        }
        
        // try to determine if the cpu is too slow to play this sid
        framesSinceLastTime += 1;
        if(framesSinceLastTime >= 44100*1/m_audioBufferSize)
        {
          long nowRealTime = java.lang.System.currentTimeMillis();
          long nowThreadTime = android.os.Debug.threadCpuTimeNanos();
          // since AudioTrack cant tell us if we have "underflowed" it, try to see if we are spending to much time:
          int realTimeUsage = (int)((nowRealTime-lastRealTime)*100l / (m_audioBufferSize*framesSinceLastTime*1000/44100));
          int threadUsage = (int)((nowThreadTime-lastThreadTime)/1000000l*100l / (m_audioBufferSize*framesSinceLastTime*1000/44100));
  
          //Log.d("@@@", "pctusage: "+realTimeUsage + ", "+threadUsage);
          lateFramesCount = realTimeUsage>=105 || threadUsage>=95 ? lateFramesCount+1 : 0;
          
          framesSinceLastTime = 0;
          lastRealTime = nowRealTime;
          lastThreadTime = nowThreadTime;
        }
  
        long nowRealTime = java.lang.System.currentTimeMillis();
        if(nowRealTime-lastBroadcastTime >= 1000)
        {
          sendBroadcast(m_songTimeUpdateBroadcast);
          lastBroadcastTime = nowRealTime;
        }

        if((nowRealTime-m_songPlayingStartedAt)/1000 >= m_cachedSongDuration)
        {
          // done playing this sid/song
          break;
        }
      }
      Log.d(MODULE+":thread", "exit loop");

      if(lateFramesCount >= 5)
      {
        showSlowCpuNotification(notificationIntent);
      }
      else if(!interrupted)
      {
        // advance to next sid/song
        synchronized(SidPlayerService.this)
        {
          if(m_advanceSongBeforeSid && m_cachedCurrentSongIndex<m_cachedSongCount)
          {
            m_cachedCurrentSongIndex += 1;
            keepRunning = true;
          }
          else
          {
            if(m_playlist!=null && m_playlistIndex<m_playlist.length-1)
            {
              m_playlistIndex += 1;
              keepRunning = true;
            }
          }
        }
      }
    } // while(keepRunning)

    stopForeground(true);

    m_audio.stop();
    m_audio.flush();

    long dt = java.lang.System.currentTimeMillis() - sidPlayingStartedAt;
    if(dt > 5*1000)
    {
      Log.v(MODULE, "played " + m_cachedSidTitle + " for " + (dt/1000.0) + " secs"); // todo: change to hvsc file path
      if(m_prefs.getBoolean("google-analytics-enabled", false))
      {
        m_tracker.trackEvent("song", "played", m_cachedSidTitle, (int)(dt/1000));
        m_tracker.dispatch();
      }
    }
    
    m_wakeLock.release();

    Log.d(MODULE+":thread", "left");
  }

  private synchronized void updateSongCache(SidPlayer sidPlayer, int sidIndex)
  {
    m_cachedSidTitle = sidPlayer.getInfoString(0);
    m_cachedSidAuthor = sidPlayer.getInfoString(1);
    m_cachedSidCopyright = sidPlayer.getInfoString(2);
    m_cachedSongCount = sidPlayer.getSongCount();
    m_cachedCurrentSongIndex = sidPlayer.getCurrentSong();

    int songIndex = sidPlayer.getCurrentSong()-1; // this is one based

    //int sidIndex = m_playlist[m_playlistIndex];
    
    Uri.Builder builder = HVSCContentProvider.CONTENT_URI.buildUpon();
    ContentUris.appendId(builder, sidIndex);
    ContentUris.appendId(builder, songIndex);
    ContentResolver cr = getContentResolver();
    Cursor sidSongRow = cr.query(builder.build(), null, null, null, null);

    sidSongRow.moveToFirst();
    m_cachedSongDuration = sidSongRow.getInt(sidSongRow.getColumnIndexOrThrow(SongCursor.COL_DURATION));
  }
}

