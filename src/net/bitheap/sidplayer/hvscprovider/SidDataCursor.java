package net.bitheap.sidplayer.hvscprovider;

import android.database.AbstractCursor;
import android.database.CursorWindow;

// MatrixCursor has a few shortcomings:
//  MatrixCursor mangles byte[] arrays since it treats all rows as strings
//  MatrixCursor does not implement getBlob
//  AbstractCursors default implementation of getBlob raises an exception
class SidDataCursor extends AbstractCursor
{
  public int id;
  public byte[] siddata;
  
  private final String[] s_colNames = { "_id", "siddata" };
  
  @Override public String[] getColumnNames() { return s_colNames; }
  @Override public int getCount() { return 1; }
  
  // these getters will normally not be called since the provider has its own process
  @Override public double getDouble(int column) { throw new IllegalArgumentException("not implemented"); }
  @Override public float getFloat(int column) { throw new IllegalArgumentException("not implemented"); }
  @Override public int getInt(int column) { return id; }
  @Override public long getLong(int column) { return id; }
  @Override public short getShort(int column) { throw new IllegalArgumentException("not implemented"); }
  @Override public String getString(int column) { throw new IllegalArgumentException("not implemented"); }
  @Override public byte[] getBlob(int column) { return siddata; }
  @Override public boolean isNull(int column) { throw new IllegalArgumentException("not implemented"); }

  @Override
  public void fillWindow(int position, CursorWindow window) 
  {
    window.acquireReference();
    window.clear();
    window.setStartPosition(0);
    window.setNumColumns(2);
    window.allocRow();
    window.putLong(0, 0, 0);
    window.putBlob(siddata, 0, 1);
    window.releaseReference();
  }
}