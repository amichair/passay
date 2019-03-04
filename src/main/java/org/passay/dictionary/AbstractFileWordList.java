/* See LICENSE for licensing and NOTICE for copyright. */
package org.passay.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.LongBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * Common implementation for file based word lists.
 *
 * @author  Middleware Services
 */
public abstract class AbstractFileWordList extends AbstractWordList
{


  /** Default index size as percent of file size. */
  public static final int DEFAULT_INDEX_SIZE_PERCENT = 5;

  /**
   * Default index size as percent of file size
   * @deprecated renamed to {@link #DEFAULT_INDEX_SIZE_PERCENT}.
   */
  @Deprecated
  public static final int DEFAULT_CACHE_PERCENT = DEFAULT_INDEX_SIZE_PERCENT;

  /** File containing words. */
  protected final RandomAccessFile file;

  /** Number of words in the file. */
  protected int size;

  /** An index of word indices to their file positions. */
  private Index index;

  /** Charset decoder. */
  private final CharsetDecoder charsetDecoder;

  /** Buffer to hold word read from file. */
  private final ByteBuffer wordBuf = ByteBuffer.allocate(256);

  /** Buffer to hold decoded word read from file. */
  private final CharBuffer charBuf = CharBuffer.allocate(wordBuf.capacity() * 4);

  /** Current position into backing file. */
  private long position;


  /**
   * Creates a new abstract file word list from the supplied file.
   *
   * @param  raf  File containing words, one per line.
   * @param  caseSensitive  Set to true to create case-sensitive word list, false otherwise.
   * @param  decoder  Charset decoder for converting file bytes to characters
   */
  public AbstractFileWordList(final RandomAccessFile raf, final boolean caseSensitive, final CharsetDecoder decoder)
  {
    file = raf;
    charsetDecoder = decoder;
    comparator = caseSensitive ? WordLists.CASE_SENSITIVE_COMPARATOR : WordLists.CASE_INSENSITIVE_COMPARATOR;
  }


  @Override
  public String get(final int i)
  {
    checkRange(i);
    try {
      return readWord(i);
    } catch (IOException e) {
      throw new RuntimeException("Error reading from file backing word list", e);
    }
  }


  @Override
  public int size()
  {
    return size;
  }


  /**
   * Returns the file backing this list.
   *
   * @return  random access file that is backing this list
   */
  public RandomAccessFile getFile()
  {
    return file;
  }


  /**
   * Closes the underlying file and releases the word list resources.
   *
   * @throws  IOException  if an error occurs closing the file
   */
  public void close() throws IOException
  {
    synchronized (index) {
      file.close();
    }
    index = null;
  }


  /**
   * Initializes this word list by reading all words from the backing file,
   * verifying that they are all ordered according to the comparator order,
   * counting the total number of words in the list, and constructing
   * an index for efficiently finding words in the file by their list index.
   *
   * @param  indexSizePercent  index size as percentage of file size.
   * @param  allocateDirect  whether buffers should be allocated with {@link ByteBuffer#allocateDirect(int)}
   *
   * @throws  IllegalArgumentException  if index size percent is out of range
   *          or the words are not sorted correctly according to the comparator
   * @throws  IOException  on I/O errors reading file data.
   */
  protected void initialize(final int indexSizePercent, final boolean allocateDirect) throws IOException
  {
    index = new Index(file.length(), indexSizePercent, allocateDirect);
    FileWord word;
    FileWord prev = null;
    synchronized (index) {
      seek(0);
      while ((word = readNextWord()) != null) {
        if (prev != null && comparator.compare(word.word, prev.word) < 0) {
          throw new IllegalArgumentException("File is not sorted correctly for this comparator");
        }
        prev = word;
        index.put(size++, word.offset);
      }
      index.initialized = true;
    }
  }


  /**
   * Reads the i-th word of the word list from the file.
   * <p>
   * The index is used to find the nearest preceding word position that has an index entry,
   * and from there the words are read sequentially until the i-th one is reached.
   *
   * @param  i  ith word in the word list
   *
   * @return  word at the supplied index
   *
   * @throws  IOException  on I/O errors
   */
  protected String readWord(final int i) throws IOException
  {
    FileWord word;
    synchronized (index) {
      final Index.Entry entry = index.get(i);
      int j = entry.index;
      seek(entry.position);
      do {
        word = readNextWord();
      } while (j++ < i && word != null);
      return word != null ? word.word : null;
    }
  }


  /**
   * Sets the position within the backing file from which
   * the next next read operation will read data.
   *
   * @param offset byte offset into file.
   *
   * @throws  IOException  on I/O errors seeking.
   */
  protected abstract void seek(long offset) throws IOException;


  /**
   * Returns the buffer providing the backing file data.
   *
   * @return  Buffer around backing file.
   */
  protected abstract ByteBuffer buffer();


  /**
   * Fills the buffer with data from the backing file (from the current read position).
   * This method may be a no-op if the entire file contents fits in the buffer.
   *
   * @throws  IOException  on I/O errors filling buffer.
   */
  protected abstract void fill() throws IOException;


  /**
   * Reads the word at the current position in the backing file,
   * and advances the position to the beginning of the following word.
   *
   * @return  the read word and its starting byte offset within the file.
   *
   * @throws  IOException  on I/O errors reading file data.
   */
  private FileWord readNextWord() throws IOException
  {
    wordBuf.clear();
    long start = position;
    while (hasRemaining()) {
      final byte b = buffer().get();
      position++;
      if (b == '\n' || b == '\r') {
        // Ignore leading line termination characters
        if (wordBuf.position() == 0) {
          start++;
          continue;
        }
        break;
      }
      wordBuf.put(b);
    }
    if (wordBuf.position() == 0) {
      return null;
    }

    charBuf.clear();
    wordBuf.flip();
    final CoderResult result = charsetDecoder.decode(wordBuf, charBuf, true);
    if (result.isError()) {
      result.throwException();
    }
    return new FileWord(charBuf.flip().toString(), start);
  }


