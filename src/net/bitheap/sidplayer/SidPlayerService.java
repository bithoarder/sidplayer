package net.bitheap.sidplayer;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SidPlayerService extends Service implements Runnable
{
  public static String PLAY_SID = "PlaySid";

  private static String MODULE = "SidPlayerService";
  private static int NOTIFICATION_ID = 1;
  
  private volatile AudioTrack m_audio;
  private int m_audioBufferSize;
  private int[] m_playlist;
  private int m_playlistIndex;
  private volatile SidPlayer m_sidPlayer;
  private volatile Thread m_thread;
  private WakeLock m_wakeLock;

  private long m_playingStartedAt;
  private String m_playingName;

  private final ISidPlayerService.Stub m_binder = new ISidPlayerService.Stub()
  {
    //@Override
    public int getCurrentPlaylistIndex() throws RemoteException
    {
      return m_playlistIndex;
    }
    
    //@Override
    public int getCurrentSong() throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_sidPlayer==null ? 0 : m_sidPlayer.getCurrentSong();
      }
    }

    //@Override
    public String getInfoString(int stringIndex) throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_sidPlayer==null ? "<?>" : m_sidPlayer.getInfoString(stringIndex);
      }
    }

    //@Override
    public int getPlaylistLength() throws RemoteException
    {
      return m_playlist!=null ? m_playlist.length : 0;
    }
    
    //@Override
    public int getSongCount() throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        return m_sidPlayer==null ? 0 : m_sidPlayer.getSongCount();
      }
    }

    //@Override
    public boolean isPlaying() throws RemoteException
    {
      return m_thread != null;
    }

    //@Override
    public void pause() throws RemoteException
    {
      stopThread();
    }
    
    //@Override
    public void play() throws RemoteException
    {
      startThread();
    }

    //@Override
    public void playPlaylistIndex(int playlistIndex) throws RemoteException
    {
      Log.v(MODULE, "play playlist:" + playlistIndex);
      if(m_playlist!=null && playlistIndex>=0 && playlistIndex<m_playlist.length)
      {
        m_playlistIndex = playlistIndex;

        stopThread();
        m_sidPlayer = null;
        startThread();
      }
    }

    //@Override
    public void setPlaylist(int[] ids) throws RemoteException
    {
      Log.v(MODULE, "got playlist:" + ids.length);
      m_playlist = ids;
      m_playlistIndex = 0;
    }

    //@Override
    public void setSong(int song) throws RemoteException
    {
      synchronized(SidPlayerService.this)
      {
        if(m_sidPlayer != null) 
        {
          m_sidPlayer.setSong(song);
          startThread();
        }
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

    m_tracker = GoogleAnalyticsTracker.getInstance();
    m_tracker.start("UA-18467147-1", this);
    m_tracker.setProductVersion("ver1", "ver2");
    
    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
    
    TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
    tm.listen(m_phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    
    PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
    m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SidPlayerService");

    int minBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_CONFIGURATION_MONO);
    Log.d(MODULE, "minBufferSize="+minBufferSize);
    m_audio = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_CONFIGURATION_MONO , AudioFormat.ENCODING_PCM_16BIT, Math.max(minBufferSize, 32*1024), AudioTrack.MODE_STREAM);
    m_audioBufferSize = 1024;
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
        byte[] sid = intent.getByteArrayExtra("sid");
        if(sid != null)
        {
          m_sidPlayer = null;
          stopThread();
          m_sidPlayer = new SidPlayer(sid, m_audioBufferSize);
          startThread();
        }
      }  
    }
    Log.d(MODULE, "onStart returning");
  }
  
  private void showSlowCpuNotification(PendingIntent contentIntent)
  {
    Log.d(MODULE, "showSlowCpuNotification");

    String title = "Failed to play " + m_sidPlayer.getInfoString(0);
    String info = "";

    Notification notification = new Notification(R.drawable.statusbar_icon, title, System.currentTimeMillis());            
    notification.setLatestEventInfo(SidPlayerService.this, title, info, contentIntent);
    notification.flags = Notification.FLAG_AUTO_CANCEL;
    
    NotificationManager manager = (NotificationManager)SidPlayerService.this.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.notify(NOTIFICATION_ID, notification);
  }
  

  private synchronized void startThread()
  {
    if(m_sidPlayer == null)
    {
      // check for playlist instead
      if(m_playlist != null)
      {
        ContentResolver cr = getContentResolver();
        int id = m_playlist[m_playlistIndex]; 
        Cursor sidDataRow = cr.query(ContentUris.withAppendedId(SidZipContentProvider.SIDDATA_CONTENT_URI, id), null, null, null, null);
        sidDataRow.moveToFirst();
        byte[] siddata = sidDataRow.getBlob(1);
        if(siddata != null)
        {
          m_sidPlayer = new SidPlayer(siddata, m_audioBufferSize);
        }
      }
    }
    
    if((m_thread==null || !m_thread.isAlive()) && m_sidPlayer!=null)
    {
      m_thread = new Thread(this);
      m_thread.start();
      
      m_playingStartedAt = java.lang.System.currentTimeMillis();
      m_playingName = m_sidPlayer.getInfoString(0);
    }
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
      long dt = java.lang.System.currentTimeMillis() - m_playingStartedAt;
      if(dt > 1*1000)
      {
        Log.v(MODULE, "played " + m_playingName + " for " + (dt/1000.0) + "secs");
        m_tracker.trackEvent("song", "played", m_playingName, (int)(dt/1000));
        m_tracker.dispatch();
      }

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
    m_wakeLock.acquire();
    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

    String title = String.format("Playing %s", m_sidPlayer.getInfoString(0));
    String info = String.format("%s (%s)", m_sidPlayer.getInfoString(1), m_sidPlayer.getInfoString(2));
    
    PendingIntent contentIntent = PendingIntent.getActivity(SidPlayerService.this, 0, new Intent(SidPlayerService.this, PlayerActivity.class), 0);
    Notification notification = new Notification(R.drawable.statusbar_icon, title, System.currentTimeMillis());            
    notification.setLatestEventInfo(SidPlayerService.this, title, info, contentIntent);
    StartForgroundService.getInstance().showNotification(SidPlayerService.this, NOTIFICATION_ID, notification);

    m_audio.play();

    long lastRealTime = java.lang.System.currentTimeMillis();
    long lastThreadTime = android.os.Debug.threadCpuTimeNanos();
    int framesSinceLastTime = 0;
    int lateFramesCount = 0;
    
    while(m_sidPlayer!=null && !Thread.interrupted() && lateFramesCount<5 /* && m_audio.getState()==AudioTrack.PLAYSTATE_PLAYING*/)
    {
      short[] samples;
      synchronized(SidPlayerService.this)
      {
        samples = m_sidPlayer.getAudioData();

        if(m_sidPlayer.getSilentFrameCount() > 10*44100/m_audioBufferSize)
        {
          // its been silent for a number of seconds, stop burning cycles...
          break;
        }
      }
      if(samples == null) break;
      m_audio.write(samples, 0, samples.length);
      
      framesSinceLastTime += 1;
      if(framesSinceLastTime >= 44100*1/m_audioBufferSize)
      {
        long nowRealTime = java.lang.System.currentTimeMillis();
        long nowThreadTime = android.os.Debug.threadCpuTimeNanos();
        // since AudioTrack cant tell us if we have "underflowed" it, try to see if we are spending to much time:
        int realTimeUsage = (int)((nowRealTime-lastRealTime)*100l / (m_audioBufferSize*framesSinceLastTime*1000/44100));
        int threadUsage = (int)((nowThreadTime-lastThreadTime)/1000000l*100l / (m_audioBufferSize*framesSinceLastTime*1000/44100));

        Log.d("@@@", "pctusage: "+realTimeUsage + ", "+threadUsage);
        lateFramesCount = realTimeUsage>=105 || threadUsage>=95 ? lateFramesCount+1 : 0;
        
        framesSinceLastTime = 0;
        lastRealTime = nowRealTime;
        lastThreadTime = nowThreadTime;
      }
    }
    Log.d(MODULE+":thread", "exit loop");

    m_audio.stop();
    m_audio.flush();
    
    StartForgroundService.getInstance().hideNotification(SidPlayerService.this, NOTIFICATION_ID);

    if(lateFramesCount >= 5)
    {
      showSlowCpuNotification(contentIntent);
    }
    
    m_wakeLock.release();

    Log.d(MODULE+":thread", "left");
  }
}

