package net.bitheap.sidplayer.downloader;

import com.google.android.vending.expansion.downloader.impl.DownloaderService;

public class HVSCDownloaderService extends DownloaderService
{
  // You must use the public key belonging to your publisher account
  public static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkvRUbfQkb0wyHo3n4MiK/iIICt4DxTresiiY3OwcH4NC/c4dxWMgSBRG6esMhPMLAJXF+EilpW+eMOuUOkbUPADKIADKRR14sFQYYpWrl33LWI/rj8euY3PmlxpVhVBEDf5DJq7jYRdtiY+Bnp8bFWxM2In6GrNqhhCTg9A7gAjb7i12KkYIi5CNFmbng2OaoCePsSHZYHzi6m8I+kZBSmw0HqTGou1Hak1y//Msl9B1EgWOJy/FGTiij5yNuG4u/ZuQGgHompWXI8H7k+NZih4mj4eZ20U5J9ktum4unLDjHKlB5yvtwDsW5csOsyEbGKQe8gLnHMLGzbDzlfv6RwIDAQAB+XDeY3oEGN4HLyw8dlL3pDjPsVj0+IHs/yZ/R2a3ej5yeIC2WyHbzkUkxsgfRZGqaYJqVzVUqyy4dDB+3uK8JlhcGQcgl/Oo9ZzSjXTnBnODphwk6x+3t6Gdvw3erAwK4v89a5ctcJGWfxmT1mXwqFB9nPZ78QNUyzS8fILWJgqK56gxnxJHLhJ2hJwtqN7Bd1HQPRUsmzyRsI49KNrBopivu0fK4jYpqnxHD01XqpecgKSR+7rEZLBig1IQIDAQAB";
  // You should also modify this salt
  public static final byte[] SALT = new byte[] { 1, 42, -12, -1, 54, 98, -100, -12, 43, 2, -8, -4, 9, 5, -106, -107, -33, 45, -1, 84 };

  @Override
  public String getPublicKey() 
  {
      return BASE64_PUBLIC_KEY;
  }

  @Override
  public byte[] getSALT() 
  {
      return SALT;
  }

  @Override
  public String getAlarmReceiverClassName() 
  {
      return HVSCAlarmReceiver.class.getName();
  }
}
