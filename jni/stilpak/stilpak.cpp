#include "stilpak.h"
#include "pak.h"

#include "libstemmer.h"

//#include <cinttypes>
#include <cstdio>
#include <cerrno>
#include <cstring>
#include <cassert>
#include <algorithm>
#include <memory>

#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>

///////////////////////////////////////////////////////////////////////////////

#ifdef ANDROID
#include <android/log.h>
#define DEBUG(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, "stilpak", fmt, ##args)
#define ERROR(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, "stilpak", fmt, ##args)
#define ASSERT(cond) ((cond)?(void)0:__android_log_assert(#cond, "sid", "ASSERT " #cond " at %s:%u", __FILE__, __LINE__))
#else
#define DEBUG(fmt, args...) fprintf(stderr, fmt "\n", ##args)
#define ERROR(fmt, args...) fprintf(stderr, fmt "\n", ##args)
#define ASSERT(cond) assert(cond)
#endif

//namespace {
//void *lzmaAlloc(void *user, size_t size) { return malloc(size); }
//void lzmaFree(void *user, void *address) { return free(address); }
//static ISzAlloc lzmaAllocFuncs = { lzmaAlloc, lzmaFree };
//}

///////////////////////////////////////////////////////////////////////////////

struct SidInfoHeader
{
  uint16_t sid_count;
  uint16_t padding;
};

struct SidInfoIndex
{
  uint32_t offset;
};

struct SidInfo
{
  uint16_t pack_index;
  uint16_t uncompressed_size;
  uint32_t pack_offset;
  uint16_t song_count;
  uint16_t padding;
  uint32_t file_path_stri[6];
  uint32_t sid_name_stri;
  uint32_t sid_author_stri;
  uint32_t sid_released_stri;
  uint32_t stil_name_stri;
  uint32_t stil_title_stri;
  uint32_t stil_artist_stri;
  uint32_t stil_comment_stri;
};

struct SongInfoIndex
{
  uint16_t song_duration;
  uint16_t offset;
};

struct StringPoolHeader
{
  uint32_t string_count;
};

struct SongInfo
{
  uint32_t stil_name_stri;
  uint32_t stil_title_stri;
  uint32_t stil_author_stri;
  uint32_t stil_artist_stri;
  uint32_t stil_comment_stri;
};

struct InvIndexHeader
{
  uint32_t word_count;
};

struct InvIndexIndex
{
  uint32_t offset;
};

enum InvIndexFlags
{
  INV_FILENAME = 0,
  INV_SID_NAME = 1,
  INV_SID_AUTHOR = 2,
  INV_SID_RELEASED = 3,
  INV_STIL_NAME = 4,
  INV_STIL_TITLE = 5,
  INV_STIL_ARTIST = 6,
  INV_STIL_COMMENT = 7,
  INV_STIL_SONG_NAME = 8,
  INV_STIL_SONG_TITLE = 9,
  INV_STIL_SONG_AUTHOR = 10,
  INV_STIL_SONG_ARTIST = 11,
  INV_STIL_SONG_COMMENT = 12,
  //INV_CONTEXT_MASK = (1<<12)-1,
};

struct InvIndexEntry
{
  uint16_t sid_index;
  uint16_t flags;
};

///////////////////////////////////////////////////////////////////////////////

StilPak::StilPak()
{
  m_rootPakData = NULL;
  m_rootPakSize = 0;

  m_stemmer = sb_stemmer_new("english", "ISO_8859_1"); // close enough to cp1252
  if(m_stemmer == NULL)
  {
    ERROR("StilPak::StilPak: unable to create stemmer, search will now work");
  }
}

StilPak::~StilPak()
{
  if(m_rootPakData != NULL)
  {
    munmap((void*)m_rootPakData, m_rootPakSize);
  }

  if(m_stemmer != NULL)
  {
    sb_stemmer_delete(m_stemmer);
  }
}

