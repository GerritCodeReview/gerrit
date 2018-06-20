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
import java.util.ArrayList;
import java.util.List;

/**
 * A Query that matches documents matching boolean combinations of other queries.
 *
 * <p>A trimmed down version of org.elasticsearch.index.query.BoolQueryBuilder.
 */
public class BoolQueryBuilder extends QueryBuilder {

  private final List<QueryBuilder> mustClauses = new ArrayList<>();

  private final List<QueryBuilder> mustNotClauses = new ArrayList<>();

  private final List<QueryBuilder> filterClauses = new ArrayList<>();

  private final List<QueryBuilder> shouldClauses = new ArrayList<>();

  /**
   * Adds a query that <b>must</b> appear in the matching documents and will contribute to scoring.
   */
  public BoolQueryBuilder must(QueryBuilder queryBuilder) {
    mustClauses.add(queryBuilder);
    return this;
  }

  /**
   * Adds a query that <b>must not</b> appear in the matching documents and will not contribute to
   * scoring.
   */
  public BoolQueryBuilder mustNot(QueryBuilder queryBuilder) {
    mustNotClauses.add(queryBuilder);
    return this;
  }

  /**
   * Adds a query that <i>should</i> appear in the matching documents. For a boolean query with no
   * <tt>MUST</tt> clauses one or more <code>SHOULD</code> clauses must match a document for the
   * BooleanQuery to match.
   */
  public BoolQueryBuilder should(QueryBuilder queryBuilder) {
    shouldClauses.add(queryBuilder);
    return this;
  }

  @Override
  protected void doXContent(XContentBuilder builder) throws IOException {
    builder.startObject("bool");
    doXArrayContent("must", mustClauses, builder);
    doXArrayContent("filter", filterClauses, builder);
    doXArrayContent("must_not", mustNotClauses, builder);
    doXArrayContent("should", shouldClauses, builder);
    builder.endObject();
  }

  private void doXArrayContent(String field, List<QueryBuilder> clauses, XContentBuilder builder)
      throws IOException {
    if (clauses.isEmpty()) {
      return;
    }
    if (clauses.size() == 1) {
      builder.field(field);
      clauses.get(0).toXContent(builder);
    } else {
      builder.startArray(field);
      for (QueryBuilder clause : clauses) {
        clause.toXContent(builder);
      }
      builder.endArray();
    }
  }
}
