package net.bitheap.sidplayer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.bitheap.sidplayer.R;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

public class SidZipContentProvider extends ContentProvider
{
  private static final String URI = "content://net.bitheap.provider.sidzip/sid";
  public static final Uri CONTENT_URI = Uri.parse(URI);
  
  private static final String SIDDATA_URI = "content://net.bitheap.provider.sidzip/siddata";
  public static final Uri SIDDATA_CONTENT_URI = Uri.parse(SIDDATA_URI);
  
  private static final int ALL_ROWS = 1;
  private static final int SINGLE_ROW = 2;
  private static final int SUGGESTIONS = 3;
  private static final int SIDDATA_ROW = 4;
  
  private static final String ZIP_FILENAME = "C64Music.zip";

  private static final UriMatcher uriMatcher;
  static 
  {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("net.bitheap.provider.sidzip", "sid", ALL_ROWS);
    uriMatcher.addURI("net.bitheap.provider.sidzip", "sid/#", SINGLE_ROW);
    uriMatcher.addURI("net.bitheap.provider.sidzip", "search_suggest_query", SUGGESTIONS);
    uriMatcher.addURI("net.bitheap.provider.sidzip", "siddata/#", SIDDATA_ROW);
  }

  private ZipFile m_zipFile;
  private String[] m_filenames;
  
  // MatrixCursor has a few shortcommings:
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
  
  // cached version of last search:
  private static String m_cachedSearch;
  private static int[] m_cachedRows;

  class SidZipCursor extends AbstractCursor
  {
    private int[] m_rows;
    
    SidZipCursor(String searchString)
    {
      Log.d("SidZipCursor", "ctor called searchString="+searchString);
      
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
          String subStrings[] = searchString.toLowerCase().split("[ \t:,.\\(\\)-*/]+");

          // todo: remove stop words from subStrings
          
          if(subStrings.length > 0)
          {
            int rowCount = 0;
            int[] rows = new int[m_filenames.length];
            
            for(int i=0; i<m_filenames.length; i++)
            {
              String filename = m_filenames[i].toLowerCase();
              int score = 0;
              for(String subString : subStrings)
              {
                int index = filename.indexOf(subString);
                if(index >= 0)
                {
                  score += 1;
                  // increase score if its the start of a word
                  if(index==0 || !java.lang.Character.isLetter(filename.charAt(index-1)))
                  {
                    score += 4;

                    // increase score if its a complete word
                    if(index+subString.length()==filename.length() || !java.lang.Character.isLetter(filename.charAt(index+subString.length())))
                    {
                      score += 5;
                    }
                  }
                }
              }
              if(score > 0)
              {
                rows[rowCount] = i | (-score<<24);
                rowCount += 1;
              }
            }
            
            java.util.Arrays.sort(rows, 0, rowCount);

            m_rows = new int[rowCount];
            for(int i=0; i<rowCount; i++)
            {
              m_rows[i] = rows[i]&0x00ffffff;
            }
          }
          
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
      return m_rows==null ? m_filenames.length : m_rows.length;
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
      Log.v("SidZipCursor", "getString("+mPos+":"+columnIndex+")");
      int row = m_rows==null ? mPos : m_rows[mPos];
      if(columnIndex == 0) return String.valueOf(row);
      if(columnIndex == 1) return formatFilename(row, 0);
      if(columnIndex == 2) return formatFilename(row, 1);
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
      Log.v("SidZipCursor", "fillWindow(position="+position+", window="+window+")");

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
        window.putString(formatFilename(row, 0), i, 1);
        window.putString(formatFilename(row, 1), i, 2);
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
          ids = new int[m_filenames.length];
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
    
    public String formatFilename(int index, int column)
    {
      String filename = m_filenames[index];
      String lowerFilename = filename.toLowerCase();
      
      String splitFilename[] = filename.split("/");
      String splitLowerFilename[] = lowerFilename.split("/");

      if(splitLowerFilename.length>=1 && splitLowerFilename[0].equals("c64music"))
      {
        if(splitLowerFilename.length==4 && splitLowerFilename[1].equals("games"))
        {
          return column==0 ? splitFilename[3] : "<?>";
        }
        else if(splitLowerFilename.length==4 && splitLowerFilename[1].equals("demos"))
        {
          return column==0 ? splitFilename[3] : "<?>";
        }
        else if(splitLowerFilename.length==5 && splitLowerFilename[1].equals("demos") && splitLowerFilename[2].equals("unknown") && splitLowerFilename[3].equals("master_composer"))
        {
          return column==0 ? splitFilename[4] : "<?>";
        }
        else if(splitLowerFilename.length==5 && splitLowerFilename[1].equals("musicians"))
        {
          return column==0 ? splitFilename[4] : splitFilename[3];
        }
        else if(splitLowerFilename.length==6 && splitLowerFilename[1].equals("musicians"))
        {
          return column==0 ? splitFilename[4]+ " - " + splitFilename[5] : splitFilename[3];
        }
        else if(splitLowerFilename.length==7 && splitLowerFilename[1].equals("musicians"))
        {
          return column==0 ? splitFilename[5]+ " - " + splitFilename[6] : splitFilename[4];
        }
      }

      return column==0 ? m_filenames[index] : "<?>";
    }
  }
  
