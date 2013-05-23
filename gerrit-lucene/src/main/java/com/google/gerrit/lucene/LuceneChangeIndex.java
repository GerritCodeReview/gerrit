// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.lucene;

import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_CHANGE;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Secondary index implementation using Apache Lucene.
 * <p>
 * Writes are managed using a single {@link IndexWriter} per process, committed
 * aggressively. Reads use {@link SearcherManager} and periodically refresh,
 * though there may be some lag between a committed write and it showing up to
 * other threads' searchers.
 */
@Singleton
public class LuceneChangeIndex implements ChangeIndex, LifecycleListener {
  private static final Logger log =
      LoggerFactory.getLogger(LuceneChangeIndex.class);

  private static final Version VERSION = Version.LUCENE_43;

  private final FillArgs fillArgs;
  private final Directory dir;
  private final IndexWriter writer;
  private final SearcherManager searcherManager;

  @Inject
  LuceneChangeIndex(SitePaths sitePaths,
      FillArgs fillArgs) throws IOException {
    this.fillArgs = fillArgs;
    dir = FSDirectory.open(new File(sitePaths.index_dir, "changes"));
    IndexWriterConfig writerConfig =
        new IndexWriterConfig(VERSION, new StandardAnalyzer(VERSION));
    writerConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
    writer = new IndexWriter(dir, writerConfig);
    searcherManager = new SearcherManager(writer, true, null);
  }

  @Override
  public void start() {
    // Do nothing.
  }

  @Override
  public void stop() {
    try {
      searcherManager.close();
    } catch (IOException e) {
      log.warn("error closing Lucene searcher", e);
    }
    try {
      writer.close(true);
    } catch (IOException e) {
      log.warn("error closing Lucene writer", e);
    }
    try {
      dir.close();
    } catch (IOException e) {
      log.warn("error closing Lucene directory", e);
    }
  }

  @Override
  public void insert(ChangeData cd) throws IOException {
    writer.addDocument(toDocument(cd));
    commit();
  }

  @Override
  public void replace(ChangeData cd) throws IOException {
    writer.updateDocument(intTerm(FIELD_CHANGE, cd.getId().get()),
        toDocument(cd));
    commit();
  }

  @Override
  public ChangeDataSource getSource(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    if (p.getType() == FieldType.INTEGER) {
      return intQuery(p);
    } else if (p.getType() == FieldType.EXACT) {
      return exactQuery(p);
    } else {
      throw badFieldType(p.getType());
    }
  }

  public IndexWriter getWriter() {
    return writer;
  }

  private void commit() throws IOException {
    writer.commit();
    searcherManager.maybeRefresh();
  }

  private Term intTerm(String name, int value) {
    BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
    NumericUtils.intToPrefixCodedBytes(value, 0, bytes);
    return new Term(name, bytes);
  }

  private QuerySource intQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    int value;
    try {
      // Can't use IntPredicate because it and IndexPredicate are different
      // subclasses of OperatorPredicate.
      value = Integer.valueOf(p.getValue());
    } catch (IllegalArgumentException e) {
      throw new QueryParseException("not an integer: " + p.getValue());
    }
    return new QuerySource(new TermQuery(intTerm(p.getOperator(), value)));
  }

  private QuerySource exactQuery(IndexPredicate<ChangeData> p) {
    return new QuerySource(new TermQuery(
        new Term(p.getOperator(), p.getValue())));
  }

  private class QuerySource implements ChangeDataSource {
    // TODO(dborowitz): Push limit down from predicate tree.
    private static final int LIMIT = 1000;

    private final Query query;

    public QuerySource(Query query) {
      this.query = query;
    }

    @Override
    public int getCardinality() {
      return 10; // TODO(dborowitz): estimate from Lucene?
    }

    @Override
    public boolean hasChange() {
      return false;
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      try {
        IndexSearcher searcher = searcherManager.acquire();
        try {
          ScoreDoc[] docs = searcher.search(query, LIMIT).scoreDocs;
          List<ChangeData> result = Lists.newArrayListWithCapacity(docs.length);
          for (ScoreDoc sd : docs) {
            Document doc = searcher.doc(sd.doc);
            Number v = doc.getField(FIELD_CHANGE).numericValue();
            result.add(new ChangeData(new Change.Id(v.intValue())));
          }
          final List<ChangeData> r = Collections.unmodifiableList(result);

          return new ResultSet<ChangeData>() {
            @Override
            public Iterator<ChangeData> iterator() {
              return r.iterator();
            }

            @Override
            public List<ChangeData> toList() {
              return r;
            }

            @Override
            public void close() {
              // Do nothing.
            }
          };
        } finally {
          searcherManager.release(searcher);
        }
      } catch (IOException e) {
        throw new OrmException(e);
      }
    }
  }

  private Document toDocument(ChangeData cd) throws IOException {
    try {
      Document result = new Document();
      for (FieldDef<ChangeData, ?> f : ChangeField.ALL.values()) {
        if (f.isRepeatable()) {
          addRepeatableField(result, f, cd);
        } else {
          addSingleField(result, f, cd);
        }
      }
      return result;
    } catch (OrmException e) {
      throw new IOException(e);
    }
  }

  private void addSingleField(Document doc, FieldDef<ChangeData, ?> f,
      ChangeData cd) throws OrmException {
    if (f.getType() == FieldType.INTEGER) {
      doc.add(intField(f, cd, (Integer) f.get(cd, fillArgs)));
    } else if (f.getType() == FieldType.EXACT) {
      doc.add(exactField(f, cd, (String) f.get(cd, fillArgs)));
    } else {
      throw badFieldType(f.getType());
    }
  }

  @SuppressWarnings("unchecked")
  private void addRepeatableField(Document doc, FieldDef<ChangeData, ?> f,
      ChangeData cd) throws OrmException {
    if (f.getType() == FieldType.INTEGER) {
      for (Integer value : (Iterable<Integer>) f.get(cd, fillArgs)) {
        doc.add(intField(f, cd, value));
      }
    } else if (f.getType() == FieldType.EXACT) {
      for (String value : (Iterable<String>) f.get(cd, fillArgs)) {
        doc.add(exactField(f, cd, value));
      }
    } else {
      throw badFieldType(f.getType());
    }
  }

  private static Field intField(FieldDef<ChangeData, ?> f, ChangeData cd,
      Integer value) {
    return new IntField(f.getName(), value, store(f));
  }

  private static Field exactField(FieldDef<ChangeData, ?> f, ChangeData cd,
      String value) {
    return new StringField(f.getName(), value, store(f));
  }

  private static Field.Store store(FieldDef<?, ?> f) {
    return f.isStored() ? Field.Store.YES : Field.Store.NO;
  }

  private static IllegalArgumentException badFieldType(FieldType<?> t) {
    return new IllegalArgumentException("unknown index field type " + t);
  }
}
