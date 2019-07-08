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
import static org.apache.commons.codec.binary.Base64.decodeBase64;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.elasticsearch.builders.SearchSourceBuilder;
import com.google.gerrit.elasticsearch.bulk.DeleteRequest;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.Schema;
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
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

abstract class AbstractElasticIndex<K, V> implements Index<K, V> {
  protected static final String BULK = "_bulk";
  protected static final String MAPPINGS = "mappings";
  protected static final String ORDER = "order";
  protected static final String SEARCH = "_search";
  protected static final String SETTINGS = "settings";

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
      try (Reader reader = new InputStreamReader(contentStream, UTF_8)) {
        content = CharStreams.toString(reader);
      }
    }
    return content;
  }

  private final Schema<V> schema;
  private final SitePaths sitePaths;
  private final String indexNameRaw;

  protected final String type;
  protected final ElasticRestClientProvider client;
  protected final String indexName;
  protected final Gson gson;
  protected final ElasticQueryBuilder queryBuilder;

  AbstractElasticIndex(
      ElasticConfiguration cfg,
      SitePaths sitePaths,
      Schema<V> schema,
      ElasticRestClientProvider client,
      String indexName,
      String indexType) {
    this.sitePaths = sitePaths;
    this.schema = schema;
    this.gson = new GsonBuilder().setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES).create();
    this.queryBuilder = new ElasticQueryBuilder();
    this.indexName = cfg.getIndexName(indexName, schema.getVersion());
    this.indexNameRaw = indexName;
    this.client = client;
    this.type = client.adapter().getType(indexType);
  }

  AbstractElasticIndex(
      ElasticConfiguration cfg,
      SitePaths sitePaths,
      Schema<V> schema,
      ElasticRestClientProvider client,
      String indexName) {
    this(cfg, sitePaths, schema, client, indexName, indexName);
  }

  @Override
  public Schema<V> getSchema() {
    return schema;
  }

  @Override
  public void close() {
    // Do nothing. Client is closed by the provider.
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    IndexUtils.setReady(sitePaths, indexNameRaw, schema.getVersion(), ready);
  }

  @Override
  public void delete(K id) throws IOException {
    String uri = getURI(type, BULK);
    Response response = postRequest(uri, getDeleteActions(id), getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new IOException(
          String.format("Failed to delete %s from index %s: %s", id, indexName, statusCode));
    }
  }

  @Override
  public void deleteAll() throws IOException {
    // Delete the index, if it exists.
    String endpoint = indexName + client.adapter().indicesExistParam();
    Response response = performRequest("HEAD", endpoint);
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == HttpStatus.SC_OK) {
      response = performRequest("DELETE", indexName);
      statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != HttpStatus.SC_OK) {
        throw new IOException(
            String.format("Failed to delete index %s: %s", indexName, statusCode));
      }
    }

    // Recreate the index.
    String indexCreationFields = concatJsonString(getSettings(), getMappings());
    response = performRequest("PUT", indexName, indexCreationFields);
    statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      String error = String.format("Failed to create index %s: %s", indexName, statusCode);
      throw new IOException(error);
    }
  }

  protected abstract String getDeleteActions(K id);

  protected abstract String getMappings();

  private String getSettings() {
    return gson.toJson(ImmutableMap.of(SETTINGS, ElasticSetting.createSetting()));
  }

  protected abstract String getId(V v);

  protected String getMappingsForSingleType(String candidateType, MappingProperties properties) {
    return getMappingsFor(client.adapter().getType(candidateType), properties);
  }

  protected String getMappingsFor(String type, MappingProperties properties) {
    JsonObject mappingType = new JsonObject();
    mappingType.add(type, gson.toJsonTree(properties));
    JsonObject mappings = new JsonObject();
    mappings.add(MAPPINGS, gson.toJsonTree(mappingType));
    return gson.toJson(mappings);
  }

  protected String delete(String type, K id) {
    return new DeleteRequest(id.toString(), indexName, type, client.adapter()).toString();
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
    client.adapter().setIgnoreUnmapped(properties);

    JsonArray sortArray = new JsonArray();
    addNamedElement(idFieldName, properties, sortArray);
    return sortArray;
  }

  protected String getURI(String type, String request) throws UnsupportedEncodingException {
    String encodedType = URLEncoder.encode(type, UTF_8.toString());
    String encodedIndexName = URLEncoder.encode(indexName, UTF_8.toString());
    return encodedIndexName + "/" + encodedType + "/" + request;
  }

  protected Response postRequest(String uri, Object payload) throws IOException {
    return performRequest("POST", uri, payload);
  }

  protected Response postRequest(String uri, Object payload, Map<String, String> params)
      throws IOException {
    return performRequest("POST", uri, payload, params);
  }

  private String concatJsonString(String target, String addition) {
    return target.substring(0, target.length() - 1) + "," + addition.substring(1);
  }

  private Response performRequest(String method, String uri) throws IOException {
    return performRequest(method, uri, null);
  }

  private Response performRequest(String method, String uri, @Nullable Object payload)
      throws IOException {
    return performRequest(method, uri, payload, Collections.emptyMap());
  }

  private Response performRequest(
      String method, String uri, @Nullable Object payload, Map<String, String> params)
      throws IOException {
    Request request = new Request(method, uri.startsWith("/") ? uri : "/" + uri);
    if (payload != null) {
      String payloadStr = payload instanceof String ? (String) payload : payload.toString();
      request.setEntity(new NStringEntity(payloadStr, ContentType.APPLICATION_JSON));
    }
    for (Map.Entry<String, String> entry : params.entrySet()) {
      request.addParameter(entry.getKey(), entry.getValue());
    }
    return client.get().performRequest(request);
  }
}
