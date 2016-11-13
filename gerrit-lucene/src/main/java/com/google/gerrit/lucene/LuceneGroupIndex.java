// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.lucene;

import static com.google.gerrit.server.index.group.GroupField.UUID;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuceneGroupIndex extends AbstractLuceneIndex<AccountGroup.UUID, AccountGroup>
    implements GroupIndex {
  private static final Logger log = LoggerFactory.getLogger(LuceneGroupIndex.class);

  private static final String GROUPS = "groups";

  private static final String UUID_SORT_FIELD = sortFieldName(UUID);

  private static Term idTerm(AccountGroup group) {
    return idTerm(group.getGroupUUID());
  }

  private static Term idTerm(AccountGroup.UUID uuid) {
    return QueryBuilder.stringTerm(UUID.getName(), uuid.get());
  }

  private final GerritIndexWriterConfig indexWriterConfig;
  private final QueryBuilder<AccountGroup> queryBuilder;
  private final Provider<GroupCache> groupCache;

  private static Directory dir(Schema<AccountGroup> schema, Config cfg, SitePaths sitePaths)
      throws IOException {
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      return new RAMDirectory();
    }
    Path indexDir = LuceneVersionManager.getDir(sitePaths, GROUPS + "_", schema);
    return FSDirectory.open(indexDir);
  }

  @Inject
  LuceneGroupIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<GroupCache> groupCache,
      @Assisted Schema<AccountGroup> schema)
      throws IOException {
    super(
        schema,
        sitePaths,
        dir(schema, cfg, sitePaths),
        GROUPS,
        null,
        new GerritIndexWriterConfig(cfg, GROUPS),
        new SearcherFactory());
    this.groupCache = groupCache;

    indexWriterConfig = new GerritIndexWriterConfig(cfg, GROUPS);
    queryBuilder = new QueryBuilder<>(schema, indexWriterConfig.getAnalyzer());
  }

  @Override
  public void replace(AccountGroup group) throws IOException {
    try {
      // No parts of FillArgs are currently required, just use null.
      replace(idTerm(group), toDocument(group, null)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void delete(AccountGroup.UUID key) throws IOException {
    try {
      delete(idTerm(key)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public DataSource<AccountGroup> getSource(Predicate<AccountGroup> p, QueryOptions opts)
      throws QueryParseException {
    return new QuerySource(
        opts,
        queryBuilder.toQuery(p),
        new Sort(new SortField(UUID_SORT_FIELD, SortField.Type.STRING, false)));
  }

  private class QuerySource implements DataSource<AccountGroup> {
    private final QueryOptions opts;
    private final Query query;
    private final Sort sort;

    private QuerySource(QueryOptions opts, Query query, Sort sort) {
      this.opts = opts;
      this.query = query;
      this.sort = sort;
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<AccountGroup> read() throws OrmException {
      IndexSearcher searcher = null;
      try {
        searcher = acquire();
        int realLimit = opts.start() + opts.limit();
        TopFieldDocs docs = searcher.search(query, realLimit, sort);
        List<AccountGroup> result = new ArrayList<>(docs.scoreDocs.length);
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
          Document doc = searcher.doc(sd.doc, IndexUtils.groupFields(opts));
          result.add(toAccountGroup(doc));
        }
        final List<AccountGroup> r = Collections.unmodifiableList(result);
        return new ResultSet<AccountGroup>() {
          @Override
          public Iterator<AccountGroup> iterator() {
            return r.iterator();
          }

          @Override
          public List<AccountGroup> toList() {
            return r;
          }

          @Override
          public void close() {
            // Do nothing.
          }
        };
      } catch (IOException e) {
        throw new OrmException(e);
      } finally {
        if (searcher != null) {
          try {
            release(searcher);
          } catch (IOException e) {
            log.warn("cannot release Lucene searcher", e);
          }
        }
      }
    }
  }

  private AccountGroup toAccountGroup(Document doc) {
    AccountGroup.UUID uuid = new AccountGroup.UUID(doc.getField(UUID.getName()).stringValue());
    // Use the GroupCache rather than depending on any stored fields in the
    // document (of which there shouldn't be any).
    return groupCache.get().get(uuid);
  }
}
