package net.bitheap.sidplayer.hvscprovider;

import net.bitheap.sidplayer.HvscPak;
import android.database.AbstractCursor;
import android.database.CursorWindow;
import android.os.Bundle;
import android.util.Log;

class SidCursor extends AbstractCursor
{
  // cached version of last search:
  static String m_cachedSearch;
  static int[] m_cachedRows;

  private final HvscPak m_pak;
  private int[] m_rows;
  
  SidCursor(HvscPak pak, String searchString)
  {
    Log.d("SidZipCursor", "ctor called searchString="+searchString);

    m_pak = pak;
    
    m_rows = null;
    if(searchString!=null)
    {
      if(m_cachedSearch!=null && searchString.equals(m_cachedSearch))
      {
        Log.d("SidZipCursor", "reusing last search");
        m_rows = m_cachedRows;
      }
      else
      {
        m_rows = m_pak.findSids(searchString);
        
        m_cachedSearch = searchString;
        m_cachedRows = m_rows;
      }
    }
  }
  
  @Override
  public String[] getColumnNames()
  {
    return new String[]{"_id", "name", "author"};
  }

  @Override
  public int getCount()
  {
    return m_rows==null ? m_pak.getSidCount() : m_rows.length;
  }

  @Override
  public double getDouble(int columnIndex)
  {
    throw new IllegalArgumentException("not implemented");
  }

  @Override
  public Bundle getExtras()
  {
    throw new IllegalArgumentException("not implemented");
  }

  @Override
  public float getFloat(int columnIndex)
  {
    throw new IllegalArgumentException("not implemented");
  }

  @Override
  public int getInt(int columnIndex)
  {
    if(columnIndex == 0) return m_rows==null ? mPos : m_rows[mPos];
    throw new IllegalArgumentException("getInt: " + columnIndex);
  }

  @Override
  public long getLong(int columnIndex)
  {
    if(columnIndex == 0) return m_rows==null ? mPos : m_rows[mPos];
    throw new IllegalArgumentException("getLong: " + columnIndex);
  }

  @Override
  public short getShort(int columnIndex)
  {
    throw new IllegalArgumentException("not implemented");
  }

  @Override
  public String getString(int columnIndex)
  {
    //Log.v("SidZipCursor", "getString("+mPos+":"+columnIndex+")");
    int row = m_rows==null ? mPos : m_rows[mPos];
    if(columnIndex == 0) return String.valueOf(row);
    if(columnIndex == 1) return formatFilename(row);
    if(columnIndex == 2) return m_pak.getSidAuthor(row);
    throw new IllegalArgumentException("getString: " + columnIndex);
  }

  @Override
  public boolean isNull(int columnIndex)
  {
    throw new IllegalArgumentException("not implemented");
  }

  // the default implementation returns ALL the rows; so instead just return a small window: 
  @Override
  public void fillWindow(int position, CursorWindow window) 
  {
    //Log.v("SidZipCursor", "fillWindow(position="+position+", window="+window+")");

    int start_position = Math.max(position-25, 0);
    int end_position = Math.min(position+25, getCount());

    window.acquireReference();
    window.clear();
    window.setStartPosition(start_position);
    window.setNumColumns(3);
    for(int i=start_position; i<end_position && window.allocRow(); i++)
    {
      int row = m_rows==null ? i : m_rows[i];
      window.putLong(row, i, 0);
      window.putString(formatFilename(row), i, 1);
      window.putString(m_pak.getSidAuthor(row), i, 2);
    } 
    window.releaseReference();
  }

  @Override
  public Bundle respond(Bundle extras)
  {
    if(extras.getString("cmd").equals("allids"))
    {
      // return a list of all ids (first column)
      int[] ids;
      if(m_rows == null)
      {
        ids = new int[m_pak.getSidCount()];
        for(int i=0; i<ids.length; i++)
        {
          ids[i] = i;
        }
      }
      else
      {
        ids = new int[m_rows.length];
        for(int i=0; i<ids.length; i++)
        {
          ids[i] = m_rows[i];
        }
      }
      Bundle reply = new Bundle();
      reply.putIntArray("ids", ids);
      return reply;
    }
    return super.respond(extras);
  }
  
  public String formatFilename(int index)
  {
    String s = m_pak.getSidStilName(index);
    if(s.length() == 0) s = m_pak.getSidName(index);
    return s;
  }
}
