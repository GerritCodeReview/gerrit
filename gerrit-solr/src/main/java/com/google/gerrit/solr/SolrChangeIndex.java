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
// limitations under the License.

package com.google.gerrit.solr;

import static com.google.gerrit.server.index.IndexRewriteImpl.CLOSED_STATUSES;
import static com.google.gerrit.server.index.IndexRewriteImpl.OPEN_STATUSES;
import static com.google.gerrit.solr.IndexVersionCheck.SCHEMA_VERSIONS;
import static com.google.gerrit.solr.IndexVersionCheck.solrIndexConfig;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lucene.QueryBuilder;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexRewriteImpl;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import org.apache.lucene.search.Query;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Secondary index implementation using a remote Solr instance. */
class SolrChangeIndex implements ChangeIndex, LifecycleListener {
  public static final String CHANGES_OPEN = "changes_open";
  public static final String CHANGES_CLOSED = "changes_closed";
  private static final String ID_FIELD = ChangeField.LEGACY_ID.getName();

  private final FillArgs fillArgs;
  private final SitePaths sitePaths;
  private final IndexCollection indexes;
  private final CloudSolrServer openIndex;
  private final CloudSolrServer closedIndex;
  private final Schema<ChangeData> schema;

  SolrChangeIndex(
      @GerritServerConfig Config cfg,
      FillArgs fillArgs,
      SitePaths sitePaths,
      IndexCollection indexes,
      Schema<ChangeData> schema,
      String base) throws IOException {
    this.fillArgs = fillArgs;
    this.sitePaths = sitePaths;
    this.indexes = indexes;
    this.schema = schema;

    String url = cfg.getString("index", "solr", "url");
    if (Strings.isNullOrEmpty(url)) {
      throw new IllegalStateException("index.solr.url must be supplied");
    }

    base = Strings.nullToEmpty(base);
    openIndex = new CloudSolrServer(url);
    openIndex.setDefaultCollection(base + CHANGES_OPEN);

    closedIndex = new CloudSolrServer(url);
    closedIndex.setDefaultCollection(base + CHANGES_CLOSED);
  }

  @Override
  public void start() {
    indexes.setSearchIndex(this);
    indexes.addWriteIndex(this);
  }

  @Override
  public void stop() {
    openIndex.shutdown();
    closedIndex.shutdown();
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @Override
  public void close() {
    stop();
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
      throw new IOException(e);
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
      throw new IOException(e);
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
      throw new IOException(e);
    }
  }

  @Override
  public void deleteAll() throws IOException {
    try {
      openIndex.deleteByQuery("*:*");
      closedIndex.deleteByQuery("*:*");
    } catch (SolrServerException e) {
      throw new IOException(e);
    }
    commit(openIndex);
    commit(closedIndex);
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, int limit)
      throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriteImpl.getPossibleStatus(p);
    List<SolrServer> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(openIndex);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(closedIndex);
    }
    return new QuerySource(indexes, QueryBuilder.toQuery(schema, p), limit,
        ChangeQueryBuilder.hasNonTrivialSortKeyAfter(schema, p));
  }

  private void commit(SolrServer server) throws IOException {
    try {
      server.commit();
    } catch (SolrServerException e) {
      throw new IOException(e);
    }
  }

  private class QuerySource implements ChangeDataSource {
    private final List<SolrServer> indexes;
    private final SolrQuery query;

    public QuerySource(List<SolrServer> indexes, Query q, int limit,
        boolean reverse) {
      this.indexes = indexes;

      query = new SolrQuery(q.toString());
      query.setParam("shards.tolerant", true);
      query.setParam("rows", Integer.toString(limit));
      query.setFields(ID_FIELD);
      query.setSort(
          ChangeField.SORTKEY.getName(),
          !reverse ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc);
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
    public String toString() {
      return query.getQuery();
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      try {
        // TODO Sort documents during merge to select only top N.
        SolrDocumentList docs = new SolrDocumentList();
        for (SolrServer index : indexes) {
          docs.addAll(index.query(query).getResults());
        }

        List<ChangeData> result = Lists.newArrayListWithCapacity(docs.size());
        for (SolrDocument doc : docs) {
          Integer v = (Integer) doc.getFieldValue(ID_FIELD);
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
        throw new OrmException(e);
      }
    }
  }

  private SolrInputDocument toDocument(ChangeData cd) throws IOException {
    try {
      SolrInputDocument result = new SolrInputDocument();
      for (Values<ChangeData> values : schema.buildFields(cd, fillArgs)) {
        add(result, values);
      }
      return result;
    } catch (OrmException e) {
      throw new IOException(e);
    }
  }

  private void add(SolrInputDocument doc, Values<ChangeData> values)
      throws OrmException {
    String name = values.getField().getName();
    FieldType<?> type = values.getField().getType();

    if (type == FieldType.INTEGER) {
      for (Object value : values.getValues()) {
        doc.addField(name, (Integer) value);
      }
    } else if (type == FieldType.LONG) {
      for (Object value : values.getValues()) {
        doc.addField(name, (Long) value);
      }
    } else if (type == FieldType.TIMESTAMP) {
      for (Object v : values.getValues()) {
        doc.addField(name, QueryBuilder.toIndexTime((Timestamp) v));
      }
    } else if (type == FieldType.EXACT
        || type == FieldType.PREFIX
        || type == FieldType.FULL_TEXT) {
      for (Object value : values.getValues()) {
        doc.addField(name, (String) value);
      }
    } else {
      throw QueryBuilder.badFieldType(type);
    }
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    // TODO Move the schema version information to a special meta-document
    FileBasedConfig cfg = new FileBasedConfig(
        solrIndexConfig(sitePaths),
        FS.detect());
    for (Map.Entry<String, Integer> e : SCHEMA_VERSIONS.entrySet()) {
      cfg.setInt("index", e.getKey(), "schemaVersion",
          ready ? e.getValue() : -1);
    }
    cfg.save();
  }
}
