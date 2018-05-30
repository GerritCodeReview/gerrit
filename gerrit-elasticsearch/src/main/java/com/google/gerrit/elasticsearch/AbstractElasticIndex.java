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
import com.google.common.io.CharStreams;
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
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

abstract class AbstractElasticIndex<K, V> implements Index<K, V> {
  protected static final String BULK = "_bulk";
  protected static final String IGNORE_UNMAPPED = "ignore_unmapped";
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
  private final SitePaths sitePaths;
  private final String indexNameRaw;
  private final RestClient client;

  protected final String indexName;
  protected final Gson gson;
  protected final ElasticQueryBuilder queryBuilder;

  AbstractElasticIndex(
      ElasticConfiguration cfg,
      SitePaths sitePaths,
      Schema<V> schema,
      ElasticRestClientBuilder clientBuilder,
      String indexName) {
    this.sitePaths = sitePaths;
    this.schema = schema;
    this.gson = new GsonBuilder().setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES).create();
    this.queryBuilder = new ElasticQueryBuilder();
    this.indexName = cfg.getIndexName(indexName, schema.getVersion());
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
    Response response = postRequest(addActions(c), uri, getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new IOException(
          String.format("Failed to delete %s from index %s: %s", c, indexName, statusCode));
    }
  }

  @Override
  public void deleteAll() throws IOException {
    // Delete the index, if it exists.
    Response response = client.performRequest("HEAD", indexName);
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == HttpStatus.SC_OK) {
      response = client.performRequest("DELETE", indexName);
      statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != HttpStatus.SC_OK) {
        throw new IOException(
            String.format("Failed to delete index %s: %s", indexName, statusCode));
      }
    }

    // Recreate the index.
    response = performRequest("PUT", getMappings(), indexName, Collections.emptyMap());
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
    return new DeleteRequest(id, indexNameRaw, type).toString();
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

  protected Response postRequest(Object payload, String uri, Map<String, String> params)
      throws IOException {
    return performRequest("POST", payload, uri, params);
  }

  private Response performRequest(
      String method, Object payload, String uri, Map<String, String> params) throws IOException {
    String payloadStr = payload instanceof String ? (String) payload : payload.toString();
    HttpEntity entity = new NStringEntity(payloadStr, ContentType.APPLICATION_JSON);
    return client.performRequest(method, uri, params, entity);
  }
}
