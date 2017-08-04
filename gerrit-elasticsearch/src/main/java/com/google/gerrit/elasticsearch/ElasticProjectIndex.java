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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.project.ProjectField;
import com.google.gerrit.server.index.project.ProjectIndex;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectData;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.Bulk.Builder;
import io.searchbox.core.Search;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.core.search.sort.Sort.Sorting;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticProjectIndex extends AbstractElasticIndex<Project.NameKey, ProjectData>
    implements ProjectIndex {
  static class ProjectMapping {
    MappingProperties projects;

    ProjectMapping(Schema<ProjectData> schema) {
      this.projects = ElasticMapping.createMapping(schema);
    }
  }

  static final String PROJECTS = "projects";
  static final String PROJECTS_PREFIX = PROJECTS + "_";

  private static final Logger log = LoggerFactory.getLogger(ElasticProjectIndex.class);

  private final ProjectMapping mapping;
  private final Provider<ProjectCache> projectCache;

  @Inject
  ElasticProjectIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<ProjectCache> projectCache,
      JestClientBuilder clientBuilder,
      @Assisted Schema<ProjectData> schema) {
    // No parts of FillArgs are currently required, just use null.
    super(cfg, null, sitePaths, schema, clientBuilder, PROJECTS_PREFIX);
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
    return new QuerySource(p, opts);
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

  private class QuerySource implements DataSource<ProjectData> {
    private final Search search;
    private final Set<String> fields;

    QuerySource(Predicate<ProjectData> p, QueryOptions opts) throws QueryParseException {
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      fields = IndexUtils.projectFields(opts);
      SearchSourceBuilder searchSource =
          new SearchSourceBuilder()
              .query(qb)
              .from(opts.start())
              .size(opts.limit())
              .fields(Lists.newArrayList(fields));

      Sort sort = new Sort(ProjectField.NAME.getName(), Sorting.ASC);
      sort.setIgnoreUnmapped();

      search =
          new Search.Builder(searchSource.toString())
              .addType(PROJECTS)
              .addIndex(indexName)
              .addSort(ImmutableList.of(sort))
              .build();
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<ProjectData> read() throws OrmException {
      try {
        List<ProjectData> results = Collections.emptyList();
        JestResult result = client.execute(search);
        if (result.isSucceeded()) {
          JsonObject obj = result.getJsonObject().getAsJsonObject("hits");
          if (obj.get("hits") != null) {
            JsonArray json = obj.getAsJsonArray("hits");
            results = Lists.newArrayListWithCapacity(json.size());
            for (int i = 0; i < json.size(); i++) {
              results.add(toProjectData(json.get(i)));
            }
          }
        } else {
          log.error(result.getErrorMessage());
        }
        final List<ProjectData> r = Collections.unmodifiableList(results);
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
      }
    }

    @Override
    public String toString() {
      return search.toString();
    }

    private ProjectData toProjectData(JsonElement json) {
      JsonElement source = json.getAsJsonObject().get("_source");
      if (source == null) {
        source = json.getAsJsonObject().get("fields");
      }

      Project.NameKey nameKey =
          new Project.NameKey(
              source.getAsJsonObject().get(ProjectField.NAME.getName()).getAsString());
      return projectCache.get().get(nameKey).toProjectData();
    }
  }
}