bool StilPak::readPak(const char *pakfilename)
{
  DEBUG("StilPak::readPak: opening %s", pakfilename);

  int fileHandle = open(pakfilename, O_RDONLY);
  if(fileHandle < 0)
  {
    ERROR("StilPak::readPak: open(%s) failed: %s", pakfilename, strerror(errno));
    return false;
  }

  size_t rootPakSize = 0;
  if((rootPakSize=lseek(fileHandle, 0, SEEK_END))<0 || lseek(fileHandle, 0, SEEK_SET)<0)
  {
    ERROR("StilPak::readPak: lseek(%s) failed: %s", pakfilename, strerror(errno));
    close(fileHandle);
    return false;
  }

  size_t pageSize = sysconf(_SC_PAGE_SIZE);
  size_t mmapSize = (rootPakSize+pageSize-1)&~(pageSize-1);
  void *rootPakData = mmap(NULL, mmapSize, PROT_READ, MAP_SHARED, fileHandle, 0);
  if(rootPakData==NULL || rootPakData==MAP_FAILED)
  {
    ERROR("StilPak::readPak: mmap of %s failed: %s (size=%zu, rsize=%zu)", pakfilename, strerror(errno), rootPakSize, mmapSize);
    close(fileHandle);
    return false;
  }

  close(fileHandle);
  fileHandle = -1;

  if(!m_rootPak.open(rootPakData, rootPakSize))
  {
    ERROR("StilPak::readPak: failed to open pak %s", pakfilename);
    munmap(rootPakData, rootPakSize);
    return false;
  }

  m_sidsPakEntry = m_rootPak.read('SIDI');
  m_stringsPakEntry = m_rootPak.read('STRS');
  m_inverseIndexPakEntry = m_rootPak.read('INVI');

  if(m_sidsPakEntry.data()==NULL || m_stringsPakEntry.data()==NULL || m_inverseIndexPakEntry.data()==NULL)
  {
    ERROR("StilPak::readPak: failed to read SIDI/STRS/INVI");
    munmap(rootPakData, rootPakSize);
    return false;
  }

  m_rootPakData = rootPakData;
  m_rootPakSize = rootPakSize;

  return true;
}

uint StilPak::getSidCount() const
{
  ASSERT(m_sidsPakEntry.data() != NULL);

  const SidInfoHeader *header = cast<SidInfoHeader>(m_sidsPakEntry.data(), 0);
  return header->sid_count;
}

std::string StilPak::getSidFilepath(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  std::string path;
  if(sidInfo != NULL)
  {
    for(int i=0; i<sizeof(sidInfo->file_path_stri)/sizeof(sidInfo->file_path_stri[0]); i++)
    {
      if(sidInfo->file_path_stri[i] != 0)
      {
        if(i != 0) path += "/";
        path += getString(sidInfo->file_path_stri[i]);
      }
    }
  }
  return path;
}

const char *StilPak::getSidSidName(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? getString(sidInfo->sid_name_stri) : "";
}

const char *StilPak::getSidSidAuthor(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? getString(sidInfo->sid_author_stri) : "";
}

const char *StilPak::getSidSidReleased(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? getString(sidInfo->sid_released_stri) : "";
}

const char *StilPak::getSidStilName(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? getString(sidInfo->stil_name_stri) : "";
}

const char *StilPak::getSidStilTitle(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? getString(sidInfo->stil_title_stri) : "";
}

const char *StilPak::getSidStilArtist(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? getString(sidInfo->stil_artist_stri) : "";
}

const char *StilPak::getSidStilComment(uint sidIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? getString(sidInfo->stil_comment_stri) : "";
}

