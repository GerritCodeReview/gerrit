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

package com.google.gerrit.solr;

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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.SolrCore;

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
@Singleton
public class SolrChangeIndex implements ChangeIndex, LifecycleListener {
  public static final String SOLR_VERSION = SolrCore.version;
  public static final String CHANGES_OPEN = "changes_open";
  public static final String CHANGES_CLOSED = "changes_closed";

  private final FillArgs fillArgs;
  private final CloudSolrServer openIndex;
  private final CloudSolrServer closedIndex;

  @Inject
  SolrChangeIndex(@Named("url") String url, FillArgs fillArgs) throws IOException {
    this.fillArgs = fillArgs;
    openIndex = new CloudSolrServer(url);
    openIndex.setDefaultCollection(CHANGES_OPEN);
    closedIndex = new CloudSolrServer(url);
    closedIndex.setDefaultCollection(CHANGES_CLOSED);
  }

  @Override
  public void start() {
    // Do nothing.
  }

  @Override
  public void stop() {
    openIndex.shutdown();
    closedIndex.shutdown();
  }

  @Override
  public void insert(ChangeData cd) throws IOException {
    String id = cd.getId().toString();
    SolrInputDocument doc = toDocument(cd);
    try {
      if (cd.getChange().getStatus().isOpen()) {
        closedIndex.deleteById(id);
        openIndex.add(doc);
      } else {
        openIndex.deleteById(id);
        closedIndex.add(doc);
      }
    } catch (SolrServerException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    commit(openIndex);
    commit(closedIndex);
  }

  @Override
  public void replace(ChangeData cd) throws IOException {
    String id = cd.getId().toString();
    SolrInputDocument doc = toDocument(cd);
    try {
      if (cd.getChange().getStatus().isOpen()) {
        closedIndex.deleteById(id);
        openIndex.add(doc);
      } else {
        openIndex.deleteById(id);
        closedIndex.add(doc);
      }
    } catch (SolrServerException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    commit(openIndex);
    commit(closedIndex);
  }

  @Override
  public void delete(ChangeData cd) throws IOException {
    String id = cd.getId().toString();
    try {
      if (cd.getChange().getStatus().isOpen()) {
        openIndex.deleteById(id);
        commit(openIndex);
      } else {
        closedIndex.deleteById(id);
        commit(closedIndex);
      }
    } catch (SolrServerException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public void deleteAll() throws IOException {
    try {
      openIndex.deleteByQuery("*:*");
      closedIndex.deleteByQuery("*:*");
    } catch (SolrServerException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    commit(openIndex);
    commit(closedIndex);
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p)
      throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriteImpl.getPossibleStatus(p);
    List<SolrServer> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(openIndex);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(closedIndex);
    }
    return new QuerySource(indexes, toQuery(p));
  }

  private void commit(SolrServer server) throws IOException {
    try {
      server.commit();
    } catch (SolrServerException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
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
    private final List<SolrServer> indexes;
    private final SolrQuery query;

    public QuerySource(List<SolrServer> indexes, Query query) {
      this.indexes = indexes;
      this.query = new SolrQuery();
      this.query.setQuery(query.toString());
    }

    @Override
    public int getCardinality() {
      return 10; // TODO: estimate from solr?
    }

    @Override
    public boolean hasChange() {
      return false;
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      try {
        List<ChangeData> result = null;
        SolrDocumentList docs = new SolrDocumentList();
        query.setParam("shards.tolerant", true);
        for (SolrServer index : indexes) {
          QueryResponse rsp = index.query(query);
          docs.addAll(rsp.getResults());
        }
        result = Lists.newArrayListWithCapacity(docs.size());
        for (SolrDocument doc : docs) {
          Integer v = (Integer) doc.getFieldValue(FIELD_CHANGE);
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
      } catch (SolrServerException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        throw new OrmException(e);
      }
    }
  }

  private SolrInputDocument toDocument(ChangeData cd) throws IOException {
    try {
      SolrInputDocument result = new SolrInputDocument();
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

  private void add(SolrInputDocument doc, FieldDef<ChangeData, ?> f,
      Iterable<?> values) throws OrmException {
    if (f.getType() == FieldType.INTEGER) {
      for (Object value : values) {
        doc.addField(f.getName(), ((Integer)value).intValue());
      }
    } else if (f.getType() == FieldType.EXACT) {
      for (Object value : values) {
        doc.addField(f.getName(), (String) value);
      }
    } else {
      throw badFieldType(f.getType());
    }
  }

  private static IllegalArgumentException badFieldType(FieldType<?> t) {
    return new IllegalArgumentException("unknown index field type " + t);
  }
}
