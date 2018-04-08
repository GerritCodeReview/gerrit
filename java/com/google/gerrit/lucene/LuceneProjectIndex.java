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

import static com.google.gerrit.index.project.ProjectField.NAME;

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.project.ProjectCache;
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

public class LuceneProjectIndex extends AbstractLuceneIndex<Project.NameKey, ProjectData>
    implements ProjectIndex {
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
      replace(idTerm(projectState), toDocument(projectState)).get();
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
    return new LuceneQuerySource(
        opts.filterFields(IndexUtils::projectFields),
        queryBuilder.toQuery(p),
        new Sort(new SortField(NAME_SORT_FIELD, SortField.Type.STRING, false)));
  }

  @Override
  protected ProjectData fromDocument(Document doc) {
    Project.NameKey nameKey = new Project.NameKey(doc.getField(NAME.getName()).stringValue());
    return projectCache.get().get(nameKey).toProjectData();
  }
}
