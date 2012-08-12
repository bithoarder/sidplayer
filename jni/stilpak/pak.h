#ifndef SIDPLAYER_PAK_H
#define SIDPLAYER_PAK_H

#include <cstdint>
#include <sys/types.h>

///////////////////////////////////////////////////////////////////////////////

template<typename T> const T *cast(const void *base, size_t offset)
{
  return reinterpret_cast<const T *>(reinterpret_cast<const uint8_t*>(base) + offset);
}

///////////////////////////////////////////////////////////////////////////////

class PakEntry
{
  friend class Pak;
public:
  PakEntry();
  PakEntry(PakEntry &&that);
  ~PakEntry();

  PakEntry &operator=(PakEntry &&that);

  const void *data() const { return m_data; }
  size_t size() const { return m_dataSize; }

private:
  PakEntry(const void *data, size_t dataSize, bool freeOnDelete);
  PakEntry(const PakEntry &);
  PakEntry &operator=(const PakEntry &);

  const void *m_data;
  size_t m_dataSize;
  bool  m_freeOnDelete;
};

///////////////////////////////////////////////////////////////////////////////

class Pak
{
public:
  Pak();
  ~Pak();

  bool open(const void *pakData, size_t pakDataSize); // borrows the memory

  uint size() const;
  PakEntry read(uint32_t tag) const;

private:
  int findEntry(uint32_t tag) const; // returns -1 if tag is not found

private:
  const void *m_pakData;
  size_t m_pakDataSize;
};

///////////////////////////////////////////////////////////////////////////////
#endif
