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

/**
 * A Query that matches documents containing a term.
 *
 * <p>A trimmed down version of org.elasticsearch.index.query.TermQueryBuilder.
 */
class TermQueryBuilder extends QueryBuilder {

  private final String name;

  private final Object value;

  /**
   * Constructs a new term query.
   *
   * @param name The name of the field
   * @param value The value of the term
   */
  TermQueryBuilder(String name, String value) {
    this(name, (Object) value);
  }

  /**
   * Constructs a new term query.
   *
   * @param name The name of the field
   * @param value The value of the term
   */
  TermQueryBuilder(String name, int value) {
    this(name, (Object) value);
  }

  /**
   * Constructs a new term query.
   *
   * @param name The name of the field
   * @param value The value of the term
   */
  private TermQueryBuilder(String name, Object value) {
    this.name = name;
    this.value = value;
  }

  @Override
  protected void doXContent(XContentBuilder builder) throws IOException {
    builder.startObject("term");
    builder.field(name, value);
    builder.endObject();
  }
}
