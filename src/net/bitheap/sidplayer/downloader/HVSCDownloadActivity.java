package net.bitheap.sidplayer.downloader;

import net.bitheap.sidplayer.R;
import net.bitheap.sidplayer.SidListActivity;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Messenger;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import com.google.android.vending.expansion.downloader.DownloaderServiceMarshaller;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IDownloaderClient;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;

// code based on googles play_akp_expansion example

public class HVSCDownloadActivity extends Activity implements IDownloaderClient 
{
  private static String MODULE = "DownloadActivity";

  private ProgressBar m_progressBar;
  private TextView m_statusText;
  private TextView m_progressFraction;
  private TextView m_progressPercent;
  private TextView m_averageSpeed;
  private TextView m_timeRemaining;
  private View m_dashboard;
  private View m_cellMessage;
  private Button m_pauseButton;
  private Button m_wiFiSettingsButton;

  private boolean m_statePaused;

  private IStub m_downloaderClientStub;
  private IDownloaderService m_remoteService;

  private int m_state;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
   
    Log.v(MODULE, "action="+getIntent().getAction());

    initializeDownloadUI();

    // Build an Intent to start this activity from the Notification
    Intent notifierIntent = new Intent(this, HVSCDownloadActivity.this.getClass());
    notifierIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifierIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    
    // Start the download service (if required)
    int startResult;
    try
    {
      startResult = DownloaderClientMarshaller.startDownloadServiceIfRequired(this, pendingIntent, HVSCDownloaderService.class);
      if(startResult != DownloaderClientMarshaller.NO_DOWNLOAD_REQUIRED) 
      {
        initializeDownloadUI();
        return;
      }
    }
    catch(NameNotFoundException e)
    {
      e.printStackTrace();
    }
  }

  /**
   * Connect the stub to our service on start.
   */
  @Override
  protected void onStart() 
  {
    Log.v(MODULE, "onStart");
    if(null != m_downloaderClientStub) 
    {
        m_downloaderClientStub.connect(this);
    }
    super.onStart();
  }

  /**
   * Disconnect the stub from our service on stop
   */
  @Override
  protected void onStop() 
  {
    Log.v(MODULE, "onStop");
    if(null != m_downloaderClientStub) 
    {
        m_downloaderClientStub.disconnect(this);
    }
    super.onStop();
  }

  /**
   * Critical implementation detail. In onServiceConnected we create the
   * remote service and marshaler. This is how we pass the client information
   * back to the service so the client can be properly notified of changes. We
   * must do this every time we reconnect to the service.
   */
  //@Override
  public void onServiceConnected(Messenger m) 
  {
    Log.v(MODULE, "onServiceConnected");
    m_remoteService = DownloaderServiceMarshaller.CreateProxy(m);
    m_remoteService.onClientUpdated(m_downloaderClientStub.getMessenger());
  }

  /**
   * If the download isn't present, we initialize the download UI. This ties
   * all of the controls into the remote service calls.
   */
  private void initializeDownloadUI() 
  {
    m_downloaderClientStub = DownloaderClientMarshaller.CreateStub(this, HVSCDownloaderService.class);
    setContentView(R.layout.download);

    m_progressBar = (ProgressBar)findViewById(R.id.progressBar);
    m_statusText = (TextView)findViewById(R.id.statusText);
    m_progressFraction = (TextView)findViewById(R.id.progressAsFraction);
    m_progressPercent = (TextView) findViewById(R.id.progressAsPercentage);
    m_averageSpeed = (TextView) findViewById(R.id.progressAverageSpeed);
    m_timeRemaining = (TextView) findViewById(R.id.progressTimeRemaining);
    m_dashboard = findViewById(R.id.downloaderDashboard);
    m_cellMessage = findViewById(R.id.approveCellular);
    m_pauseButton = (Button) findViewById(R.id.pauseButton);
    m_wiFiSettingsButton = (Button) findViewById(R.id.wifiSettingsButton);

    m_pauseButton.setOnClickListener(new View.OnClickListener() 
    {
      //@Override
      public void onClick(View view) 
      {
        if(m_statePaused) 
        {
          m_remoteService.requestContinueDownload();
        } 
        else 
        {
          m_remoteService.requestPauseDownload();
        }
        setButtonPausedState(!m_statePaused);
      }
    });

    m_wiFiSettingsButton.setOnClickListener(new View.OnClickListener() 
    {
        //@Override
        public void onClick(View v) 
        {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    });

    Button resumeOnCell = (Button)findViewById(R.id.resumeOverCellular);
    resumeOnCell.setOnClickListener(new View.OnClickListener() 
    {
        //@Override
        public void onClick(View view) 
        {
            m_remoteService.setDownloadFlags(IDownloaderService.FLAGS_DOWNLOAD_OVER_CELLULAR);
            m_remoteService.requestContinueDownload();
            m_cellMessage.setVisibility(View.GONE);
        }
    });

  }

  private void setButtonPausedState(boolean paused) 
  {
    m_statePaused = paused;
    int stringResourceID = paused ? R.string.text_button_resume : R.string.text_button_pause;
    m_pauseButton.setText(stringResourceID);
  }

  private void setState(int newState) 
  {
    if(m_state != newState) 
    {
      m_state = newState;
      if(newState == STATE_DOWNLOADING)
        m_statusText.setText(R.string.state_downloading);
      else if(newState == STATE_FAILED_UNLICENSED)
        m_statusText.setText(R.string.state_failed_unlicensed);
      else
        m_statusText.setText(Helpers.getDownloaderStringResourceIDFromState(newState));
    }
  }
  
  /**
   * The download state should trigger changes in the UI --- it may be useful
   * to show the state as being indeterminate at times. This sample can be
   * considered a guideline.
   */
  //@Override
  public void onDownloadStateChanged(int newState) 
  {
    Log.v(MODULE, "onDownloadStateChanged "+newState);
    setState(newState);
    boolean showDashboard = true;
    boolean showCellMessage = false;
    boolean paused;
    boolean indeterminate;
    switch(newState) 
    {
        case IDownloaderClient.STATE_IDLE:
            // STATE_IDLE means the service is listening, so it's
            // safe to start making calls via mRemoteService.
            paused = false;
            indeterminate = true;
            break;
        case IDownloaderClient.STATE_CONNECTING:
        case IDownloaderClient.STATE_FETCHING_URL:
            showDashboard = true;
            paused = false;
            indeterminate = true;
            break;
        case IDownloaderClient.STATE_DOWNLOADING:
            paused = false;
            showDashboard = true;
            indeterminate = false;
            break;

        case IDownloaderClient.STATE_FAILED_CANCELED:
        case IDownloaderClient.STATE_FAILED:
        case IDownloaderClient.STATE_FAILED_FETCHING_URL:
        case IDownloaderClient.STATE_FAILED_UNLICENSED:
            paused = true;
            showDashboard = false;
            indeterminate = false;
            break;
        case IDownloaderClient.STATE_PAUSED_NEED_CELLULAR_PERMISSION:
        case IDownloaderClient.STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION:
            showDashboard = false;
            paused = true;
            indeterminate = false;
            showCellMessage = true;
            break;

        case IDownloaderClient.STATE_PAUSED_BY_REQUEST:
            paused = true;
            indeterminate = false;
            break;
        case IDownloaderClient.STATE_PAUSED_ROAMING:
        case IDownloaderClient.STATE_PAUSED_SDCARD_UNAVAILABLE:
            paused = true;
            indeterminate = false;
            break;
        case IDownloaderClient.STATE_COMPLETED:
            showDashboard = false;
            paused = false;
            indeterminate = false;
            //validateXAPKZipFiles();
            startActivity(new Intent(this, SidListActivity.class));
            finish();
            return;
        default:
            paused = true;
            indeterminate = true;
            showDashboard = true;
    }

    int newDashboardVisibility = showDashboard ? View.VISIBLE : View.GONE;
    if(m_dashboard.getVisibility() != newDashboardVisibility) 
    {
      m_dashboard.setVisibility(newDashboardVisibility);
    }

    int cellMessageVisibility = showCellMessage ? View.VISIBLE : View.GONE;
    if(m_cellMessage.getVisibility() != cellMessageVisibility) 
    {
      m_cellMessage.setVisibility(cellMessageVisibility);
    }

    m_progressBar.setIndeterminate(indeterminate);
    setButtonPausedState(paused);
  }

  //@Override
  public void onDownloadProgress(DownloadProgressInfo progress) 
  {
    Log.v(MODULE, "onDownloadProgress");

    m_averageSpeed.setText(getString(R.string.kilobytes_per_second, Helpers.getSpeedString(progress.mCurrentSpeed)));
    m_timeRemaining.setText(getString(R.string.time_remaining, Helpers.getTimeRemaining(progress.mTimeRemaining)));

    progress.mOverallTotal = progress.mOverallTotal;
    m_progressBar.setMax((int) (progress.mOverallTotal >> 8));
    m_progressBar.setProgress((int) (progress.mOverallProgress >> 8));
    m_progressPercent.setText(Long.toString(progress.mOverallProgress*100/progress.mOverallTotal) + "%");
    m_progressFraction.setText(Helpers.getDownloadProgressString(progress.mOverallProgress, progress.mOverallTotal));
  }

  //@Override
  protected void onDestroy() 
  {
    Log.v(MODULE, "onDestroy");
    super.onDestroy();
  }
}
