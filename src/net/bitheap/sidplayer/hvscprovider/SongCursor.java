package net.bitheap.sidplayer.hvscprovider;

import net.bitheap.sidplayer.HvscPak;
import android.database.AbstractCursor;
import android.database.CursorWindow;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.Log;

public class SongCursor extends AbstractCursor
{
  private static final String MODULE = "SongCursor"; 

  public static final String COL_ID = BaseColumns._ID;
  public static final String COL_DURATION = "duration";
  private static final String[] COLUMN_NAMES = {
    COL_ID,
    COL_DURATION
  };
  
  private final HvscPak m_pak;
  private final int m_sidIndex;
  private final int m_songIndex;
  
  SongCursor(HvscPak pak, int sidIndex, int songIndex)
  {
    Log.d(MODULE, "ctor sid="+sidIndex+" song="+songIndex);

    m_pak = pak;
    m_sidIndex = sidIndex;
    m_songIndex = songIndex;
  }
  
  @Override
  public String[] getColumnNames()
  {
    return COLUMN_NAMES;
  }

  @Override
  public int getCount()
  {
    return m_songIndex>=0 ? 1 : m_pak.getSongCount(m_sidIndex);
  }

  @Override public float getFloat(int columnIndex) { throw new IllegalArgumentException("not implemented"); }
  @Override public double getDouble(int columnIndex) { throw new IllegalArgumentException("not implemented"); }
  @Override public short getShort(int columnIndex) { throw new IllegalArgumentException("not implemented"); }
  @Override public String getString(int columnIndex) { throw new IllegalArgumentException("not implemented"); }
  @Override public Bundle getExtras() { throw new IllegalArgumentException("not implemented"); }
  @Override public boolean isNull(int columnIndex) { throw new IllegalArgumentException("not implemented"); }

  @Override
  public int getInt(int columnIndex)
  {
    return (int)getLong(columnIndex);
  }

  @Override
  public long getLong(int columnIndex)
  {
    if(columnIndex == 0) return m_songIndex>=0 ? m_songIndex : mPos;
    if(columnIndex == 1) return m_pak.getSongDuration(m_sidIndex, m_songIndex>=0 ? m_songIndex : mPos);
    throw new IllegalArgumentException("getInt: " + columnIndex);
  }

  // the default implementation returns ALL the rows; so instead just return a small window: 
//  @Override
//  public void fillWindow(int position, CursorWindow window) 
//  {
//    Log.v(MODULE, "fillWindow(position="+position+", window="+window+")");
//
//    int start_position = Math.max(position-25, 0);
//    int end_position = Math.min(position+25, getCount());
//
//    window.acquireReference();
//    window.clear();
//    window.setStartPosition(start_position);
//    window.setNumColumns(3);
//    for(int i=start_position; i<end_position && window.allocRow(); i++)
//    {
//      int row = m_rows==null ? i : m_rows[i];
//      window.putLong(row, i, 0);
//      window.putString(formatFilename(row), i, 1);
//      window.putString(m_provider.m_pak.getSidAuthor(row), i, 2);
//    } 
//    window.releaseReference();
//  }
}
