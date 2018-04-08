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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.eclipse.jgit.lib.Config;

public class LuceneGroupIndex extends AbstractLuceneIndex<AccountGroup.UUID, InternalGroup>
    implements GroupIndex {

  private static final String GROUPS = "groups";

  private static final String UUID_SORT_FIELD = sortFieldName(UUID);

  private static Term idTerm(InternalGroup group) {
    return idTerm(group.getGroupUUID());
  }

  private static Term idTerm(AccountGroup.UUID uuid) {
    return QueryBuilder.stringTerm(UUID.getName(), uuid.get());
  }

  private final GerritIndexWriterConfig indexWriterConfig;
  private final QueryBuilder<InternalGroup> queryBuilder;
  private final Provider<GroupCache> groupCache;

  private static Directory dir(Schema<?> schema, Config cfg, SitePaths sitePaths)
      throws IOException {
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      return new RAMDirectory();
    }
    Path indexDir = LuceneVersionManager.getDir(sitePaths, GROUPS, schema);
    return FSDirectory.open(indexDir);
  }

  @Inject
  LuceneGroupIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<GroupCache> groupCache,
      @Assisted Schema<InternalGroup> schema)
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
  public void replace(InternalGroup group) throws IOException {
    try {
      replace(idTerm(group), toDocument(group)).get();
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
  public DataSource<InternalGroup> getSource(Predicate<InternalGroup> p, QueryOptions opts)
      throws QueryParseException {
    return new LuceneQuerySource(
        opts.filterFields(IndexUtils::groupFields),
        queryBuilder.toQuery(p),
        new Sort(new SortField(UUID_SORT_FIELD, SortField.Type.STRING, false)));
  }

  @Override
  protected InternalGroup fromDocument(Document doc) {
    AccountGroup.UUID uuid = new AccountGroup.UUID(doc.getField(UUID.getName()).stringValue());
    // Use the GroupCache rather than depending on any stored fields in the
    // document (of which there shouldn't be any).
    return groupCache.get().get(uuid).orElse(null);
  }
}
