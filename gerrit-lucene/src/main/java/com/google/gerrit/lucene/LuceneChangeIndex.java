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
import static com.google.gerrit.server.query.change.IndexRewriteImpl.CLOSED_STATUSES;
import static com.google.gerrit.server.query.change.IndexRewriteImpl.OPEN_STATUSES;

import static org.apache.lucene.search.BooleanClause.Occur.MUST;
import static org.apache.lucene.search.BooleanClause.Occur.MUST_NOT;
import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.IndexRewriteImpl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Secondary index implementation using Apache Lucene.
 * <p>
 * Writes are managed using a single {@link IndexWriter} per process, committed
 * aggressively. Reads use {@link SearcherManager} and periodically refresh,
 * though there may be some lag between a committed write and it showing up to
 * other threads' searchers.
 */
public class LuceneChangeIndex implements ChangeIndex, LifecycleListener {
  public static final Version LUCENE_VERSION = Version.LUCENE_43;
  public static final String CHANGES_OPEN = "changes_open";
  public static final String CHANGES_CLOSED = "changes_closed";

  private final FillArgs fillArgs;
  private final boolean readOnly;
  private final SubIndex openIndex;
  private final SubIndex closedIndex;

  LuceneChangeIndex(SitePaths sitePaths, FillArgs fillArgs, boolean readOnly)
      throws IOException {
    this.fillArgs = fillArgs;
    this.readOnly = readOnly;
    openIndex = new SubIndex(new File(sitePaths.index_dir, CHANGES_OPEN));
    closedIndex = new SubIndex(new File(sitePaths.index_dir, CHANGES_CLOSED));
  }

  @Override
  public void start() {
    // Do nothing.
  }

  @Override
  public void stop() {
    openIndex.close();
    closedIndex.close();
  }

  @Override
  public void insert(ChangeData cd) throws IOException {
    Term id = idTerm(cd);
    Document doc = toDocument(cd);
    if (readOnly) {
      return;
    }
    if (cd.getChange().getStatus().isOpen()) {
      closedIndex.delete(id);
      openIndex.insert(doc);
    } else {
      openIndex.delete(id);
      closedIndex.insert(doc);
    }
  }

  @Override
  public void replace(ChangeData cd) throws IOException {
    Term id = idTerm(cd);
    Document doc = toDocument(cd);
    if (readOnly) {
      return;
    }
    if (cd.getChange().getStatus().isOpen()) {
      closedIndex.delete(id);
      openIndex.replace(id, doc);
    } else {
      openIndex.delete(id);
      closedIndex.replace(id, doc);
    }
  }

  @Override
  public void delete(ChangeData cd) throws IOException {
    Term id = idTerm(cd);
    if (readOnly) {
      return;
    }
    if (cd.getChange().getStatus().isOpen()) {
      openIndex.delete(id);
    } else {
      closedIndex.delete(id);
    }
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p)
      throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriteImpl.getPossibleStatus(p);
    List<SubIndex> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(openIndex);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(closedIndex);
    }
    return new QuerySource(indexes, toQuery(p));
  }

  private Term idTerm(ChangeData cd) {
    return intTerm(FIELD_CHANGE, cd.getId().get());
  }

  private Query toQuery(Predicate<ChangeData> p) throws QueryParseException {
    if (p.getClass() == AndPredicate.class) {
      return booleanQuery(p, MUST);
    } else if (p.getClass() == OrPredicate.class) {
      return booleanQuery(p, SHOULD);
    } else if (p.getClass() == NotPredicate.class) {
      return booleanQuery(p, MUST_NOT);
    } else if (p instanceof IndexPredicate) {
      return fieldQuery((IndexPredicate<ChangeData>) p);
    } else {
      throw new QueryParseException("Cannot convert to index predicate: " + p);
    }
  }

  private Query booleanQuery(Predicate<ChangeData> p, BooleanClause.Occur o)
      throws QueryParseException {
    BooleanQuery q = new BooleanQuery();
    for (int i = 0; i < p.getChildCount(); i++) {
      q.add(toQuery(p.getChild(i)), o);
    }
    return q;
  }

  private Query fieldQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    if (p.getType() == FieldType.INTEGER) {
      return intQuery(p);
    } else if (p.getType() == FieldType.EXACT) {
      return exactQuery(p);
    } else {
      throw badFieldType(p.getType());
    }
  }

  private Term intTerm(String name, int value) {
    BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
    NumericUtils.intToPrefixCodedBytes(value, 0, bytes);
    return new Term(name, bytes);
  }

  private Query intQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    int value;
    try {
      // Can't use IntPredicate because it and IndexPredicate are different
      // subclasses of OperatorPredicate.
      value = Integer.valueOf(p.getValue());
    } catch (IllegalArgumentException e) {
      throw new QueryParseException("not an integer: " + p.getValue());
    }
    return new TermQuery(intTerm(p.getOperator(), value));
  }

  private Query exactQuery(IndexPredicate<ChangeData> p) {
    return new TermQuery(new Term(p.getOperator(), p.getValue()));
  }

  private class QuerySource implements ChangeDataSource {
    // TODO(dborowitz): Push limit down from predicate tree.
    private static final int LIMIT = 1000;

    private final List<SubIndex> indexes;
    private final Query query;

    public QuerySource(List<SubIndex> indexes, Query query) {
      this.indexes = indexes;
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
        List<ChangeData> result =
            Lists.newArrayListWithExpectedSize(2 * getCardinality());
        for (SubIndex index : indexes) {
          result.addAll(index.search(query, LIMIT));
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
          add(result, f, (Iterable<?>) f.get(cd, fillArgs));
        } else {
          add(result, f, Collections.singleton(f.get(cd, fillArgs)));
        }
      }
      return result;
    } catch (OrmException e) {
      throw new IOException(e);
    }
  }

  private void add(Document doc, FieldDef<ChangeData, ?> f,
      Iterable<?> values) throws OrmException {
    if (f.getType() == FieldType.INTEGER) {
      for (Object value : values) {
        doc.add(new IntField(f.getName(), (Integer) value, store(f)));
      }
    } else if (f.getType() == FieldType.EXACT) {
      for (Object value : values) {
        doc.add(new StringField(f.getName(), (String) value, store(f)));
      }
    } else {
      throw badFieldType(f.getType());
    }
  }

  private static Field.Store store(FieldDef<?, ?> f) {
    return f.isStored() ? Field.Store.YES : Field.Store.NO;
  }

  private static IllegalArgumentException badFieldType(FieldType<?> t) {
    return new IllegalArgumentException("unknown index field type " + t);
  }
}
