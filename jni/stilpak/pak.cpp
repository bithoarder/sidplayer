#include "pak.h"

#include "../lzma/LzmaDec.h"
#include "../lzma/Types.h"

#include <cstddef>
#include <cassert>
#include <cstring>
#include <algorithm>
#include <arpa/inet.h>

///////////////////////////////////////////////////////////////////////////////

#ifdef ANDROID
#include <android/log.h>
#define DEBUG(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, "stilpak", fmt, ##args)
#define ERROR(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, "stilpak", fmt, ##args)
#else
#define DEBUG(fmt, args...) fprintf(stderr, fmt "\n", ##args)
#define ERROR(fmt, args...) fprintf(stderr, fmt "\n", ##args)
#endif

namespace {
void *lzmaAlloc(void *user, size_t size) { return malloc(size); }
void lzmaFree(void *user, void *address) { return free(address); }
static ISzAlloc lzmaAllocFuncs = { lzmaAlloc, lzmaFree };
}

///////////////////////////////////////////////////////////////////////////////

struct Header
{
  uint32_t magic; // 'PAK0'
  uint32_t count;
};

struct Entry
{
  uint32_t tag;
  uint32_t offset;
};

struct LzmaHeader
{
  char magic[4];
  uint32_t decompressedSize;
  uint8_t props[LZMA_PROPS_SIZE];
  uint8_t data[0];
};

///////////////////////////////////////////////////////////////////////////////

PakEntry::PakEntry()
{
  //DEBUG("PAK(%p):ctor()", this);

  m_data = NULL;
  m_dataSize = 0;
  m_freeOnDelete = false;
}

PakEntry::PakEntry(const void *data, size_t dataSize, bool freeOnDelete)
{
  //DEBUG("PAK(%p):ctor(%p, %zu, %s)", this, data, dataSize, freeOnDelete?"free":"dontfree");

  m_data = data;
  m_dataSize = dataSize;
  m_freeOnDelete = freeOnDelete;
}

PakEntry::PakEntry(PakEntry &&that)
{
  //DEBUG("PAK(%p):ctor(move(%p) (%p, %zu, %s))", this, &that, that.m_data, that.m_dataSize, that.m_freeOnDelete?"free":"dontfree");

  m_data = that.m_data;
  m_dataSize = that.m_dataSize;
  m_freeOnDelete = that.m_freeOnDelete;

  that.m_data = NULL;
  that.m_dataSize = 0;
  that.m_freeOnDelete = false;
}

PakEntry &PakEntry::operator=(PakEntry &&that)
{
  //DEBUG("PAK(%p):=(move(%p) (%p, %zu, %s))", this, &that, that.m_data, that.m_dataSize, that.m_freeOnDelete?"free":"dontfree");

  if(m_freeOnDelete)
  {
    free(const_cast<void*>(m_data));
  }

  m_data = that.m_data;
  m_dataSize = that.m_dataSize;
  m_freeOnDelete = that.m_freeOnDelete;

  that.m_data = NULL;
  that.m_dataSize = 0;
  that.m_freeOnDelete = false;

  return *this;
}

PakEntry::~PakEntry()
{
  //DEBUG("PAK(%p):dtor(%p, %zu, %s))", this, m_data, m_dataSize, m_freeOnDelete?"free":"dontfree");
  if(m_freeOnDelete)
  {
    free(const_cast<void*>(m_data));
  }
}

///////////////////////////////////////////////////////////////////////////////

Pak::Pak()
{
  m_pakData = NULL;
  m_pakDataSize = 0;
}

Pak::~Pak()
{
}

bool Pak::open(const void *pakData, size_t pakDataSize)
{
  assert(m_pakData == NULL);
  assert(pakData!=NULL && pakDataSize>=sizeof(Header));

  DEBUG("Pak::open(%p, %zu)", pakData, pakDataSize);

  if(m_pakData!=NULL || pakData==NULL || pakDataSize<sizeof(Header))
  {
    ERROR("Pak::open: already open");
    return false;
  }

  const Header *header = reinterpret_cast<const Header*>(pakData);
  if(header->magic != htonl('PAK0'))
  {
    ERROR("Pak::open: unknown magic 0x%08x", header->magic);
    return false;
  }

  if(header->count > 100000)
  {
    ERROR("Pak::open: unexpected count (%u)", header->count);
    return false;
  }

  DEBUG("Pak::open: contains %u entries", header->count);


  m_pakData = pakData;
  m_pakDataSize = pakDataSize;

  return true;
}

uint Pak::size() const
{
  const Header *header = reinterpret_cast<const Header*>(m_pakData);
  return header->count;
}

