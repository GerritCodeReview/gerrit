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

import static org.apache.lucene.search.BooleanClause.Occur.MUST;
import static org.apache.lucene.search.BooleanClause.Occur.MUST_NOT;
import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

import com.google.common.collect.Lists;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.RegexPredicate;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.TimestampRangePredicate;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SortKeyPredicate;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

import java.sql.Timestamp;
import java.util.List;

public class QueryBuilder {
  private static final String ID_FIELD = ChangeField.LEGACY_ID.getName();

  public static Term idTerm(ChangeData cd) {
    return intTerm(ID_FIELD, cd.getId().get());
  }

  public static Query toQuery(Schema<ChangeData> schema, Predicate<ChangeData> p)
      throws QueryParseException {
    if (p instanceof AndPredicate) {
      return and(schema, p);
    } else if (p instanceof OrPredicate) {
      return or(schema, p);
    } else if (p instanceof NotPredicate) {
      return not(schema, p);
    } else if (p instanceof IndexPredicate) {
      return fieldQuery(schema, (IndexPredicate<ChangeData>) p);
    } else {
      throw new QueryParseException("cannot create query for index: " + p);
    }
  }

  private static Query or(Schema<ChangeData> schema, Predicate<ChangeData> p)
      throws QueryParseException {
    try {
      BooleanQuery q = new BooleanQuery();
      for (int i = 0; i < p.getChildCount(); i++) {
        q.add(toQuery(schema, p.getChild(i)), SHOULD);
      }
      return q;
    } catch (BooleanQuery.TooManyClauses e) {
      throw new QueryParseException("cannot create query for index: " + p, e);
    }
  }

  private static Query and(Schema<ChangeData> schema, Predicate<ChangeData> p)
      throws QueryParseException {
    try {
      BooleanQuery b = new BooleanQuery();
      List<Query> not = Lists.newArrayListWithCapacity(p.getChildCount());
      for (int i = 0; i < p.getChildCount(); i++) {
        Predicate<ChangeData> c = p.getChild(i);
        if (c instanceof NotPredicate) {
          Predicate<ChangeData> n = c.getChild(0);
          if (n instanceof TimestampRangePredicate) {
            b.add(notTimestamp((TimestampRangePredicate<ChangeData>) n), MUST);
          } else {
            not.add(toQuery(schema, n));
          }
        } else {
          b.add(toQuery(schema, c), MUST);
        }
      }
      for (Query q : not) {
        b.add(q, MUST_NOT);
      }
      return b;
    } catch (BooleanQuery.TooManyClauses e) {
      throw new QueryParseException("cannot create query for index: " + p, e);
    }
  }

  private static Query not(Schema<ChangeData> schema, Predicate<ChangeData> p)
      throws QueryParseException {
    Predicate<ChangeData> n = p.getChild(0);
    if (n instanceof TimestampRangePredicate) {
      return notTimestamp((TimestampRangePredicate<ChangeData>) n);
    }

    // Lucene does not support negation, start with all and subtract.
    BooleanQuery q = new BooleanQuery();
    q.add(new MatchAllDocsQuery(), MUST);
    q.add(toQuery(schema, n), MUST_NOT);
    return q;
  }

  private static Query fieldQuery(Schema<ChangeData> schema,
      IndexPredicate<ChangeData> p) throws QueryParseException {
    if (p.getType() == FieldType.INTEGER) {
      return intQuery(p);
    } else if (p.getType() == FieldType.TIMESTAMP) {
      return timestampQuery(p);
    } else if (p.getType() == FieldType.EXACT) {
      return exactQuery(p);
    } else if (p.getType() == FieldType.PREFIX) {
      return prefixQuery(p);
    } else if (p.getType() == FieldType.FULL_TEXT) {
      return fullTextQuery(p);
    } else if (p instanceof SortKeyPredicate) {
      return sortKeyQuery(schema, (SortKeyPredicate) p);
    } else {
      throw badFieldType(p.getType());
    }
  }

  private static Term intTerm(String name, int value) {
    BytesRef bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
    NumericUtils.intToPrefixCodedBytes(value, 0, bytes);
    return new Term(name, bytes);
  }

  private static Query intQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    int value;
    try {
      // Can't use IntPredicate because it and IndexPredicate are different
      // subclasses of OperatorPredicate.
      value = Integer.valueOf(p.getValue());
    } catch (IllegalArgumentException e) {
      throw new QueryParseException("not an integer: " + p.getValue());
    }
    return new TermQuery(intTerm(p.getField().getName(), value));
  }

  private static Query sortKeyQuery(Schema<ChangeData> schema, SortKeyPredicate p) {
    long min = p.getMinValue(schema);
    long max = p.getMaxValue(schema);
    return NumericRangeQuery.newLongRange(
        p.getField().getName(),
        min != Long.MIN_VALUE ? min : null,
        max != Long.MAX_VALUE ? max : null,
        false, false);
  }

  private static Query timestampQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    if (p instanceof TimestampRangePredicate) {
      TimestampRangePredicate<ChangeData> r =
          (TimestampRangePredicate<ChangeData>) p;
      return NumericRangeQuery.newIntRange(
          r.getField().getName(),
          toIndexTime(r.getMinTimestamp()),
          toIndexTime(r.getMaxTimestamp()),
          true, true);
    }
    throw new QueryParseException("not a timestamp: " + p);
  }

  private static Query notTimestamp(TimestampRangePredicate<ChangeData> r)
      throws QueryParseException {
    if (r.getMinTimestamp().getTime() == 0) {
      return NumericRangeQuery.newIntRange(
          r.getField().getName(),
          toIndexTime(r.getMaxTimestamp()),
          null,
          true, true);
    }
    throw new QueryParseException("cannot negate: " + r);
  }

  private static Query exactQuery(IndexPredicate<ChangeData> p) {
    if (p instanceof RegexPredicate<?>) {
      return regexQuery(p);
    } else {
      return new TermQuery(new Term(p.getField().getName(), p.getValue()));
    }
  }

  private static Query regexQuery(IndexPredicate<ChangeData> p) {
    String re = p.getValue();
    if (re.startsWith("^")) {
      re = re.substring(1);
    }
    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }
    return new RegexpQuery(new Term(p.getField().getName(), re));
  }

  private static Query prefixQuery(IndexPredicate<ChangeData> p) {
    return new PrefixQuery(new Term(p.getField().getName(), p.getValue()));
  }

  private static Query fullTextQuery(IndexPredicate<ChangeData> p) {
    return new FuzzyQuery(new Term(p.getField().getName(), p.getValue()));
  }

  public static int toIndexTime(Timestamp ts) {
    return (int) (ts.getTime() / 60000);
  }

  public static IllegalArgumentException badFieldType(FieldType<?> t) {
    return new IllegalArgumentException("unknown index field type " + t);
  }

  private QueryBuilder() {
  }
}
