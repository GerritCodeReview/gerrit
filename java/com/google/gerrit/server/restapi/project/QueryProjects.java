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

import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
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
import com.google.inject.Provider;
import java.util.List;
import org.kohsuke.args4j.Option;

public class QueryProjects implements RestReadView<TopLevelResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProjectIndexCollection indexes;
  private final ProjectQueryBuilder queryBuilder;
  private final Provider<ProjectQueryProcessor> queryProcessorProvider;
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
      Provider<ProjectQueryProcessor> queryProcessorProvider,
      ProjectJson json) {
    this.indexes = indexes;
    this.queryBuilder = queryBuilder;
    this.queryProcessorProvider = queryProcessorProvider;
    this.json = json;
  }

  @Override
  public Response<List<ProjectInfo>> apply(TopLevelResource resource)
      throws BadRequestException, MethodNotAllowedException {
    return Response.ok(apply());
  }

  public List<ProjectInfo> apply() throws BadRequestException, MethodNotAllowedException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    ProjectIndex searchIndex = indexes.getSearchIndex();
    if (searchIndex == null) {
      throw new MethodNotAllowedException("no project index");
    }

    ProjectQueryProcessor queryProcessor = queryProcessorProvider.get();

    if (start != 0) {
      queryProcessor.setStart(start);
    }

    if (limit != 0) {
      queryProcessor.setUserProvidedLimit(limit);
    }

    try {
      logger.atFine().log("query: %s", query);
      QueryResult<ProjectData> result = queryProcessor.query(queryBuilder.parse(query));
      List<ProjectData> pds = result.entities();
      logger.atFine().log("query returned %d results", pds.size());
      List<ProjectInfo> projectInfos =
          pds.stream().map(pd -> json.format(pd.getProject())).distinct().collect(toList());
      logger.atFine().log("returning %d distinct results", projectInfos.size());
      return projectInfos;
    } catch (QueryParseException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
