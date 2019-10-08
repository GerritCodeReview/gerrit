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

import com.google.gerrit.elasticsearch.builders.BoolQueryBuilder;
import com.google.gerrit.elasticsearch.builders.QueryBuilder;
import com.google.gerrit.elasticsearch.builders.QueryBuilders;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.FieldType;
import com.google.gerrit.index.query.AndPredicate;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.IntegerRangePredicate;
import com.google.gerrit.index.query.NotPredicate;
import com.google.gerrit.index.query.OrPredicate;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.RegexPredicate;
import com.google.gerrit.index.query.TimestampRangePredicate;
import com.google.gerrit.server.query.change.AfterPredicate;
import java.time.Instant;

public class ElasticQueryBuilder {

  <T> QueryBuilder toQueryBuilder(Predicate<T> p) throws QueryParseException {
    if (p instanceof AndPredicate) {
      return and(p);
    } else if (p instanceof OrPredicate) {
      return or(p);
    } else if (p instanceof NotPredicate) {
      return not(p);
    } else if (p instanceof IndexPredicate) {
      return fieldQuery((IndexPredicate<T>) p);
    } else if (p instanceof PostFilterPredicate) {
      return QueryBuilders.matchAllQuery();
    } else {
      throw new QueryParseException("cannot create query for index: " + p);
    }
  }

  private <T> BoolQueryBuilder and(Predicate<T> p) throws QueryParseException {
    BoolQueryBuilder b = QueryBuilders.boolQuery();
    for (Predicate<T> c : p.getChildren()) {
      b.must(toQueryBuilder(c));
    }
    return b;
  }

  private <T> BoolQueryBuilder or(Predicate<T> p) throws QueryParseException {
    BoolQueryBuilder q = QueryBuilders.boolQuery();
    for (Predicate<T> c : p.getChildren()) {
      q.should(toQueryBuilder(c));
    }
    return q;
  }

  private <T> QueryBuilder not(Predicate<T> p) throws QueryParseException {
    Predicate<T> n = p.getChild(0);
    if (n instanceof TimestampRangePredicate) {
      return notTimestamp((TimestampRangePredicate<T>) n);
    }

    // Lucene does not support negation, start with all and subtract.
    BoolQueryBuilder q = QueryBuilders.boolQuery();
    q.must(QueryBuilders.matchAllQuery());
    q.mustNot(toQueryBuilder(n));
    return q;
  }

  private <T> QueryBuilder fieldQuery(IndexPredicate<T> p) throws QueryParseException {
    FieldType<?> type = p.getType();
    FieldDef<?, ?> field = p.getField();
    String name = field.getName();
    String value = p.getValue();

    if (type == FieldType.INTEGER) {
      // QueryBuilder encodes integer fields as prefix coded bits,
      // which elasticsearch's queryString can't handle.
      // Create integer terms with string representations instead.
      return QueryBuilders.termQuery(name, value);
    } else if (type == FieldType.INTEGER_RANGE) {
      return intRangeQuery(p);
    } else if (type == FieldType.TIMESTAMP) {
      return timestampQuery(p);
    } else if (type == FieldType.EXACT) {
      return exactQuery(p);
    } else if (type == FieldType.PREFIX) {
      return QueryBuilders.matchPhrasePrefixQuery(name, value);
    } else if (type == FieldType.FULL_TEXT) {
      return QueryBuilders.matchPhraseQuery(name, value);
    } else {
      throw FieldType.badFieldType(p.getType());
    }
  }

  private <T> QueryBuilder intRangeQuery(IndexPredicate<T> p) throws QueryParseException {
    if (p instanceof IntegerRangePredicate) {
      IntegerRangePredicate<T> r = (IntegerRangePredicate<T>) p;
      int minimum = r.getMinimumValue();
      int maximum = r.getMaximumValue();
      if (minimum == maximum) {
        // Just fall back to a standard integer query.
        return QueryBuilders.termQuery(p.getField().getName(), minimum);
      }
      return QueryBuilders.rangeQuery(p.getField().getName()).gte(minimum).lte(maximum);
    }
    throw new QueryParseException("not an integer range: " + p);
  }

  private <T> QueryBuilder notTimestamp(TimestampRangePredicate<T> r) throws QueryParseException {
    if (r.getMinTimestamp().getTime() == 0) {
      return QueryBuilders.rangeQuery(r.getField().getName())
          .gt(Instant.ofEpochMilli(r.getMaxTimestamp().getTime()));
    }
    throw new QueryParseException("cannot negate: " + r);
  }

  private <T> QueryBuilder timestampQuery(IndexPredicate<T> p) throws QueryParseException {
    if (p instanceof TimestampRangePredicate) {
      TimestampRangePredicate<T> r = (TimestampRangePredicate<T>) p;
      if (p instanceof AfterPredicate) {
        return QueryBuilders.rangeQuery(r.getField().getName())
            .gte(Instant.ofEpochMilli(r.getMinTimestamp().getTime()));
      }
      return QueryBuilders.rangeQuery(r.getField().getName())
          .gte(Instant.ofEpochMilli(r.getMinTimestamp().getTime()))
          .lte(Instant.ofEpochMilli(r.getMaxTimestamp().getTime()));
    }
    throw new QueryParseException("not a timestamp: " + p);
  }

  private <T> QueryBuilder exactQuery(IndexPredicate<T> p) {
    String name = p.getField().getName();
    String value = p.getValue();

    if (!p.getField().isRepeatable() && value.isEmpty()) {
      return new BoolQueryBuilder().mustNot(QueryBuilders.existsQuery(name));
    } else if (p instanceof RegexPredicate) {
      if (value.startsWith("^")) {
        value = value.substring(1);
      }
      if (value.endsWith("$") && !value.endsWith("\\$") && !value.endsWith("\\\\$")) {
        value = value.substring(0, value.length() - 1);
      }
      return QueryBuilders.regexpQuery(name + ".key", value);
    } else {
      return QueryBuilders.termQuery(name + ".key", value);
    }
  }
}