std::vector<uint8_t> StilPak::getSidData(uint sidIndex) const
{
  DEBUG("getSidData: %u", sidIndex);

  std::vector<uint8_t> data;
  const SidInfo *sidInfo = getSidInfo(sidIndex);

#if 1
  uint32_t sidTag = ('P'<<24) | (('0'+sidInfo->pack_index/100)<<16) | (('0'+(sidInfo->pack_index/10)%10)<<8) | ('0'+sidInfo->pack_index%10);
  PakEntry sidDataPakEntry = m_rootPak.read(sidTag);
  if(sidDataPakEntry.data() == NULL)
  {
    ERROR("StilPak::getSidData: unable to read 0x%08x", sidTag);
  }
  else if(sidInfo->pack_offset+sidInfo->uncompressed_size > sidDataPakEntry.size())
  {
    ERROR("StilPak::getSidData: sid %u is outside the sidpack (offset=%u size=%u paksize=%u)", sidIndex, sidInfo->pack_offset, sidInfo->uncompressed_size, sidDataPakEntry.size());
  }
  else
  {
    const uint8_t *p = static_cast<const uint8_t*>(sidDataPakEntry.data());
    data.insert(data.end(), p+sidInfo->pack_offset, p+sidInfo->pack_offset+sidInfo->uncompressed_size);
  }

  return data;

#else
  if(sidInfo != NULL)
  {
    if(lseek(m_pakFileHandle, m_sidPacksOffset, SEEK_SET) < 0)
    {
      DEBUG("lseek(%u) failed: %s", (int)m_sidPacksOffset, strerror(errno));
      return data;
    }

    SidPackHeader header;
    if(read(m_pakFileHandle, &header, sizeof(header)) != sizeof(header))
    {
      DEBUG("Failed to read pack header: %s", strerror(errno));
      return data;
    }

    if(sidInfo->pack_index >= header.pack_count)
    {
      DEBUG("Invalid sid pack index %u (max %u)", sidInfo->pack_index, header.pack_count);
      return data;
    }

    SidPackOffset offsets[header.pack_count];
    if(read(m_pakFileHandle, &offsets, sizeof(offsets)) != sizeof(offsets))
    {
      DEBUG("Failed to read pack offsets: %s", strerror(errno));
      return data;
    }

    if(lseek(m_pakFileHandle, m_sidPacksOffset+offsets[sidInfo->pack_index].offset, SEEK_SET) < 0)
    {
      DEBUG("lseek(%u) failed: %s", (int)offsets[sidInfo->pack_index].offset, strerror(errno));
      return data;
    }

    uint8_t props[LZMA_PROPS_SIZE];
    if(read(m_pakFileHandle, props, sizeof(props)) != sizeof(props))
    {
      DEBUG("Failed to read sid lzma props info: %s", strerror(errno));
      return data;
    }

    CLzmaDec dec;
    LzmaDec_Construct(&dec);
    LzmaDec_Init(&dec);
    SRes res = LzmaDec_Allocate(&dec, props, LZMA_PROPS_SIZE, &lzmaAllocFuncs);
    if(res != SZ_OK)
    {
      DEBUG("Failed to init lzma decompressor: %u", (int)res);
      return data;
    }

    size_t decompressedOffset = 0;
    size_t compressedOffset = 0;

    std::vector<uint8_t> decdata;
    decdata.resize(offsets[sidInfo->pack_index].decompressed_size);

    for(;;)
    {
      uint8_t tmpbuf[64*1024];
      size_t srcSize = std::min((size_t)sizeof(tmpbuf), offsets[sidInfo->pack_index].compressed_size-compressedOffset);

      if(read(m_pakFileHandle, tmpbuf, srcSize) != srcSize)
      {
        DEBUG("Failed to read sid lzma data: %s", strerror(errno));
        return data;
      }

      size_t destSize = offsets[sidInfo->pack_index].decompressed_size - decompressedOffset;
      ELzmaStatus status;
      SRes res = LzmaDec_DecodeToBuf(&dec,
          (Byte*)&decdata[decompressedOffset], &destSize,
          tmpbuf, &srcSize,
          LZMA_FINISH_END, &status);
      if(res != SZ_OK)
      {
        DEBUG("Failed to init lzma decompressor: %u", (int)res);
        return data;
      }

      decompressedOffset += destSize;
      compressedOffset += srcSize;

      switch(status)
      {
      case LZMA_STATUS_FINISHED_WITH_MARK:
        if(decompressedOffset != offsets[sidInfo->pack_index].decompressed_size)
        {
          DEBUG("lzma decompression failed: hit fin mark on unexpected pos (%u != %u)", (int)decompressedOffset, (int)offsets[sidInfo->pack_index].decompressed_size);
          LzmaDec_Free(&dec, &lzmaAllocFuncs);
          return data;
        }
        LzmaDec_Free(&dec, &lzmaAllocFuncs);

        data.insert(data.end(), decdata.begin()+sidInfo->pack_offset, decdata.begin()+sidInfo->pack_offset+sidInfo->uncompressed_size);
        return data;

      case LZMA_STATUS_NEEDS_MORE_INPUT:
        break;

      case LZMA_STATUS_MAYBE_FINISHED_WITHOUT_MARK:
        if(decompressedOffset == offsets[sidInfo->pack_index].decompressed_size)
        {
          LzmaDec_Free(&dec, &lzmaAllocFuncs);
          data.insert(data.end(), decdata.begin()+sidInfo->pack_offset, decdata.begin()+sidInfo->pack_offset+sidInfo->uncompressed_size);
          return data;
        }
        break;

      case LZMA_STATUS_NOT_FINISHED:
      default:
        DEBUG("lzma decompression failed: unexpected status %u", (int)status);
        LzmaDec_Free(&dec, &lzmaAllocFuncs);
        return data;
      }
    }
  }
#endif
}

