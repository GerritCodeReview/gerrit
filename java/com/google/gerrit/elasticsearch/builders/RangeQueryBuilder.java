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
 * A Query that matches documents within an range of terms.
 *
 * <p>A trimmed down version of org.elasticsearch.index.query.RangeQueryBuilder.
 */
public class RangeQueryBuilder extends QueryBuilder {

  private final String name;
  private Object from;
  private Object to;
  private boolean includeLower = true;
  private boolean includeUpper = true;

  /**
   * A Query that matches documents within an range of terms.
   *
   * @param name The field name
   */
  RangeQueryBuilder(String name) {
    this.name = name;
  }

  /** The from part of the range query. Null indicates unbounded. */
  public RangeQueryBuilder gt(Object from) {
    this.from = from;
    this.includeLower = false;
    return this;
  }

  /** The from part of the range query. Null indicates unbounded. */
  public RangeQueryBuilder gte(Object from) {
    this.from = from;
    this.includeLower = true;
    return this;
  }

  /** The from part of the range query. Null indicates unbounded. */
  public RangeQueryBuilder gte(int from) {
    this.from = from;
    this.includeLower = true;
    return this;
  }

  /** The to part of the range query. Null indicates unbounded. */
  public RangeQueryBuilder lte(Object to) {
    this.to = to;
    this.includeUpper = true;
    return this;
  }

  /** The to part of the range query. Null indicates unbounded. */
  public RangeQueryBuilder lte(int to) {
    this.to = to;
    this.includeUpper = true;
    return this;
  }

  @Override
  protected void doXContent(XContentBuilder builder) throws IOException {
    builder.startObject("range");
    builder.startObject(name);

    builder.field("from", from);
    builder.field("to", to);
    builder.field("include_lower", includeLower);
    builder.field("include_upper", includeUpper);

    builder.endObject();
    builder.endObject();
  }
}
