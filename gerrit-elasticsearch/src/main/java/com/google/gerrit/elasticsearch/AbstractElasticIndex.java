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

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.binary.Base64.decodeBase64;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.Schema.Values;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gwtorm.protobuf.ProtobufCodec;
import io.searchbox.client.JestResult;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.core.Bulk;
import io.searchbox.core.Delete;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;
import io.searchbox.indices.IndicesExists;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.common.xcontent.XContentBuilder;

abstract class AbstractElasticIndex<K, V> implements Index<K, V> {
  protected static <T> List<T> decodeProtos(
      JsonObject doc, String fieldName, ProtobufCodec<T> codec) {
    JsonArray field = doc.getAsJsonArray(fieldName);
    if (field == null) {
      return null;
    }
    return FluentIterable.from(field)
        .transform(i -> codec.decode(decodeBase64(i.toString())))
        .toList();
  }

  private final Schema<V> schema;
  private final SitePaths sitePaths;
  private final String indexNameRaw;

  protected final String indexName;
  protected final JestHttpClient client;
  protected final Gson gson;
  protected final ElasticQueryBuilder queryBuilder;

  AbstractElasticIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Schema<V> schema,
      JestClientBuilder clientBuilder,
      String indexName) {
    this.sitePaths = sitePaths;
    this.schema = schema;
    this.gson = new GsonBuilder().setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES).create();
    this.queryBuilder = new ElasticQueryBuilder();
    this.indexName =
        String.format(
            "%s%s_%04d",
            Strings.nullToEmpty(cfg.getString("elasticsearch", null, "prefix")),
            indexName,
            schema.getVersion());
    this.indexNameRaw = indexName;
    this.client = clientBuilder.build();
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
    IndexUtils.setReady(sitePaths, indexNameRaw, schema.getVersion(), ready);
  }

  @Override
  public void delete(K c) throws IOException {
    Bulk bulk = addActions(new Bulk.Builder(), c).refresh(true).build();
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

  private static boolean shouldAddElement(Object element) {
    return !(element instanceof String) || !((String) element).isEmpty();
  }

  private String toDoc(V v) throws IOException {
    try (XContentBuilder builder = jsonBuilder().startObject()) {
      for (Values<V> values : schema.buildFields(v)) {
        String name = values.getField().getName();
        if (values.getField().isRepeatable()) {
          builder.field(
              name,
              Streams.stream(values.getValues())
                  .filter(e -> shouldAddElement(e))
                  .collect(toList()));
        } else {
          Object element = Iterables.getOnlyElement(values.getValues(), "");
          if (shouldAddElement(element)) {
            builder.field(name, element);
          }
        }
      }
      return builder.endObject().string();
    }
  }
}
