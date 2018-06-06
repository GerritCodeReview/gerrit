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

/**
 * A static factory for simple "import static" usage.
 *
 * <p>A trimmed down version of org.elasticsearch.index.query.QueryBuilders.
 */
public abstract class QueryBuilders {

  /** A query that match on all documents. */
  public static MatchAllQueryBuilder matchAllQuery() {
    return new MatchAllQueryBuilder();
  }

  /**
   * Creates a text query with type "PHRASE" for the provided field name and text.
   *
   * @param name The field name.
   * @param text The query text (to be analyzed).
   */
  public static MatchQueryBuilder matchPhraseQuery(String name, Object text) {
    return new MatchQueryBuilder(name, text).type(MatchQueryBuilder.Type.MATCH_PHRASE);
  }

  /**
   * Creates a match query with type "PHRASE_PREFIX" for the provided field name and text.
   *
   * @param name The field name.
   * @param text The query text (to be analyzed).
   */
  public static MatchQueryBuilder matchPhrasePrefixQuery(String name, Object text) {
    return new MatchQueryBuilder(name, text).type(MatchQueryBuilder.Type.MATCH_PHRASE_PREFIX);
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name The name of the field
   * @param value The value of the term
   */
  public static TermQueryBuilder termQuery(String name, String value) {
    return new TermQueryBuilder(name, value);
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name The name of the field
   * @param value The value of the term
   */
  public static TermQueryBuilder termQuery(String name, int value) {
    return new TermQueryBuilder(name, value);
  }

  /**
   * A Query that matches documents within an range of terms.
   *
   * @param name The field name
   */
  public static RangeQueryBuilder rangeQuery(String name) {
    return new RangeQueryBuilder(name);
  }

  /**
   * A Query that matches documents containing terms with a specified regular expression.
   *
   * @param name The name of the field
   * @param regexp The regular expression
   */
  public static RegexpQueryBuilder regexpQuery(String name, String regexp) {
    return new RegexpQueryBuilder(name, regexp);
  }

  /** A Query that matches documents matching boolean combinations of other queries. */
  public static BoolQueryBuilder boolQuery() {
    return new BoolQueryBuilder();
  }

  /**
   * A filter to filter only documents where a field exists in them.
   *
   * @param name The name of the field
   */
  public static ExistsQueryBuilder existsQuery(String name) {
    return new ExistsQueryBuilder(name);
  }

  private QueryBuilders() {}
}
