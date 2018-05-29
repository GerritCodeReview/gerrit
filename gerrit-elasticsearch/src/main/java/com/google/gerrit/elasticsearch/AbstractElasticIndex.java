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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.binary.Base64.decodeBase64;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.io.CharStreams;
import com.google.gerrit.elasticsearch.builders.SearchSourceBuilder;
import com.google.gerrit.elasticsearch.builders.XContentBuilder;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gwtorm.protobuf.ProtobufCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

abstract class AbstractElasticIndex<K, V> implements Index<K, V> {

  protected static final String BULK = "_bulk";
  protected static final String DELETE = "delete";
  protected static final String IGNORE_UNMAPPED = "ignore_unmapped";
  protected static final String INDEX = "index";
  protected static final String ORDER = "order";
  protected static final String SEARCH = "_search";

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

  static String getContent(Response response) throws IOException {
    HttpEntity responseEntity = response.getEntity();
    String content = "";
    if (responseEntity != null) {
      InputStream contentStream = responseEntity.getContent();
      try (Reader reader = new InputStreamReader(contentStream)) {
        content = CharStreams.toString(reader);
      }
    }
    return content;
  }

  private final Schema<V> schema;
  private final FillArgs fillArgs;
  private final SitePaths sitePaths;
  private final String indexNameRaw;
  private final RestClient client;

  protected final String indexName;
  protected final Gson gson;
  protected final ElasticQueryBuilder queryBuilder;

  AbstractElasticIndex(
      @GerritServerConfig Config cfg,
      FillArgs fillArgs,
      SitePaths sitePaths,
      Schema<V> schema,
      ElasticRestClientBuilder clientBuilder,
      String indexName) {
    this.fillArgs = fillArgs;
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
    try {
      client.close();
    } catch (IOException e) {
      // Ignored.
    }
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    IndexUtils.setReady(sitePaths, indexNameRaw, schema.getVersion(), ready);
  }

  @Override
  public void delete(K c) throws IOException {
    String uri = getURI(indexNameRaw, BULK);
    Response response = performRequest(HttpPost.METHOD_NAME, addActions(c), uri, getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new IOException(
          String.format("Failed to delete %s from index %s: %s", c, indexName, statusCode));
    }
  }

  @Override
  public void deleteAll() throws IOException {
    // Delete the index, if it exists.
    Response response = client.performRequest(HttpHead.METHOD_NAME, indexName);
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == HttpStatus.SC_OK) {
      response = client.performRequest(HttpDelete.METHOD_NAME, indexName);
      statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != HttpStatus.SC_OK) {
        throw new IOException(
            String.format("Failed to delete index %s: %s", indexName, statusCode));
      }
    }

    // Recreate the index.
    response =
        performRequest(HttpPut.METHOD_NAME, getMappings(), indexName, Collections.emptyMap());
    statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      String error = String.format("Failed to create index %s: %s", indexName, statusCode);
      throw new IOException(error);
    }
  }

  protected abstract String addActions(K c);

  protected abstract String getMappings();

  protected abstract String getId(V v);

  protected String delete(String type, K c) {
    String id = c.toString();
    return toAction(type, id, DELETE);
  }

  private static boolean shouldAddElement(Object element) {
    return !(element instanceof String) || !((String) element).isEmpty();
  }

  protected String toDoc(V v) throws IOException {
    try (XContentBuilder closeable = new XContentBuilder()) {
      XContentBuilder builder = closeable.startObject();
      for (Values<V> values : schema.buildFields(v, fillArgs)) {
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
      return builder.endObject().string() + System.lineSeparator();
    }
  }

  protected String toAction(String type, String id, String action) {
    JsonObject properties = new JsonObject();
    properties.addProperty("_id", id);
    properties.addProperty("_index", indexName);
    properties.addProperty("_type", type);

    JsonObject jsonAction = new JsonObject();
    jsonAction.add(action, properties);
    return jsonAction.toString() + System.lineSeparator();
  }

  protected void addNamedElement(String name, JsonObject element, JsonArray array) {
    JsonObject arrayElement = new JsonObject();
    arrayElement.add(name, element);
    array.add(arrayElement);
  }

  protected Map<String, String> getRefreshParam() {
    Map<String, String> params = new HashMap<>();
    params.put("refresh", "true");
    return params;
  }

  protected String getSearch(SearchSourceBuilder searchSource, JsonArray sortArray) {
    JsonObject search = new JsonParser().parse(searchSource.toString()).getAsJsonObject();
    search.add("sort", sortArray);
    return gson.toJson(search);
  }

  protected JsonArray getSortArray(String idFieldName) {
    JsonObject properties = new JsonObject();
    properties.addProperty(ORDER, "asc");
    properties.addProperty(IGNORE_UNMAPPED, true);

    JsonArray sortArray = new JsonArray();
    addNamedElement(idFieldName, properties, sortArray);
    return sortArray;
  }

  protected String getURI(String type, String request) throws UnsupportedEncodingException {
    String encodedType = URLEncoder.encode(type, UTF_8.toString());
    String encodedIndexName = URLEncoder.encode(indexName, UTF_8.toString());
    return encodedIndexName + "/" + encodedType + "/" + request;
  }

  protected Response performRequest(
      String method, String payload, String uri, Map<String, String> params) throws IOException {
    HttpEntity entity = new NStringEntity(payload, ContentType.APPLICATION_JSON);
    return client.performRequest(method, uri, params, entity);
  }
}
