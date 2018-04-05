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

package com.google.gerrit.elasticsearch;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.Bulk.Builder;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.core.search.sort.Sort.Sorting;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

public class ElasticProjectIndex extends AbstractElasticIndex<Project.NameKey, ProjectData>
    implements ProjectIndex {
  static class ProjectMapping {
    MappingProperties projects;

    ProjectMapping(Schema<ProjectData> schema) {
      this.projects = ElasticMapping.createMapping(schema);
    }
  }

  static final String PROJECTS = "projects";

  private final ProjectMapping mapping;
  private final Provider<ProjectCache> projectCache;

  @Inject
  ElasticProjectIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<ProjectCache> projectCache,
      JestClientBuilder clientBuilder,
      @Assisted Schema<ProjectData> schema) {
    super(cfg, sitePaths, schema, clientBuilder, PROJECTS);
    this.projectCache = projectCache;
    this.mapping = new ProjectMapping(schema);
  }

  @Override
  public void replace(ProjectData projectState) throws IOException {
    Bulk bulk =
        new Bulk.Builder()
            .defaultIndex(indexName)
            .defaultType(PROJECTS)
            .addAction(insert(PROJECTS, projectState))
            .refresh(true)
            .build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(
          String.format(
              "Failed to replace project %s in index %s: %s",
              projectState.getProject().getName(), indexName, result.getErrorMessage()));
    }
  }

  @Override
  public DataSource<ProjectData> getSource(Predicate<ProjectData> p, QueryOptions opts)
      throws QueryParseException {
    Sort sort = new Sort(ProjectField.NAME.getName(), Sorting.ASC);
    sort.setIgnoreUnmapped();
    return new ElasticQuerySource(p, opts.filterFields(IndexUtils::projectFields), PROJECTS, sort);
  }

  @Override
  protected Builder addActions(Builder builder, Project.NameKey nameKey) {
    return builder.addAction(delete(PROJECTS, nameKey));
  }

  @Override
  protected String getMappings() {
    ImmutableMap<String, ProjectMapping> mappings = ImmutableMap.of("mappings", mapping);
    return gson.toJson(mappings);
  }

  @Override
  protected String getId(ProjectData projectState) {
    return projectState.getProject().getName();
  }

  @Override
  protected ProjectData fromDocument(JsonObject json, Set<String> fields) {
    JsonElement source = json.get("_source");
    if (source == null) {
      source = json.getAsJsonObject().get("fields");
    }

    Project.NameKey nameKey =
        new Project.NameKey(
            source.getAsJsonObject().get(ProjectField.NAME.getName()).getAsString());
    return projectCache.get().get(nameKey).toProjectData();
  }
}
