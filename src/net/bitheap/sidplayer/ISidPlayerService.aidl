package net.bitheap.sidplayer;

interface ISidPlayerService
{
  void setPlaylist(in int[] ids); // SidZipContentProvider ids
  void playPlaylistIndex(int playlistIndex);
  int getPlaylistLength();
  int getCurrentPlaylistIndex();

  String getInfoString(int stringIndex);

  void play();
  void pause();

  boolean isPlaying();

  int getSongCount();
  int getCurrentSong();
  void setSong(int song);
}
