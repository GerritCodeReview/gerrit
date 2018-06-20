// Copyright (C) 2018 The Android Open Source Project, 2009-2015 Elasticsearch
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

package com.google.gerrit.elasticsearch.builders;

import com.google.gerrit.elasticsearch.ElasticQueryAdapter;
import java.io.IOException;
import java.util.List;

/**
 * A search source builder allowing to easily build search source.
 *
 * <p>A trimmed down and modified version of org.elasticsearch.search.builder.SearchSourceBuilder.
 */
public class SearchSourceBuilder {
  private final ElasticQueryAdapter adapter;

  private QuerySourceBuilder querySourceBuilder;

  private int from = -1;

  private int size = -1;

  private List<String> fieldNames;

  /** Constructs a new search source builder. */
  public SearchSourceBuilder(ElasticQueryAdapter adapter) {
    this.adapter = adapter;
  }

  /** Constructs a new search source builder with a search query. */
  public SearchSourceBuilder query(QueryBuilder query) {
    if (this.querySourceBuilder == null) {
      this.querySourceBuilder = new QuerySourceBuilder(query);
    }
    return this;
  }

  /** From index to start the search from. Defaults to <tt>0</tt>. */
  public SearchSourceBuilder from(int from) {
    this.from = from;
    return this;
  }

  /** The number of search hits to return. Defaults to <tt>10</tt>. */
  public SearchSourceBuilder size(int size) {
    this.size = size;
    return this;
  }

  /**
   * Sets the fields to load and return as part of the search request. If none are specified, the
   * source of the document will be returned.
   */
  public SearchSourceBuilder fields(List<String> fields) {
    this.fieldNames = fields;
    return this;
  }

  @Override
  public final String toString() {
    try {
      XContentBuilder builder = new XContentBuilder();
      toXContent(builder);
      return builder.string();
    } catch (IOException ioe) {
      return "";
    }
  }

  private void toXContent(XContentBuilder builder) throws IOException {
    builder.startObject();
    innerToXContent(builder);
    builder.endObject();
  }

  private void innerToXContent(XContentBuilder builder) throws IOException {
    if (from != -1) {
      builder.field("from", from);
    }
    if (size != -1) {
      builder.field("size", size);
    }

    if (querySourceBuilder != null) {
      querySourceBuilder.innerToXContent(builder);
    }

    if (fieldNames != null) {
      if (fieldNames.size() == 1) {
        builder.field(adapter.searchFilteringName(), fieldNames.get(0));
      } else {
        builder.startArray(adapter.searchFilteringName());
        for (String fieldName : fieldNames) {
          builder.value(fieldName);
        }
        builder.endArray();
      }
    }
  }
}