PakEntry Pak::read(uint32_t tag) const
{
  int entryIndex = findEntry(tag);
  if(entryIndex < 0)
  {
    return PakEntry(NULL, 0, false);
  }

  const Header *header = reinterpret_cast<const Header*>(m_pakData);
  const Entry *entry = reinterpret_cast<const Entry*>(header + 1) + entryIndex;

  size_t entrySize = (entry+1)->offset - entry->offset;
  if(entry->offset>m_pakDataSize || entrySize>=4*1024*1024 || entry->offset+entrySize>m_pakDataSize)
  {
    ERROR("Pak::findEntry: invalid entry offset/size (tag=0x%08x index=%u offset=%u size=%u)", tag, entryIndex, entry->offset, entrySize);
    return PakEntry(NULL, 0, false);
  }

  const uint8_t *entryData = cast<uint8_t>(m_pakData, entry->offset);
  const LzmaHeader *lzmaHeader = cast<LzmaHeader>(entryData, 0);

  if(memcmp(lzmaHeader->magic, "lzma", 4) != 0)
  {
    DEBUG("Pak::findEntry: found uncompressed 0x%08x at %u size %zu", tag, entry->offset, entrySize);
    return PakEntry(entryData, entrySize, false);
  }

  if(lzmaHeader->decompressedSize<entrySize/2 || lzmaHeader->decompressedSize>=8*1024*1024)
  {
    ERROR("Pak::findEntry: invalid uncompressed size (tag=0x%08x size=%u decompressedsize=%u)", tag, entrySize, lzmaHeader->decompressedSize);
    return PakEntry(NULL, 0, false);
  }

  CLzmaDec dec;
  LzmaDec_Construct(&dec);
  LzmaDec_Init(&dec);
  SRes res = LzmaDec_Allocate(&dec, lzmaHeader->props, LZMA_PROPS_SIZE, &lzmaAllocFuncs);
  if(res != SZ_OK)
  {
    ERROR("Pak::findEntry: LzmaDec_Allocate failed: %d (tag=0x%08x)", res, tag);
    return PakEntry(NULL, 0, false);
  }

  void *decdata = malloc(lzmaHeader->decompressedSize);
  if(decdata == NULL)
  {
    ERROR("Pak::findEntry: LzmaDec_Allocate failed to allocate %u bytes", lzmaHeader->decompressedSize);
    LzmaDec_Free(&dec, &lzmaAllocFuncs);
    return PakEntry(NULL, 0, false);
  }

  size_t destSize = lzmaHeader->decompressedSize;
  size_t srcSize = entrySize - sizeof(lzmaHeader);
  ELzmaStatus status;
  res = LzmaDec_DecodeToBuf(&dec,
      (Byte*)decdata, &destSize,
      lzmaHeader->data, &srcSize,
      LZMA_FINISH_END, &status);

  if(res != SZ_OK)
  {
    ERROR("Pak::findEntry: LzmaDec_DecodeToBuf failed: %d (tag=0x%08x)", res, tag);
    LzmaDec_Free(&dec, &lzmaAllocFuncs);
    free(decdata);
    return PakEntry(NULL, 0, false);
  }

  if(!(status==LZMA_STATUS_FINISHED_WITH_MARK || (status==LZMA_STATUS_MAYBE_FINISHED_WITHOUT_MARK && destSize!=lzmaHeader->decompressedSize)))
  {
    ERROR("Pak::findEntry: LzmaDec_DecodeToBuf ended with unexpected status: %d (tag=0x%08x)", status, tag);
    LzmaDec_Free(&dec, &lzmaAllocFuncs);
    free(decdata);
    return PakEntry(NULL, 0, false);
  }

  if(destSize != lzmaHeader->decompressedSize)
  {
    ERROR("Pak::findEntry: LzmaDec_DecodeToBuf failed to decompress whole buffer: %zu!=%u (tag=0x%08x)", destSize, lzmaHeader->decompressedSize, tag);
    LzmaDec_Free(&dec, &lzmaAllocFuncs);
    free(decdata);
    return PakEntry(NULL, 0, false);
  }

  LzmaDec_Free(&dec, &lzmaAllocFuncs);
  return PakEntry(decdata, lzmaHeader->decompressedSize, true);
}

struct FindTag
{
  bool operator()(const Entry &entry, uint32_t tag)
  {
    return ntohl(entry.tag) < tag;
  }
};

int Pak::findEntry(uint32_t tag) const
{
  const Header *header = reinterpret_cast<const Header*>(m_pakData);
  const Entry *begin = reinterpret_cast<const Entry*>(header + 1);
  const Entry *end = begin + header->count;

  const Entry *i = std::lower_bound(begin, end, tag, FindTag());
  if(i==end || htonl(i->tag)!=tag)
  {
    ERROR("Pak::findEntry: unknown tag 0x%08x", tag);
    return -1;
  }

  return i-begin;
}