uint StilPak::getSongCount(uint sidIndex) const
{
  ASSERT(m_sidsPakEntry.data() != NULL);

  const SidInfo *sidInfo = getSidInfo(sidIndex);
  return sidInfo ? sidInfo->song_count : 0;
}

uint StilPak::getSongDuration(uint sidIndex, uint songIndex) const
{
  ASSERT(m_sidsPakEntry.data() != NULL);

  const SongInfoIndex *songInfoIndex = getSongInfoIndex(sidIndex, songIndex);
  return songInfoIndex ? songInfoIndex->song_duration : 0;
}

namespace {
struct SidHits { uint sidIndex; uint hits; };
struct CompareHits{ bool operator()(const SidHits &a, const SidHits &b){ return a.hits > b.hits; } };
}

std::vector<uint> StilPak::findSids(const char *searchString)
{
  DEBUG("StilPak::findSids: searching for \"%s\"", searchString);

  std::vector<uint> matchingSids;

  if(m_stemmer != NULL)
  {
    std::unique_ptr<char, void (*)(void*)> tokenizedSearchString(strdup(searchString), free);

    std::vector<std::string> tokens;

    char *strtokTmp = NULL;
    for(;;)
    {
      char *token = strtok_r(tokens.empty() ? tokenizedSearchString.get() : NULL, " ,.:!?/\"#$&*+_<>()[]-", &strtokTmp);
      if(token == NULL)
      {
        break;
      }

      const unsigned char *tokenStem = sb_stemmer_stem(m_stemmer, (const unsigned char*)token, strlen(token));

      //DEBUG("StilPak::findSids: token \"%s\" -> \"%s\"", token, tokenStem);
      tokens.push_back((const char*)tokenStem);
    }

    uint sidCount = getSidCount();

    const InvIndexHeader *indexHeader = cast<InvIndexHeader>(m_inverseIndexPakEntry.data(), 0);
    ASSERT(indexHeader->word_count <= 1000000);
    ASSERT(sizeof(InvIndexHeader)+indexHeader->word_count*sizeof(InvIndexIndex) < m_inverseIndexPakEntry.size());

    //DEBUG("StilPak::findSids: examining %u strings", indexHeader->word_count);

    const InvIndexIndex *indexIndex = cast<InvIndexIndex>(indexHeader, sizeof(InvIndexHeader));
    ASSERT(indexIndex[indexHeader->word_count].offset == m_inverseIndexPakEntry.size());
    // todo: check if the last string is nul terminated

    // note: InvIndexIndex->offsets points to the stemmed nul terminated string,
    // just after the string (padded up to 2 bytes) is a list of sid ids,
    // the list ends at the new strings start position.
    // InvIndexIndex has one entry more than indexHeader->word_count, so simplify the code.

    std::vector<SidHits> hits(sidCount, SidHits{0,0});
    for(uint i=0; i<sidCount; i++)
    {
      hits[i].sidIndex = i;
    }

    for(uint i=0; i<indexHeader->word_count; i++)
    {
      ASSERT(indexIndex[i].offset < m_inverseIndexPakEntry.size()-1);
      //for(const std::string &needle : tokens)
      for(auto needleIterator=tokens.begin(); needleIterator!=tokens.end(); needleIterator++)
      {
        const std::string &needle = *needleIterator;

        //DEBUG("token: %s", needle.c_str());

        const char *indexString = cast<char>(indexHeader, indexIndex[i].offset);
        const size_t indexStringLen = strlen(indexString);

        char *fpos = strstr(indexString, needle.c_str());
        if(fpos != NULL)
        {
          int scorebonus = 1;
          if(fpos == indexString)
          {
            if(indexStringLen==needle.size())
              scorebonus = 10; // matched whole word
            else
              scorebonus = 5; // matched beginning of word
          }

          //DEBUG("token: %s found", needle.c_str());

          const InvIndexEntry *begin = (const InvIndexEntry*)(((intptr_t)indexString+indexStringLen+2)&~1);
          const InvIndexEntry *end = cast<InvIndexEntry>(indexHeader, indexIndex[i+1].offset);

          //DEBUG("token: %s has %u sids", needle.c_str(), end-begin);

          for(const InvIndexEntry *i = begin; i!=end; i++)
          {
            //DEBUG("%s: %u %u", indexString, i->sid_index, i->flags);
            ASSERT(i->sid_index < sidCount);

            uint score = 0;
            score += ((i->flags>>INV_FILENAME)&1)*10;
            score += ((i->flags>>INV_SID_NAME)&1)*50;
            score += ((i->flags>>INV_SID_AUTHOR)&1)*20;
            score += ((i->flags>>INV_SID_RELEASED)&1)*10;
            score += ((i->flags>>INV_STIL_NAME)&1)*50; // of the song has the same (or close) stil_name and sid_name, the score might get doubled
            score += ((i->flags>>INV_STIL_TITLE)&1)*10; // original title (if cover)
            score += ((i->flags>>INV_STIL_ARTIST)&1)*10; // original composer (if cover)
            score += ((i->flags>>INV_STIL_COMMENT)&1)*1;
            score += ((i->flags>>INV_STIL_SONG_NAME)&1)*10;
            score += ((i->flags>>INV_STIL_SONG_TITLE)&1)*10;
            score += ((i->flags>>INV_STIL_SONG_AUTHOR)&1)*10;
            score += ((i->flags>>INV_STIL_SONG_ARTIST)&1)*10;
            score += ((i->flags>>INV_STIL_SONG_COMMENT)&1)*1;
            hits[i->sid_index].hits += score*scorebonus;
          }
        }
      }
    }

    std::sort(hits.begin(), hits.end(), CompareHits());
    for(auto i=hits.begin(); i!=hits.end(); i++)
    {
      if(i->hits > 0)
      {
        //DEBUG("%u: %u", i->sidIndex, i->hits);
        matchingSids.push_back(i->sidIndex);
      }
    }
  }

  return matchingSids;
}



