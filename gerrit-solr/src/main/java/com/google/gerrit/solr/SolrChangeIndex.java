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
import static com.google.gerrit.solr.IndexVersionCheck.SCHEMA_VERSIONS;
import static com.google.gerrit.solr.IndexVersionCheck.solrIndexConfig;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lucene.QueryBuilder;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.IndexRewriteImpl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherManager;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.SolrCore;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
  private final SitePaths sitePaths;
  private final CloudSolrServer openIndex;
  private final CloudSolrServer closedIndex;

  @Inject
  SolrChangeIndex(@SolrIndexUrl String url, FillArgs fillArgs,
      SitePaths sitePaths) throws IOException {
    this.fillArgs = fillArgs;
    this.sitePaths = sitePaths;
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
    return new QuerySource(indexes, QueryBuilder.toQuery(p));
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
        doc.addField(f.getName(), ((Integer) value).intValue());
      }
    } else if (f.getType() == FieldType.EXACT) {
      for (Object value : values) {
        doc.addField(f.getName(), (String) value);
      }
    } else {
      throw QueryBuilder.badFieldType(f.getType());
    }
  }

  @Override
  public void finishIndex() throws IOException,
      ConfigInvalidException {
    FileBasedConfig cfg =
        new FileBasedConfig(solrIndexConfig(sitePaths), FS.detect());

    for (Map.Entry<String, Integer> e : SCHEMA_VERSIONS.entrySet()) {
      cfg.setInt("index", e.getKey(), "schemaVersion", e.getValue());
    }
    cfg.setString("solr", null, "version", SOLR_VERSION);
    cfg.save();
  }
}
