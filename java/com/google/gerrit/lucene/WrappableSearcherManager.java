package com.google.gerrit.lucene;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterDirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.Directory;

/**
 * Utility class to safely share {@link IndexSearcher} instances across multiple threads, while
 * periodically reopening. This class ensures each searcher is closed only once all threads have
 * finished using it.
 *
 * <p>Use {@link #acquire} to obtain the current searcher, and {@link #release} to release it, like
 * this:
 *
 * <pre class="prettyprint">
 * IndexSearcher s = manager.acquire();
 * try {
 *   // Do searching, doc retrieval, etc. with s
 * } finally {
 *   manager.release(s);
 * }
 * // Do not use s after this!
 * s = null;
 * </pre>
 *
 * <p>In addition you should periodically call {@link #maybeRefresh}. While it's possible to call
 * this just before running each query, this is discouraged since it penalizes the unlucky queries
 * that need to refresh. It's better to use a separate background thread, that periodically calls
 * {@link #maybeRefresh}. Finally, be sure to call {@link #close} once you are done.
 *
 * @see SearcherFactory
 * @lucene.experimental
 */
// This file was copied from:
// https://github.com/apache/lucene-solr/blob/lucene_solr_5_0/lucene/core/src/java/org/apache/lucene/search/SearcherManager.java
// The only change (other than class name and import fixes)
// is to skip the check in getSearcher that searcherFactory.newSearcher wraps
// the provided searcher exactly.
final class WrappableSearcherManager extends ReferenceManager<IndexSearcher> {

  private final SearcherFactory searcherFactory;

  /**
   * Creates and returns a new SearcherManager from the given {@link IndexWriter}.
   *
   * @param writer the IndexWriter to open the IndexReader from.
   * @param applyAllDeletes If <code>true</code>, all buffered deletes will be applied (made
   *     visible) in the {@link IndexSearcher} / {@link DirectoryReader}. If <code>false</code>, the
   *     deletes may or may not be applied, but remain buffered (in IndexWriter) so that they will
   *     be applied in the future. Applying deletes can be costly, so if your app can tolerate
   *     deleted documents being returned you might gain some performance by passing <code>false
   *     </code>. See {@link DirectoryReader#openIfChanged(DirectoryReader, IndexWriter, boolean)}.
   * @param searcherFactory An optional {@link SearcherFactory}. Pass <code>null</code> if you don't
   *     require the searcher to be warmed before going live or other custom behavior.
   * @throws IOException if there is a low-level I/O error
   */
  WrappableSearcherManager(
      IndexWriter writer, boolean applyAllDeletes, SearcherFactory searcherFactory)
      throws IOException {
    // TODO(davido): Make it configurable
    // If true, new deletes will be written down to index files instead of carried over from writer
    // to reader directly in heap
    boolean writeAllDeletes = false;
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    current =
        getSearcher(
            searcherFactory, DirectoryReader.open(writer, applyAllDeletes, writeAllDeletes));
  }

  /**
   * Creates and returns a new SearcherManager from the given {@link Directory}.
   *
   * @param dir the directory to open the DirectoryReader on.
   * @param searcherFactory An optional {@link SearcherFactory}. Pass <code>null</code> if you don't
   *     require the searcher to be warmed before going live or other custom behavior.
   * @throws IOException if there is a low-level I/O error
   */
  WrappableSearcherManager(Directory dir, SearcherFactory searcherFactory) throws IOException {
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    current = getSearcher(searcherFactory, DirectoryReader.open(dir));
  }

  /**
   * Creates and returns a new SearcherManager from an existing {@link DirectoryReader}. Note that
   * this steals the incoming reference.
   *
   * @param reader the DirectoryReader.
   * @param searcherFactory An optional {@link SearcherFactory}. Pass <code>null</code> if you don't
   *     require the searcher to be warmed before going live or other custom behavior.
   * @throws IOException if there is a low-level I/O error
   */
  WrappableSearcherManager(DirectoryReader reader, SearcherFactory searcherFactory)
      throws IOException {
    if (searcherFactory == null) {
      searcherFactory = new SearcherFactory();
    }
    this.searcherFactory = searcherFactory;
    this.current = getSearcher(searcherFactory, reader);
  }

  @Override
  protected void decRef(IndexSearcher reference) throws IOException {
    reference.getIndexReader().decRef();
  }

  @Override
  protected IndexSearcher refreshIfNeeded(IndexSearcher referenceToRefresh) throws IOException {
    final IndexReader r = referenceToRefresh.getIndexReader();
    assert r instanceof DirectoryReader
        : "searcher's IndexReader should be a DirectoryReader, but got " + r;
    final IndexReader newReader = DirectoryReader.openIfChanged((DirectoryReader) r);
    if (newReader == null) {
      return null;
    }
    return getSearcher(searcherFactory, newReader);
  }

  @Override
  protected boolean tryIncRef(IndexSearcher reference) {
    return reference.getIndexReader().tryIncRef();
  }

  @Override
  protected int getRefCount(IndexSearcher reference) {
    return reference.getIndexReader().getRefCount();
  }

  /**
   * Returns <code>true</code> if no changes have occured since this searcher ie. reader was opened,
   * otherwise <code>false</code>.
   *
   * @see DirectoryReader#isCurrent()
   */
  public boolean isSearcherCurrent() throws IOException {
    final IndexSearcher searcher = acquire();
    try {
      final IndexReader r = searcher.getIndexReader();
      assert r instanceof DirectoryReader
          : "searcher's IndexReader should be a DirectoryReader, but got " + r;
      return ((DirectoryReader) r).isCurrent();
    } finally {
      release(searcher);
    }
  }

  /**
   * Expert: creates a searcher from the provided {@link IndexReader} using the provided {@link
   * SearcherFactory}. NOTE: this decRefs incoming reader on throwing an exception.
   */
  @SuppressWarnings({"resource", "ReferenceEquality"})
  public static IndexSearcher getSearcher(SearcherFactory searcherFactory, IndexReader reader)
      throws IOException {
    boolean success = false;
    final IndexSearcher searcher;
    try {
      searcher = searcherFactory.newSearcher(reader, null);
      // Modification for Gerrit: Allow searcherFactory to transitively wrap the
      // provided reader.
      IndexReader unwrapped = searcher.getIndexReader();
      while (true) {
        if (unwrapped == reader) {
          break;
        } else if (unwrapped instanceof FilterDirectoryReader) {
          unwrapped = ((FilterDirectoryReader) unwrapped).getDelegate();
        } else if (unwrapped instanceof FilterLeafReader) {
          unwrapped = ((FilterLeafReader) unwrapped).getDelegate();
        } else {
          break;
        }
      }

      if (unwrapped != reader) {
        throw new IllegalStateException(
            "SearcherFactory must wrap the provided reader (got "
                + searcher.getIndexReader()
                + " but expected "
                + reader
                + ")");
      }
      success = true;
    } finally {
      if (!success) {
        reader.decRef();
      }
    }
    return searcher;
  }
}
