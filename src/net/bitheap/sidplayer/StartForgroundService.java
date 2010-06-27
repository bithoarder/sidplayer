package net.bitheap.sidplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.os.Build;

abstract class StartForgroundService
{
  public static StartForgroundService getInstance()
  {
    if(Integer.parseInt(Build.VERSION.SDK) <= 4)
      return PreSdk4.Holder.sInstance;
    else
      return PostSdk4.Holder.sInstance;
  }

  public abstract void showNotification(Service context, int id, Notification notification);
  public abstract void hideNotification(Service context, int id);

  private static class PreSdk4 extends StartForgroundService
  {
    private static class Holder
    {
      private static final PreSdk4 sInstance = new PreSdk4();
    }

    private NotificationManager getNotificationManager(Context context)
    {
      return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void showNotification(Service context, int id, Notification n)
    {
      context.setForeground(true);
      getNotificationManager(context).notify(id, n);
    }

    @Override
    public void hideNotification(Service context, int id)
    {
      context.setForeground(false);
      if(id>=0) getNotificationManager(context).cancel(id);
    }
  }

  private static class PostSdk4 extends StartForgroundService
  {
    private static class Holder
    {
      private static final PostSdk4 sInstance = new PostSdk4();
    }

    @Override
    public void showNotification(Service context, int id, Notification n)
    {
      context.startForeground(id, n);
    }

    @Override
    public void hideNotification(Service context, int id)
    {
      context.stopForeground(id>=0);
    }
  }
}
