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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.query.project.ProjectQueryBuilder;
import com.google.gerrit.server.query.project.ProjectQueryProcessor;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

public class QueryProjects implements RestReadView<TopLevelResource> {
  private final ProjectIndexCollection indexes;
  private final ProjectQueryBuilder queryBuilder;
  private final ProjectQueryProcessor queryProcessor;
  private final ProjectJson json;

  private String query;
  private int limit;
  private int start;

  @Option(
      name = "--query",
      aliases = {"-q"},
      usage = "project query")
  public QueryProjects withQuery(String query) {
    this.query = query;
    return this;
  }

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of projects to list")
  public QueryProjects withLimit(int limit) {
    this.limit = limit;
    return this;
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "number of projects to skip")
  public QueryProjects withStart(int start) {
    this.start = start;
    return this;
  }

  @Inject
  protected QueryProjects(
      ProjectIndexCollection indexes,
      ProjectQueryBuilder queryBuilder,
      ProjectQueryProcessor queryProcessor,
      ProjectJson json) {
    this.indexes = indexes;
    this.queryBuilder = queryBuilder;
    this.queryProcessor = queryProcessor;
    this.json = json;
  }

  @Override
  public List<ProjectInfo> apply(TopLevelResource resource)
      throws BadRequestException, MethodNotAllowedException, StorageException {
    return apply();
  }

  public List<ProjectInfo> apply()
      throws BadRequestException, MethodNotAllowedException, StorageException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    ProjectIndex searchIndex = indexes.getSearchIndex();
    if (searchIndex == null) {
      throw new MethodNotAllowedException("no project index");
    }

    if (start != 0) {
      queryProcessor.setStart(start);
    }

    if (limit != 0) {
      queryProcessor.setUserProvidedLimit(limit);
    }

    try {
      QueryResult<ProjectData> result = queryProcessor.query(queryBuilder.parse(query));
      List<ProjectData> pds = result.entities();

      ArrayList<ProjectInfo> projectInfos = Lists.newArrayListWithCapacity(pds.size());
      for (ProjectData pd : pds) {
        projectInfos.add(json.format(pd.getProject()));
      }
      return projectInfos;
    } catch (QueryParseException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
