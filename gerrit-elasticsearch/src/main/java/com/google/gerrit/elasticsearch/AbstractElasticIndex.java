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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.index.IndexUtils;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.core.Bulk;
import io.searchbox.core.Delete;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;
import io.searchbox.indices.IndicesExists;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.common.xcontent.XContentBuilder;

abstract class AbstractElasticIndex<K, V> implements Index<K, V> {
  private static final String DEFAULT_INDEX_NAME = "gerrit";

  private final Schema<V> schema;
  private final FillArgs fillArgs;
  private final SitePaths sitePaths;

  protected final boolean refresh;
  protected final String indexName;
  protected final JestHttpClient client;

  @Inject
  AbstractElasticIndex(
      @GerritServerConfig Config cfg,
      FillArgs fillArgs,
      SitePaths sitePaths,
      @Assisted Schema<V> schema) {
    this.fillArgs = fillArgs;
    this.sitePaths = sitePaths;
    this.schema = schema;
    String protocol = getRequiredConfigOption(cfg, "protocol");
    String hostname = getRequiredConfigOption(cfg, "hostname");
    String port = getRequiredConfigOption(cfg, "port");

    this.indexName = firstNonNull(cfg.getString("index", null, "name"), DEFAULT_INDEX_NAME);

    // By default Elasticsearch has a 1s delay before changes are available in
    // the index.  Setting refresh(true) on calls to the index makes the index
    // refresh immediately.
    //
    // Discovery should be disabled during test mode to prevent spurious
    // connection failures caused by the client starting up and being ready
    // before the test node.
    //
    // This setting should only be set to true during testing, and is not
    // documented.
    this.refresh = cfg.getBoolean("index", "elasticsearch", "test", false);

    String url = buildUrl(protocol, hostname, port);
    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(
        new HttpClientConfig.Builder(url)
            .multiThreaded(true)
            .discoveryEnabled(!refresh)
            .discoveryFrequency(1L, TimeUnit.MINUTES)
            .build());
    client = (JestHttpClient) factory.getObject();
  }

  @Override
  public Schema<V> getSchema() {
    return schema;
  }

  @Override
  public void close() {
    client.shutdownClient();
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    IndexUtils.setReady(sitePaths, indexName, schema.getVersion(), ready);
  }

  @Override
  public void delete(K c) throws IOException {
    Bulk bulk = addActions(new Bulk.Builder(), c).refresh(refresh).build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(
          String.format(
              "Failed to delete change %s in index %s: %s",
              c, indexName, result.getErrorMessage()));
    }
  }

  @Override
  public void deleteAll() throws IOException {
    // Delete the index, if it exists.
    JestResult result = client.execute(new IndicesExists.Builder(indexName).build());
    if (result.isSucceeded()) {
      result = client.execute(new DeleteIndex.Builder(indexName).build());
      if (!result.isSucceeded()) {
        throw new IOException(
            String.format("Failed to delete index %s: %s", indexName, result.getErrorMessage()));
      }
    }

    // Recreate the index.
    result = client.execute(new CreateIndex.Builder(indexName).settings(getMappings()).build());
    if (!result.isSucceeded()) {
      String error =
          String.format("Failed to create index %s: %s", indexName, result.getErrorMessage());
      throw new IOException(error);
    }
  }

  protected abstract Bulk.Builder addActions(Bulk.Builder builder, K c);

  protected abstract String getMappings();

  protected abstract String getId(V v);

  protected Delete delete(String type, K c) {
    String id = c.toString();
    return new Delete.Builder(id).index(indexName).type(type).build();
  }

  protected io.searchbox.core.Index insert(String type, V v) throws IOException {
    String id = getId(v);
    String doc = toDoc(v);
    return new io.searchbox.core.Index.Builder(doc).index(indexName).type(type).id(id).build();
  }

  private String toDoc(V v) throws IOException {
    XContentBuilder builder = jsonBuilder().startObject();
    for (Values<V> values : schema.buildFields(v, fillArgs)) {
      String name = values.getField().getName();
      if (values.getField().isRepeatable()) {
        builder.array(name, values.getValues());
      } else {
        Object element = Iterables.getOnlyElement(values.getValues(), "");
        if (!(element instanceof String) || !((String) element).isEmpty()) {
          builder.field(name, element);
        }
      }
    }
    return builder.endObject().string();
  }

  private String getRequiredConfigOption(Config cfg, String name) {
    String option = cfg.getString("index", null, name);
    checkState(!Strings.isNullOrEmpty(option), "index." + name + " must be supplied");
    return option;
  }

  private String buildUrl(String protocol, String hostname, String port) {
    try {
      return new URL(protocol, hostname, Integer.parseInt(port), "").toString();
    } catch (MalformedURLException | NumberFormatException e) {
      throw new RuntimeException(
          "Cannot build url to Elasticsearch from values: protocol="
              + protocol
              + " hostname="
              + hostname
              + " port="
              + port,
          e);
    }
  }
}
