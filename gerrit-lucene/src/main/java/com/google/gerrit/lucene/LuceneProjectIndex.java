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

import static com.google.gerrit.server.index.project.ProjectField.NAME;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.project.ProjectIndex;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectData;
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

public class LuceneProjectIndex extends AbstractLuceneIndex<Project.NameKey, ProjectData>
    implements ProjectIndex {
  private static final Logger log = LoggerFactory.getLogger(LuceneProjectIndex.class);

  private static final String PROJECTS = "projects";

  private static final String NAME_SORT_FIELD = sortFieldName(NAME);

  private static Term idTerm(ProjectData projectState) {
    return idTerm(projectState.getProject().getNameKey());
  }

  private static Term idTerm(Project.NameKey nameKey) {
    return QueryBuilder.stringTerm(NAME.getName(), nameKey.get());
  }

  private final GerritIndexWriterConfig indexWriterConfig;
  private final QueryBuilder<ProjectData> queryBuilder;
  private final Provider<ProjectCache> projectCache;

  private static Directory dir(Schema<ProjectData> schema, Config cfg, SitePaths sitePaths)
      throws IOException {
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      return new RAMDirectory();
    }
    Path indexDir = LuceneVersionManager.getDir(sitePaths, PROJECTS, schema);
    return FSDirectory.open(indexDir);
  }

  @Inject
  LuceneProjectIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<ProjectCache> projectCache,
      @Assisted Schema<ProjectData> schema)
      throws IOException {
    super(
        schema,
        sitePaths,
        dir(schema, cfg, sitePaths),
        PROJECTS,
        null,
        new GerritIndexWriterConfig(cfg, PROJECTS),
        new SearcherFactory());
    this.projectCache = projectCache;

    indexWriterConfig = new GerritIndexWriterConfig(cfg, PROJECTS);
    queryBuilder = new QueryBuilder<>(schema, indexWriterConfig.getAnalyzer());
  }

  @Override
  public void replace(ProjectData projectState) throws IOException {
    try {
      // No parts of FillArgs are currently required, just use null.
      replace(idTerm(projectState), toDocument(projectState, null)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void delete(Project.NameKey nameKey) throws IOException {
    try {
      delete(idTerm(nameKey)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public DataSource<ProjectData> getSource(Predicate<ProjectData> p, QueryOptions opts)
      throws QueryParseException {
    return new QuerySource(
        opts,
        queryBuilder.toQuery(p),
        new Sort(new SortField(NAME_SORT_FIELD, SortField.Type.STRING, false)));
  }

  private class QuerySource implements DataSource<ProjectData> {
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
    public ResultSet<ProjectData> read() throws OrmException {
      IndexSearcher searcher = null;
      try {
        searcher = acquire();
        int realLimit = opts.start() + opts.limit();
        TopFieldDocs docs = searcher.search(query, realLimit, sort);
        List<ProjectData> result = new ArrayList<>(docs.scoreDocs.length);
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
          Document doc = searcher.doc(sd.doc, IndexUtils.projectFields(opts));
          result.add(toProjectData(doc));
        }
        final List<ProjectData> r = Collections.unmodifiableList(result);
        return new ResultSet<ProjectData>() {
          @Override
          public Iterator<ProjectData> iterator() {
            return r.iterator();
          }

          @Override
          public List<ProjectData> toList() {
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

  private ProjectData toProjectData(Document doc) {
    Project.NameKey nameKey = new Project.NameKey(doc.getField(NAME.getName()).stringValue());
    return projectCache.get().get(nameKey).toProjectData();
  }
}
