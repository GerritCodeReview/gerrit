// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.lucene;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.lucene.search.BooleanClause.Occur.MUST;
import static org.apache.lucene.search.BooleanClause.Occur.MUST_NOT;
import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

import com.google.common.collect.Lists;
import com.google.gerrit.index.FieldType;
import com.google.gerrit.index.Schema;
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
import java.util.Date;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRefBuilder;

public class QueryBuilder<V> {
  static Term stringTerm(String name, String value) {
    BytesRefBuilder builder = new BytesRefBuilder();
    builder.append(value.getBytes(UTF_8), 0, value.length());
    return new Term(name, builder.get());
  }

  static Query intPoint(String name, int value) {
    return IntPoint.newExactQuery(name, value);
  }

  private final Schema<V> schema;
  private final org.apache.lucene.util.QueryBuilder queryBuilder;

  public QueryBuilder(Schema<V> schema, Analyzer analyzer) {
    this.schema = schema;
    queryBuilder = new org.apache.lucene.util.QueryBuilder(analyzer);
  }

  public Query toQuery(Predicate<V> p) throws QueryParseException {
    if (p instanceof AndPredicate) {
      return and(p);
    } else if (p instanceof OrPredicate) {
      return or(p);
    } else if (p instanceof NotPredicate) {
      return not(p);
    } else if (p instanceof IndexPredicate) {
      return fieldQuery((IndexPredicate<V>) p);
    } else if (p instanceof PostFilterPredicate) {
      return new MatchAllDocsQuery();
    } else {
      throw new QueryParseException("cannot create query for index: " + p);
    }
  }

  private Query or(Predicate<V> p) throws QueryParseException {
    try {
      BooleanQuery.Builder q = new BooleanQuery.Builder();
      for (int i = 0; i < p.getChildCount(); i++) {
        q.add(toQuery(p.getChild(i)), SHOULD);
      }
      return q.build();
    } catch (BooleanQuery.TooManyClauses e) {
      throw new QueryParseException("cannot create query for index: " + p, e);
    }
  }

  private Query and(Predicate<V> p) throws QueryParseException {
    try {
      BooleanQuery.Builder b = new BooleanQuery.Builder();
      List<Query> not = Lists.newArrayListWithCapacity(p.getChildCount());
      for (int i = 0; i < p.getChildCount(); i++) {
        Predicate<V> c = p.getChild(i);
        if (c instanceof NotPredicate) {
          Predicate<V> n = c.getChild(0);
          if (n instanceof TimestampRangePredicate) {
            b.add(notTimestamp((TimestampRangePredicate<V>) n), MUST);
          } else {
            not.add(toQuery(n));
          }
        } else {
          b.add(toQuery(c), MUST);
        }
      }
      for (Query q : not) {
        b.add(q, MUST_NOT);
      }
      return b.build();
    } catch (BooleanQuery.TooManyClauses e) {
      throw new QueryParseException("cannot create query for index: " + p, e);
    }
  }

  private Query not(Predicate<V> p) throws QueryParseException {
    Predicate<V> n = p.getChild(0);
    if (n instanceof TimestampRangePredicate) {
      return notTimestamp((TimestampRangePredicate<V>) n);
    }

    // Lucene does not support negation, start with all and subtract.
    return new BooleanQuery.Builder()
        .add(new MatchAllDocsQuery(), MUST)
        .add(toQuery(n), MUST_NOT)
        .build();
  }

  private Query fieldQuery(IndexPredicate<V> p) throws QueryParseException {
    checkArgument(
        schema.hasField(p.getField()),
        "field not in schema v%s: %s",
        schema.getVersion(),
        p.getField().getName());
    FieldType<?> type = p.getType();
    if (type == FieldType.INTEGER) {
      return intQuery(p);
    } else if (type == FieldType.INTEGER_RANGE) {
      return intRangeQuery(p);
    } else if (type == FieldType.TIMESTAMP) {
      return timestampQuery(p);
    } else if (type == FieldType.EXACT) {
      return exactQuery(p);
    } else if (type == FieldType.PREFIX) {
      return prefixQuery(p);
    } else if (type == FieldType.FULL_TEXT) {
      return fullTextQuery(p);
    } else {
      throw FieldType.badFieldType(type);
    }
  }

  private Query intQuery(IndexPredicate<V> p) throws QueryParseException {
    int value;
    try {
      // Can't use IntPredicate because it and IndexPredicate are different
      // subclasses of OperatorPredicate.
      value = Integer.parseInt(p.getValue());
    } catch (NumberFormatException e) {
      throw new QueryParseException("not an integer: " + p.getValue());
    }
    return intPoint(p.getField().getName(), value);
  }

  private Query intRangeQuery(IndexPredicate<V> p) throws QueryParseException {
    if (p instanceof IntegerRangePredicate) {
      IntegerRangePredicate<V> r = (IntegerRangePredicate<V>) p;
      int minimum = r.getMinimumValue();
      int maximum = r.getMaximumValue();
      if (minimum == maximum) {
        // Just fall back to a standard integer query.
        return intPoint(p.getField().getName(), minimum);
      }
      return IntPoint.newRangeQuery(r.getField().getName(), minimum, maximum);
    }
    throw new QueryParseException("not an integer range: " + p);
  }

  private Query timestampQuery(IndexPredicate<V> p) throws QueryParseException {
    if (p instanceof TimestampRangePredicate) {
      TimestampRangePredicate<V> r = (TimestampRangePredicate<V>) p;
      return LongPoint.newRangeQuery(
          r.getField().getName(), r.getMinTimestamp().getTime(), r.getMaxTimestamp().getTime());
    }
    throw new QueryParseException("not a timestamp: " + p);
  }

  private Query notTimestamp(TimestampRangePredicate<V> r) throws QueryParseException {
    if (r.getMinTimestamp().getTime() == 0) {
      return LongPoint.newRangeQuery(
          r.getField().getName(), r.getMaxTimestamp().getTime(), Long.MAX_VALUE);
    }
    throw new QueryParseException("cannot negate: " + r);
  }

  private Query exactQuery(IndexPredicate<V> p) {
    if (p instanceof RegexPredicate<?>) {
      return regexQuery(p);
    }
    return new TermQuery(new Term(p.getField().getName(), p.getValue()));
  }

  private Query regexQuery(IndexPredicate<V> p) {
    String re = p.getValue();
    if (re.startsWith("^")) {
      re = re.substring(1);
    }
    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }
    return new RegexpQuery(new Term(p.getField().getName(), re));
  }

  private Query prefixQuery(IndexPredicate<V> p) {
    return new PrefixQuery(new Term(p.getField().getName(), p.getValue()));
  }

  private Query fullTextQuery(IndexPredicate<V> p) throws QueryParseException {
    String value = p.getValue();
    if (value == null) {
      throw new QueryParseException("Full-text search over empty string not supported");
    }
    Query query = queryBuilder.createPhraseQuery(p.getField().getName(), value);
    if (query == null) {
      throw new QueryParseException("Cannot create full-text query with value: " + value);
    }
    return query;
  }

  public int toIndexTimeInMinutes(Date ts) {
    return (int) (ts.getTime() / 60000);
  }
}
