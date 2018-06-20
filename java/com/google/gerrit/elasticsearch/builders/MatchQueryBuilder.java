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

import java.io.IOException;
import java.util.Locale;

/**
 * Match query is a query that analyzes the text and constructs a query as the result of the
 * analysis. It can construct different queries based on the type provided.
 *
 * <p>A trimmed down version of org.elasticsearch.index.query.MatchQueryBuilder.
 */
class MatchQueryBuilder extends QueryBuilder {

  enum Type {
    /** The text is analyzed and used as a phrase query. */
    MATCH_PHRASE,
    /** The text is analyzed and used in a phrase query, with the last term acting as a prefix. */
    MATCH_PHRASE_PREFIX;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.US);
    }
  }

  private final String name;

  private final Object text;

  private Type type;

  /** Constructs a new text query. */
  MatchQueryBuilder(String name, Object text) {
    this.name = name;
    this.text = text;
  }

  /** Sets the type of the text query. */
  MatchQueryBuilder type(Type type) {
    this.type = type;
    return this;
  }

  @Override
  protected void doXContent(XContentBuilder builder) throws IOException {
    builder.startObject(type.toString()).field(name, text).endObject();
  }
}
