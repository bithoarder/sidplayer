#ifndef SIDPLAYER_STILPAK_H
#define SIDPLAYER_STILPAK_H

#include "pak.h"

#include <cstdlib>
#include <string>
#include <vector>

///////////////////////////////////////////////////////////////////////////////

class StilPak
{
public:
  StilPak();
  ~StilPak();

  bool readPak(const char *pakfilename);

  uint getSidCount() const;

  std::vector<uint> findSids(const char *searchString);

  std::string getSidFilepath(uint sidIndex) const;
  const char *getSidSidName(uint sidIndex) const;
  const char *getSidSidAuthor(uint sidIndex) const;
  const char *getSidSidReleased(uint sidIndex) const;
  const char *getSidStilName(uint sidIndex) const;
  const char *getSidStilTitle(uint sidIndex) const;
  const char *getSidStilArtist(uint sidIndex) const;
  const char *getSidStilComment(uint sidIndex) const;
  std::vector<uint8_t> getSidData(uint sidIndex) const;
  uint getSongCount(uint sidIndex) const;

  uint getSongDuration(uint sidIndex, uint songIndex) const;

private:
  //bool readPakHeader(int fileHandle, struct PakHeader *header);
  //void *readSidInfo(int fileHandle, off_t offset, size_t compressedSize, size_t decompressedSize);

  const struct SidInfo *getSidInfo(uint sidIndex) const;
  const struct SongInfoIndex *getSongInfoIndex(uint sidIndex, uint songIndex) const;
  const char *getString(uint stringOffset) const;

  //bool verifySidInfo(const void *sidInfo, size_t sidInfoSize);

private:
  struct sb_stemmer *m_stemmer;

  const void *m_rootPakData;
  size_t m_rootPakSize;

  Pak m_rootPak;
  PakEntry m_sidsPakEntry;
  PakEntry m_stringsPakEntry;
  PakEntry m_inverseIndexPakEntry;
};

///////////////////////////////////////////////////////////////////////////////
#endif