  /**
   * Returns whether there is additional unread file data available.
   * <p>
   * If the buffer does not contain any more unread data, an attempt
   * is made to read additional data from the file into the buffer.
   *
   * @return  True if there is any more data to read from the buffer, false otherwise.
   *
   * @throws  IOException  on I/O errors reading file data.
   */
  private boolean hasRemaining() throws IOException
  {
    if (buffer().hasRemaining()) {
      return true;
    }
    fill();
    return buffer().hasRemaining();
  }


  @Override
  public String toString()
  {
    return
      String.format(
        "%s@%h::size=%s,index=%s,charsetDecoder=%s",
        getClass().getName(),
        hashCode(),
        size,
        index,
        charsetDecoder);
  }


  /**
   * Data structure containing a word and its starting offset within the file.
   */
  protected static class FileWord
  {

    // CheckStyle:VisibilityModifier OFF
    /** Word read from backing file. */
    String word;

    /** Byte offset into file where word begins. */
    long offset;
    // CheckStyle:VisibilityModifier ON


    /**
     * Creates a new instance with a word and offset.
     *
     * @param  s  word.
     * @param  position  byte offset into file.
     */
    FileWord(final String s, final long position)
    {
      word = s;
      offset = position;
    }
  }


  /**
   * Index of word indices in the word list and their corresponding byte offsets in the backing file.
   * The index itself is of limited size (specified as a percentage of the total file size), and so
   * does not contain the position of every word in the list. However, for words that are not in
   * the index, it can return the offset of the nearest preceding word that is in the index,
   * and from that position the words can be read sequentially from the file until the desired
   * word is reached.
   * <p>
   * The implementation basically divides the list into equal-sized blocks,
   * and stores the first word of each block in the index. For any word it is then easy
   * to calculate which block it belongs to and get the corresponding first entry in the block.
   * <p>
   * The index itself is just a buffer of long values, where the i-th value in the buffer is
   * the file position of the first word in the i-th block of words.
   */
  private static class Index
  {

    /** An index entry containing a word index and its starting byte offset in the file. */
    static class Entry
    {
      // CheckStyle:VisibilityModifier OFF
      /** Word index within the word list. */
      int index;

      /** Word starting position in the file. */
      long position;
      // CheckStyle:VisibilityModifier ON


      /**
       * Creates a new Index entry.
       *
       * @param  i  word index within the word list.
       * @param  pos  word starting position in the file.
       */
      Entry(final int i, final long pos)
      {
        index = i;
        position = pos;
      }
    }

    /** Map of word indices to the byte offset in the file where the word starts. */
    private LongBuffer map;

    /** Size of block for which there is a representative entry. */
    private int blockSize;

    /** Whether to allocate a direct buffer. */
    private boolean allocateDirect;

    /** Whether this index has been initialized. */
    private boolean initialized;


    /**
     * Creates a new index instance.
     *
     * @param  fileSize  Size of file in bytes.
     * @param  indexSizePercent  index size as percentage of file size
     * @param  direct  Whether to allocate a direct byte buffer.
     *
     * @throws  IllegalArgumentException  if indexSizePercent is not in the range 0-100
     *          or if the computed index size exceeds {@link Integer#MAX_VALUE}
     */
    Index(final long fileSize, final int indexSizePercent, final boolean direct)
    {
      if (indexSizePercent < 0 || indexSizePercent > 100) {
        throw new IllegalArgumentException("indexSizePercent must be between 0 and 100 inclusive");
      }
      final long indexSize = fileSize * indexSizePercent / 100;
      // round size up to next multiple of 8 (size of long)
      final long bufferSize = (indexSize | (Long.BYTES - 1)) + 1;
      if (bufferSize > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Index size limit exceeded. Try reducing the index size.");
      }
      final long indexEntries = bufferSize / Long.BYTES;
      final long fileWords = fileSize / 4;
      allocateDirect = direct;
      blockSize = indexEntries == 0 ? 0 : (int) (fileWords / indexEntries);
      map = allocateDirect
        ? ByteBuffer.allocateDirect((int) bufferSize).asLongBuffer()
        : ByteBuffer.allocate((int) bufferSize).asLongBuffer();
    }


    /**
     * Adds an index entry that maps a word index within the word list to to its starting position in the file.
     * This method must be called in the order of the word indices. Depending on the file size and index size,
     * some entries will actually be added to the index and some will not.
     *
     * @param  i  Word at index.
     * @param  position  Byte offset into backing for file where word starts.
     * @throws IllegalStateException if the cache has already been initialized
     */
    void put(final int i, final long position)
    {
      if (initialized) {
        throw new IllegalStateException("Index already initialized, put is not allowed");
      }
      if (blockSize != 0 && i % blockSize == 0) {
        map.put(position);
      }
    }


    /**
     * Returns the starting position within the file of the word at the given word list index,
     * or of the nearest preceding word for which there exists an index entry.
     *
     * @param  i  index within the word list of the requested word.
     *
     * @return  the index entry of the word if it exists, or its nearest preceding entry.
     */
    Entry get(final int i)
    {
      final int j = blockSize == 0 ? 0 : i / blockSize;
      return new Entry(j * blockSize, map.get(j));
    }


    @Override
    public String toString()
    {
      return
        String.format(
          "%s@%h::size=%s,blockSize=%s,allocateDirect=%s,initialized=%s",
          getClass().getSimpleName(),
          hashCode(),
          map.capacity(),
          blockSize,
          allocateDirect,
          initialized);
    }
  }
}
