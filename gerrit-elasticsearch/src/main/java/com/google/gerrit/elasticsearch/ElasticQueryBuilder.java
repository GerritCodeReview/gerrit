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

import com.google.common.collect.Lists;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.IntegerRangePredicate;
import com.google.gerrit.server.index.RegexPredicate;
import com.google.gerrit.server.index.TimestampRangePredicate;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.AfterPredicate;
import com.google.gerrit.server.query.change.ChangeData;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.joda.time.DateTime;

import java.util.List;

public class ElasticQueryBuilder extends com.google.gerrit.lucene.QueryBuilder {
  public ElasticQueryBuilder(Analyzer analyzer) {
    super(analyzer);
  }

  protected QueryBuilder toQueryBuilder(Predicate<ChangeData> p)
      throws QueryParseException {
    if (p instanceof AndPredicate) {
      return and(p);
    } else if (p instanceof OrPredicate) {
      return or(p);
    } else if (p instanceof NotPredicate) {
      return not(p);
    } else if (p instanceof IndexPredicate) {
      return fieldQuery((IndexPredicate<ChangeData>) p);
    } else {
      throw new QueryParseException("cannot create query for index: " + p);
    }
  }

  private BoolQueryBuilder and(Predicate<ChangeData> p)
      throws QueryParseException {
    try {
      BoolQueryBuilder b = QueryBuilders.boolQuery();
      List<QueryBuilder> not = Lists.newArrayListWithCapacity(p.getChildCount());
      for (Predicate<ChangeData> c : p.getChildren()) {
        if (c instanceof NotPredicate) {
          Predicate<ChangeData> n = c.getChild(0);
          if (n instanceof TimestampRangePredicate) {
            b.must(notTimestamp((TimestampRangePredicate<ChangeData>) n));
          } else {
            not.add(toQueryBuilder(n));
          }
        } else {
          b.must(toQueryBuilder(c));
        }
      }
      for (QueryBuilder q : not) {
        b.mustNot(q);
      }
      return b;
    } catch (BooleanQuery.TooManyClauses e) {
      throw new QueryParseException("cannot create query for index: " + p, e);
    }
  }

  private BoolQueryBuilder or(Predicate<ChangeData> p)
      throws QueryParseException {
    try {
      BoolQueryBuilder q = QueryBuilders.boolQuery();
      for (Predicate<ChangeData> c : p.getChildren()) {
        q.should(toQueryBuilder(c));
      }
      return q;
    } catch (BooleanQuery.TooManyClauses e) {
      throw new QueryParseException("cannot create query for index: " + p, e);
    }
  }

  private QueryBuilder not(Predicate<ChangeData> p)
      throws QueryParseException {
    Predicate<ChangeData> n = p.getChild(0);
    if (n instanceof TimestampRangePredicate) {
      return notTimestamp((TimestampRangePredicate<ChangeData>) n);
    }

    // Lucene does not support negation, start with all and subtract.
    BoolQueryBuilder q = QueryBuilders.boolQuery();
    q.must(QueryBuilders.matchAllQuery());
    q.mustNot(toQueryBuilder(n));
    return q;
  }

  private QueryBuilder fieldQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    FieldType<?> type = p.getType();
    FieldDef<?,?> field = p.getField();
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

  private QueryBuilder intRangeQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    if (p instanceof IntegerRangePredicate) {
      IntegerRangePredicate<ChangeData> r =
          (IntegerRangePredicate<ChangeData>) p;
      int minimum = r.getMinimumValue();
      int maximum = r.getMaximumValue();
      if (minimum == maximum) {
        // Just fall back to a standard integer query.
        return QueryBuilders.termQuery(p.getField().getName(), minimum);
      } else {
        return QueryBuilders.rangeQuery(p.getField().getName())
            .gte(minimum)
            .lte(maximum);
      }
    }
    throw new QueryParseException("not an integer range: " + p);
  }

  private QueryBuilder notTimestamp(TimestampRangePredicate<ChangeData> r)
      throws QueryParseException {
    if (r.getMinTimestamp().getTime() == 0) {
      return QueryBuilders.rangeQuery(r.getField().getName())
          .gt(new DateTime(r.getMaxTimestamp().getTime()));
    }
    throw new QueryParseException("cannot negate: " + r);
  }

  private QueryBuilder timestampQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    if (p instanceof TimestampRangePredicate) {
      TimestampRangePredicate<ChangeData> r =
          (TimestampRangePredicate<ChangeData>) p;
      if (p instanceof AfterPredicate) {
         // Don't add an upper limit for an AfterPredicate.
         // The DateTime created from date given by long.MAX_VALUE is
         // outside of the joda-time range. See
         // https://github.com/JodaOrg/joda-time/issues/190
        return QueryBuilders.rangeQuery(r.getField().getName())
            .gte(new DateTime(r.getMinTimestamp().getTime()));
      }
      return QueryBuilders.rangeQuery(r.getField().getName())
          .gte(new DateTime(r.getMinTimestamp().getTime()))
          .lte(new DateTime(r.getMaxTimestamp().getTime()));
    }
    throw new QueryParseException("not a timestamp: " + p);
  }

  private QueryBuilder exactQuery(IndexPredicate<ChangeData> p){
    String name = p.getField().getName();
    String value = p.getValue();

    if (value.isEmpty()) {
      return QueryBuilders.missingQuery(name);
    } else if (p instanceof RegexPredicate) {
      if (value.startsWith("^")) {
        value = value.substring(1);
      }
      if (value.endsWith("$") && !value.endsWith("\\$")) {
        value = value.substring(0, value.length() - 1);
      }
      return QueryBuilders.regexpQuery(name + ".key", value);
    } else {
      return QueryBuilders.termQuery(name + ".key", value);
    }
  }
}