  @Override
  public boolean onCreate()
  {
    Log.d("SidZipContentProvider", "OnCreate called");

    m_filenames = new String[0];
    
    // maybe install default mini C64Music.zip on /sdcard
    File c64music = new File(Environment.getExternalStorageDirectory(), ZIP_FILENAME);
    if(!c64music.isFile())
    {
      Log.w("SidZipContentProvider", "installing default "+ZIP_FILENAME+" as "+c64music.getAbsolutePath());
      Toast.makeText(getContext(), "Installing demo "+ZIP_FILENAME, Toast.LENGTH_SHORT).show();
      
      FileOutputStream fileStream;
      try
      {
        fileStream = new FileOutputStream(c64music);
      }
      catch(FileNotFoundException e)
      {
        Log.v("SidZipContentProvider", "could not open "+c64music.getAbsolutePath()+" for writing");
        Toast.makeText(getContext(), "Failed to install "+ZIP_FILENAME+" on sd card", Toast.LENGTH_LONG).show();
        return false;
      }

      try
      {
        InputStream resourceStream = getContext().getResources().openRawResource(R.raw.c64music);
        byte[] buffer = new byte[64*1024];
        while(true)
        {
          int readlen = resourceStream.read(buffer);
          if(readlen <= 0) break;
          fileStream.write(buffer, 0, readlen);
        }
        fileStream.close();
        resourceStream.close();
      }
      catch(IOException e)
      {
        Log.v("SidZipContentProvider", "failed to copy "+ZIP_FILENAME+" resource: "+e);
        c64music.delete();
        return false;
      }
    }

    try
    {
      m_zipFile = new ZipFile(c64music.getAbsolutePath());
    }
    catch(IOException e)
    {
      Log.e("SidZipContentProvider", "could not open "+c64music.getAbsolutePath()+": "+e);
//      new AlertDialog.Builder(this)
//        .setMessage("Could not find the C64Music.zip archive. Please download the HSCV collection from http://www.hvsc.de, extract C64Music.zip from the downloaded archive, and place it in the root of the sdcard.")
//        .show();
      return false;
    }
    
    int entryCount = 0;
    for(Enumeration<? extends ZipEntry> i=m_zipFile.entries(); i.hasMoreElements(); )
    {
      ZipEntry e = i.nextElement();
      if(!e.isDirectory())
      {
        String filename = e.getName();
        if(filename.endsWith(".sid"))
        {
          entryCount += 1;
        }
      }
    }

    m_filenames = new String[entryCount];

    int entryIndex = 0;
    for(Enumeration<? extends ZipEntry> i = m_zipFile.entries(); i.hasMoreElements(); )
    {
      ZipEntry e = i.nextElement();
      if(!e.isDirectory())
      {
        String filename = e.getName();
        if(filename.endsWith(".sid"))
        {
          m_filenames[entryIndex] = filename;
          entryIndex += 1;
        }
      }
    }

    Log.d("SidZipContentProvider", "found "+entryCount+" sids in zip file.");
    
    return true;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
  {
    Log.v("SidZipContentProvider", "query, uri="+uri+" projection="+projection+" selection="+selection);
    
    // query, uri=content://net.bitheap.provider.test/search_suggest_query?limit=50 projection=null selection=field =?
    
    switch(uriMatcher.match(uri))
    {
      case ALL_ROWS:
        return new SidZipCursor(selection);
        
      case SIDDATA_ROW:
        int id = (int)ContentUris.parseId(uri);
        Log.v("SidZipContentProvider", "id="+id);
        if(id>=0 && id<m_filenames.length)
        {
          Log.v("SidZipContentProvider", "filename="+m_filenames[id]);

          ZipEntry entry = m_zipFile.getEntry(m_filenames[id]);
          
          InputStream stream;
          try
          {
            stream = m_zipFile.getInputStream(entry);
          }
          catch(IOException e1)
          {
            Log.e("SidZipContentProvider", "failed to open sid: "+entry.getName());
            return null;
          }
          byte[] siddata = new byte[(int)entry.getSize()];
          try
          {
            int offset = 0;
            while(offset < entry.getSize())
            {
              int readlen = stream.read(siddata, offset, (int)entry.getSize()-offset);
              if(readlen == 0) break;
              offset += readlen;
            }
          }
          catch(IOException e)
          {
            Log.e("SidZipContentProvider", "failed to read sid: "+entry.getName());
            return null;
          }

          Log.v("SidZipContentProvider", "data="+siddata.length);
          
          SidDataCursor cursor = new SidDataCursor();
          cursor.id = id;
          cursor.siddata = siddata;
          return cursor;
        }
        

//      case SUGGESTIONS:
//        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_INTENT_ACTION});
//        cursor.addRow(new Object[]{0, "Fool", Intent.ACTION_VIEW});
//        cursor.addRow(new Object[]{1, "Baz", Intent.ACTION_SEARCH});
//        return cursor;
        
//      case SINGLE_ROW:
    }
    return null;
  }
  
  @Override
  public Uri insert(Uri uri, ContentValues values)
  {
    Log.v("SidZipContentProvider", "insert, uri="+uri+" values="+values);
    throw new IllegalArgumentException("insert not supported");
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs)
  {
    Log.v("SidZipContentProvider", "delete, uri="+uri+" selection="+selection);
    throw new IllegalArgumentException("delete not supported");
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
  {
    Log.v("SidZipContentProvider", "delete, uri="+uri+" selection="+selection);
    throw new IllegalArgumentException("update not supported");
  }
  
  @Override
  public String getType(Uri _uri) 
  {
    switch(uriMatcher.match(_uri)) 
    {
      case ALL_ROWS: return "vnd.android.cursor.dir/sid";
      case SINGLE_ROW: return "vnd.android.cursor.item/sid";
      default: throw new IllegalArgumentException("Unsupported URI: " + _uri);
    }
  }
}