//bool StilPak::readPakHeader(int fileHandle, PakHeader *header)
//{
//  if(read(fileHandle, header, sizeof(*header)) != sizeof(*header))
//  {
//    fprintf(stderr, "Failed to read pak header: %s", strerror(errno));
//    return false;
//  }
//
//  if(memcmp(header->magic, "HVSCPAK", 7) != 0)
//  {
//    fprintf(stderr, "Failed to read pak file: unexpected file magic");
//    return false;
//  }
//
//  if(header->format_version != '0')
//  {
//    fprintf(stderr, "Failed to read pak file: unknown file version");
//    return false;
//  }
//
//  return true;
//}

//void *StilPak::readSidInfo(int fileHandle, off_t offset, size_t compressedSize, size_t decompressedSize)
//{
//  if(lseek(fileHandle, offset, SEEK_SET) < 0)
//  {
//    ERROR("lseek(%u) failed: %s", (int)offset, strerror(errno));
//    return NULL;
//  }
//
//  void *sidInfo = malloc(decompressedSize);
//  if(sidInfo == NULL)
//  {
//    ERROR("malloc(%u) failed while reading sid info", (int)decompressedSize);
//    return NULL;
//  }
//
//  if(compressedSize == 0)
//  {
//    // read uncompressed info
//    if(read(fileHandle, sidInfo, decompressedSize) != decompressedSize)
//    {
//      ERROR("Failed to read sid info: %s", strerror(errno));
//      free(sidInfo);
//      return NULL;
//    }
//
//    return sidInfo;
//  }
//  else
//  {
//    uint8_t props[LZMA_PROPS_SIZE];
//    if(read(fileHandle, props, sizeof(props)) != sizeof(props))
//    {
//      ERROR("Failed to read sid lzma props info: %s", strerror(errno));
//      free(sidInfo);
//      return NULL;
//    }
//
//    CLzmaDec dec;
//    LzmaDec_Construct(&dec);
//    LzmaDec_Init(&dec);
//    SRes res = LzmaDec_Allocate(&dec, props, LZMA_PROPS_SIZE, &lzmaAllocFuncs);
//    if(res != SZ_OK)
//    {
//      ERROR("Failed to init lzma decompressor: %u", (int)res);
//      free(sidInfo);
//      return NULL;
//    }
//
//    size_t decompressedOffset = 0;
//    size_t compressedOffset = 0;
//
//    for(;;)
//    {
//      uint8_t tmpbuf[64*1024];
//      size_t srcSize = std::min((size_t)sizeof(tmpbuf), compressedSize-compressedOffset);
//
//      if(read(fileHandle, tmpbuf, srcSize) != srcSize)
//      {
//        ERROR("Failed to read sid lzma data: %s", strerror(errno));
//        free(sidInfo);
//        return NULL;
//      }
//
//      size_t destSize = decompressedSize - decompressedOffset;
//      ELzmaStatus status;
//      SRes res = LzmaDec_DecodeToBuf(&dec,
//          (Byte*)sidInfo+decompressedOffset, &destSize,
//          tmpbuf, &srcSize,
//          LZMA_FINISH_END, &status);
//      if(res != SZ_OK)
//      {
//        ERROR("Failed to init lzma decompressor: %u", (int)res);
//        free(sidInfo);
//        LzmaDec_Free(&dec, &lzmaAllocFuncs);
//        return NULL;
//      }
//
//      decompressedOffset += destSize;
//      compressedOffset += srcSize;
//
//      switch(status)
//      {
//      case LZMA_STATUS_FINISHED_WITH_MARK:
//        if(decompressedOffset != decompressedSize)
//        {
//          ERROR("lzma decompression failed: hit fin mark on unexpected pos (%u != %u)", (int)decompressedOffset, (int)decompressedSize);
//          free(sidInfo);
//          LzmaDec_Free(&dec, &lzmaAllocFuncs);
//          return NULL;
//        }
//        LzmaDec_Free(&dec, &lzmaAllocFuncs);
//        return sidInfo;
//
//      case LZMA_STATUS_NEEDS_MORE_INPUT:
//        break;
//
//      case LZMA_STATUS_MAYBE_FINISHED_WITHOUT_MARK:
//        if(decompressedOffset == decompressedSize)
//        {
//          LzmaDec_Free(&dec, &lzmaAllocFuncs);
//          return sidInfo;
//        }
//        break;
//
//      case LZMA_STATUS_NOT_FINISHED:
//      default:
//        ERROR("lzma decompression failed: unexpected status %u", (int)status);
//        free(sidInfo);
//        LzmaDec_Free(&dec, &lzmaAllocFuncs);
//        return NULL;
//      }
//    }
//  }
//
//  return NULL;
//}

