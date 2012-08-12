package net.bitheap.sidplayer.hvscprovider;

import java.io.File;

import net.bitheap.sidplayer.HvscPak;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;
import android.widget.Toast;

public class HVSCContentProvider extends ContentProvider
{
  private static final String MODULE = "HVSCContentProvider";

  private static final String AUTH = "net.bitheap.sidplayer.hvsc";
  
  private static final String URI = "content://" + AUTH + "/sid";
  public static final Uri CONTENT_URI = Uri.parse(URI);
  
  private static final String SIDDATA_URI = "content://" + AUTH + "/siddata";
  public static final Uri SIDDATA_CONTENT_URI = Uri.parse(SIDDATA_URI);

  private static final int MATCHER_SID_ALL_ROWS = 1;
  private static final int MATCHER_SID_SINGLE_ROW = 2;
  private static final int MATCHER_SONG_SINGLE_ROW = 3;
  private static final int MATCHER_SUGGESTIONS = 4;
  private static final int MATCHER_SID_DATA = 5;

  //private static final String ZIP_FILENAME = "C64Music.zip";
  //private static final String PAK_FILENAME = "hvsc.pak";

  private static final UriMatcher uriMatcher;
  static 
  {
    Log.d(MODULE, "static init");
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI(AUTH, "sid", MATCHER_SID_ALL_ROWS);
    uriMatcher.addURI(AUTH, "sid/#", MATCHER_SID_SINGLE_ROW);
    uriMatcher.addURI(AUTH, "sid/#/#", MATCHER_SONG_SINGLE_ROW);
    uriMatcher.addURI(AUTH, "search_suggest_query/*", MATCHER_SUGGESTIONS);
    uriMatcher.addURI(AUTH, "siddata/#", MATCHER_SID_DATA);
    Log.d(MODULE, "static init done");
  }

  //private ZipFile m_zipFile;
  private HvscPak m_pak; 

  @Override
  public boolean onCreate()
  {
    Log.d(MODULE, "OnCreate called");

    String pakPath = Environment.getExternalStorageDirectory()+"/Android/obb/"+getContext().getPackageName();
    String pakFilename = "main.4." + getContext().getPackageName() + ".obb"; 
    Log.d(MODULE, "pak path: "+pakPath+"/"+pakFilename);
    
    File c64music = new File(pakPath, pakFilename);
    if(!c64music.isFile())
    {
      Log.e(MODULE, "could not find "+pakPath+"/"+pakFilename);
      Toast.makeText(getContext(), "hvscpak file not found...", Toast.LENGTH_LONG).show();
      return false;
    }
    else
    {
      m_pak = new HvscPak(c64music.getAbsolutePath());
    }
    
    int tuneCount = m_pak.getSidCount();
    
    Log.d(MODULE, "found "+tuneCount+" sids in pak file.");
    
    return true;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
  {
    Log.v(MODULE, "query, uri="+uri+" projection="+projection+" selection="+selection);
    
    // query, uri=content://net.bitheap.provider.test/search_suggest_query?limit=50 projection=null selection=field =?
    
    switch(uriMatcher.match(uri))
    {
      case MATCHER_SID_ALL_ROWS:
        return new SidCursor(m_pak, selection);
        
      case MATCHER_SONG_SINGLE_ROW:
      {
        int sidIndex = Integer.parseInt(uri.getPathSegments().get(1));
        int songIndex = Integer.parseInt(uri.getPathSegments().get(2));
        return new SongCursor(m_pak, sidIndex, songIndex);
      }
        
      case MATCHER_SID_DATA:
        int id = (int)ContentUris.parseId(uri);
        Log.v(MODULE, "id="+id);
        if(id>=0 && id<m_pak.getSidCount())
        {
          //Log.v(MODULE, "filename="+m_filenames[id]);

          byte[] siddata = m_pak.getSidData(id);
          if(siddata == null)
          {
            //Log.e(MODULE, "failed to open sid: "+m_filenames[id]);
            return null;
          }

          Log.v(MODULE, "data="+siddata.length);
          
          SidDataCursor cursor = new SidDataCursor();
          cursor.id = id;
          cursor.siddata = siddata;
          return cursor;
        }
        
      case MATCHER_SUGGESTIONS:
        String searchString = uri.getLastPathSegment();
        //Log.v(MODULE, "suggestions for "+query);
        int[] searchSuggestions = m_pak.findSids(searchString);
        String[] columns = { BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_INTENT_DATA };
        MatrixCursor cursor = new MatrixCursor(columns, searchSuggestions.length);
        for(int i=0; i<searchSuggestions.length && i<50; i++)
        {
          int sidIndex = searchSuggestions[i];
          MatrixCursor.RowBuilder row = cursor.newRow();
          row.add(sidIndex);

          String s = m_pak.getSidStilName(sidIndex);
          if(s.length() == 0) s = m_pak.getSidName(sidIndex);
          row.add(s);

          row.add(sidIndex);
        }
        
        return cursor;
        
      default:
        Log.v(MODULE, "got unknown uri "+uri);
    }
    return null;
  }
  
  @Override
  public Uri insert(Uri uri, ContentValues values)
  {
    Log.v(MODULE, "insert, uri="+uri+" values="+values);
    throw new IllegalArgumentException("insert not supported");
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs)
  {
    Log.v(MODULE, "delete, uri="+uri+" selection="+selection);
    throw new IllegalArgumentException("delete not supported");
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
  {
    Log.v(MODULE, "delete, uri="+uri+" selection="+selection);
    throw new IllegalArgumentException("update not supported");
  }
  
  @Override
  public String getType(Uri _uri) 
  {
    switch(uriMatcher.match(_uri)) 
    {
      case MATCHER_SID_ALL_ROWS: return "vnd.android.cursor.dir/sid";
      case MATCHER_SID_SINGLE_ROW: return "vnd.android.cursor.item/sid";
      default: throw new IllegalArgumentException("Unsupported URI: " + _uri);
    }
  }
}

