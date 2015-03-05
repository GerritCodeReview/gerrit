// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ProjectListIndex;

import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.core.Delete;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.IndicesExists;

import org.eclipse.jgit.lib.Config;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Secondary index implementation using Elasticsearch. */
public class ElasticsearchProjectListIndex implements ProjectListIndex,
    LifecycleListener {
  private final Logger log = LoggerFactory
      .getLogger(ElasticsearchProjectListIndex.class);

  private final String DEFAULT_INDEX_NAME = "gerrit_project_list";
  private final String DEFAULT_TYPE_NAME = "project_list";
  private final String DEFAULT_PROP_NAME = "name";

  private JestHttpClient client;

  public ElasticsearchProjectListIndex(@GerritServerConfig Config cfg) {
    String url = cfg.getString("index", null, "url");
    if (Strings.isNullOrEmpty(url)) {
      throw new IllegalStateException("index.url must be supplied");
    }
    boolean testMode = cfg.getBoolean("index", "elasticsearch", "test", false);

    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(new HttpClientConfig.Builder(url)
        .multiThreaded(true).discoveryEnabled(!testMode)
        .discoveryFrequency(1l, TimeUnit.MINUTES).build());
    client = (JestHttpClient) factory.getObject();
  }

  @Override
  public void start() {
  }

  @Override
  public void reCreateIndex() {
    JestResult result = null;
    try {
      result =
          client.execute(new IndicesExists.Builder(DEFAULT_INDEX_NAME).build());
    } catch (IOException e) {
      String error =
          String.format("Failed to check if index %s already exists",
              DEFAULT_INDEX_NAME, e.getMessage());
      log.error(error);
    }

    if (result != null && !result.isSucceeded()) {
      createIndex();
    } else if (result != null && result.isSucceeded()) {
      deleteIndex();
      createIndex();
    }
  }

  private void deleteIndex() {
    try {
      client.execute(new Delete.Builder(DEFAULT_INDEX_NAME).build());
    } catch (IOException e) {
      String error =
          String.format("Failed to delete index %s", DEFAULT_INDEX_NAME,
              e.getMessage());
      log.error(error);
    }
  }

  private void createIndex() {
    try {
      client.execute(new CreateIndex.Builder(DEFAULT_INDEX_NAME).build());
    } catch (IOException e) {
      String error =
          String.format("Failed to create index %s", DEFAULT_INDEX_NAME,
              e.getMessage());
      log.error(error);
    }
  }

  @Override
  public void stop() {
    client.shutdownClient();
  }

  @Override
  public void deleteAll() {
    // TODO Auto-generated method stub

  }

  @Override
  public void delete(String name) {
    // TODO Auto-generated method stub

  }

  @Override
  public void insert(ProjectInfo info) {
    try {
      client.execute(new Index.Builder(info).index(DEFAULT_INDEX_NAME)
          .type(DEFAULT_TYPE_NAME).build());
    } catch (IOException e) {
      String error =
          String.format("Failed to index a document %s", info.name,
              e.getMessage());
      log.error(error);
    }
  }

  @Override
  public List<ProjectInfo> getAllProjectInfo(EnumMap<MATCH, String> match) {
    return search(getSearchSourceBuilder(match));
  }

  @Override
  public List<ProjectInfo> getPageProjectInfo(int pageNumber, int pageSize,
      EnumMap<MATCH, String> match) {
    return search(getSearchSourceBuilder(match).size(pageSize).from(
        pageNumber * pageSize));
  }

  private SearchSourceBuilder getSearchSourceBuilder(
      EnumMap<MATCH, String> match) {
    if (match.isEmpty()) {
      return new SearchSourceBuilder().query(QueryBuilders.matchAllQuery());
    } else if (match.keySet().iterator().hasNext()) {
      switch (match.keySet().iterator().next()) {
        case PREFIX:
          return new SearchSourceBuilder().query(QueryBuilders.prefixQuery(
              DEFAULT_PROP_NAME, match.get(MATCH.PREFIX)));
        case SUBSTRING:
          return new SearchSourceBuilder().query(QueryBuilders.wildcardQuery(
              DEFAULT_PROP_NAME,
              String.format("*%s*", match.get(MATCH.SUBSTRING))));
        case REGEX:
          return new SearchSourceBuilder().query(QueryBuilders.regexpQuery(
              DEFAULT_PROP_NAME, match.get(MATCH.REGEX)));
        default:
          return null;
      }
    }
    return null;
  }

  private List<ProjectInfo> search(SearchSourceBuilder builder) {
    try {
      SearchResult result =
          client.execute(new Search.Builder(builder.toString())
              .addSort(new Sort(DEFAULT_PROP_NAME))
              .addIndex(DEFAULT_INDEX_NAME).build());
      if (result != null && result.isSucceeded()) {
        return result.getSourceAsObjectList(ProjectInfo.class);
      }
    } catch (IOException e) {
      String error =
          String.format("Failed to search documents", e.getMessage());
      log.error(error);
    }
    return null;
  }
}