const SidInfo *StilPak::getSidInfo(uint sidIndex) const
{
  ASSERT(m_sidsPakEntry.data() != NULL);
  if(m_sidsPakEntry.data() == NULL)
  {
    ERROR("StilPak::getSidInfo: called with NULL pak entry");
    return NULL;
  }

  const SidInfoHeader *header = cast<SidInfoHeader>(m_sidsPakEntry.data(), 0);
  ASSERT(sidIndex < header->sid_count);
  if(sidIndex >= header->sid_count)
  {
    ERROR("StilPak::getSidInfo: invalid sid index %u (max=%u)", sidIndex, header->sid_count);
    return NULL;
  }

  const SidInfoIndex *sidInfoIndices = cast<SidInfoIndex>(header, sizeof(SidInfoHeader));

  const SidInfo *sidInfo = cast<SidInfo>(sidInfoIndices, sidInfoIndices[sidIndex].offset);
  return sidInfo;
}

const SongInfoIndex *StilPak::getSongInfoIndex(uint sidIndex, uint songIndex) const
{
  const SidInfo *sidInfo = getSidInfo(sidIndex);
  if(sidInfo == NULL)
  {
    return NULL;
  }

  ASSERT(songIndex < sidInfo->song_count);
  if(songIndex >= sidInfo->song_count)
  {
    ERROR("StilPak::getSidInfo: invalid song index %u for sid %u (max=%u)", songIndex, sidIndex, sidInfo->song_count);
    return NULL;
  }

  const SongInfoIndex *index = cast<SongInfoIndex>(sidInfo, sizeof(SidInfo) + songIndex*sizeof(SongInfoIndex));
  return index;
}

const char *StilPak::getString(uint stringOffset) const
{
  const StringPoolHeader *stringPoolHeader = cast<StringPoolHeader>(m_stringsPakEntry.data(), 0);
  const char *stringPool = cast<char>(stringPoolHeader, sizeof(StringPoolHeader));
  return stringPool + stringOffset;
}


//bool StilPak::verifySidInfo(const void *sidInfo, size_t sidInfoSize)
//{
//  const void *sidInfoEnd = cast<void>(sidInfo, sidInfoSize);
//
//  if(sizeof(SidInfoHeader) > sidInfoSize)
//  {
//    ERROR("*** error: verifySidInfo, buffer too short");
//    return false;
//  }
//
//  const TuneInfoHeader *header = cast<TuneInfoHeader>(sidInfo, 0);
//  if(header->tune_count<40000 || header->tune_count>45000)
//  {
//    ERROR("*** warning: verifyTuneInfo, unexpected tune count %u", header->tune_count);
//  }
//
//  if(header->tuneinfo_offset+header->tune_count*sizeof(TuneInfoIndex) > sidInfoSize)
//  {
//    ERROR("*** error: verifyTuneInfo, buffer too short");
//    return false;
//  }
//
//  // verify string pool
//
//  if(header->stringpoll_offset+sizeof(StringPoolHeader) > sidInfoSize)
//  {
//    ERROR("*** error: verifyTuneInfo, buffer too short");
//    return false;
//  }
//
//  const StringPoolHeader *stringPoolHeader = cast<StringPoolHeader>(sidInfo, header->stringpoll_offset);
//  if(stringPoolHeader->string_count <10000 || stringPoolHeader->string_count>100000)
//  {
//    ERROR("*** warning: verifyTuneInfo, unexpected string count %u", stringPoolHeader->string_count);
//  }
//  const char *stringPool = cast<char>(stringPoolHeader, sizeof(StringPoolHeader));
//  const char *stringPoolEnd = stringPool;
//  {
//    uint nulCount = 0;
//    for(const char *string = stringPool; string<sidInfoEnd; string++)
//    {
//      if(*string == '\0')
//      {
//        nulCount += 1;
//        if(nulCount == stringPoolHeader->string_count)
//        {
//          stringPoolEnd = string + 1;
//          break;
//        }
//      }
//    }
//    if(nulCount != stringPoolHeader->string_count)
//    {
//      ERROR("*** error: verifyTuneInfo, only found %u of %u strings", nulCount, stringPoolHeader->string_count);
//      return false;
//    }
//  }
//
//  // verify tunes
//
//  const TuneInfoIndex *tuneInfoIndices = cast<TuneInfoIndex>(sidInfo, header->tuneinfo_offset);
//  for(uint i=0; i<header->tune_count; i++)
//  {
//    const TuneInfo *tuneInfo = cast<TuneInfo>(tuneInfoIndices, tuneInfoIndices[i].offset);
//    if(tuneInfo<sidInfo || tuneInfo+1>=sidInfoEnd)
//    {
//      ERROR("*** error: verifyTuneInfo, buffer too short");
//      return false;
//    }
//
//#define CHECK_STRING(STROFF) do { \
//  if(stringPool+STROFF>=stringPoolEnd) { \
//    ERROR("*** error: verifyTuneInfo, invalid string offset %s: %u", #STROFF, STROFF); \
//    return false; \
//  } } \
//while(0)
//
//    // todo pack_index
//    // todo pack_offset
//    // todo song_count
//    for(int i=0; i<6; i++)
//      CHECK_STRING(tuneInfo->file_path_stri[0]);
//    CHECK_STRING(tuneInfo->sid_name_stri);
//    CHECK_STRING(tuneInfo->sid_author_stri);
//    CHECK_STRING(tuneInfo->sid_released_stri);
//    CHECK_STRING(tuneInfo->stil_name_stri);
//    CHECK_STRING(tuneInfo->stil_title_stri);
//    CHECK_STRING(tuneInfo->stil_artist_stri);
//    CHECK_STRING(tuneInfo->stil_comment_stri);
//
//    const SongInfoIndex *songInfoOffsets = cast<SongInfoIndex>(tuneInfo, sizeof(*tuneInfo));
//    if(songInfoOffsets+tuneInfo->song_count<sidInfo || songInfoOffsets+tuneInfo->song_count>=sidInfoEnd)
//    {
//      ERROR("*** error: verifyTuneInfo, buffer too short");
//      return false;
//    }
//
//    for(uint songIndex=0; songIndex<tuneInfo->song_count; songIndex++)
//    {
//      //printf("%d %d/%d %d (dur:%d)\n", i, songIndex, tuneInfo->song_count, songInfoOffsets[songIndex].offset, songInfoOffsets[songIndex].song_duration);
//      if(songInfoOffsets[songIndex].offset != 0)
//      {
//        const SongInfo *songInfo = cast<SongInfo>(songInfoOffsets, songInfoOffsets[songIndex].offset);
//        if(songInfo<sidInfo || songInfo+1>=sidInfoEnd)
//        {
//          ERROR("*** error: verifyTuneInfo, buffer too short");
//          return false;
//        }
//
//        CHECK_STRING(songInfo->stil_name_stri);
//        CHECK_STRING(songInfo->stil_title_stri);
//        CHECK_STRING(songInfo->stil_author_stri);
//        CHECK_STRING(songInfo->stil_artist_stri);
//        CHECK_STRING(songInfo->stil_comment_stri);
//      }
//    }
//  }
//
//  return true;
//}























