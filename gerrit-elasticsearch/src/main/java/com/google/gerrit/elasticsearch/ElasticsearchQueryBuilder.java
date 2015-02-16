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

import com.google.gerrit.lucene.QueryBuilder;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.RegexPredicate;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanQuery;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;

public class ElasticsearchQueryBuilder extends QueryBuilder {
  public ElasticsearchQueryBuilder(Analyzer analyzer) {
    super(analyzer);
  }

  protected BaseQueryBuilder toQueryBuilder(Predicate<ChangeData> p)
      throws QueryParseException {
    if (p instanceof AndPredicate) {
      return QueryBuilders.queryString(and(p).toString());
    } else if (p instanceof OrPredicate) {
      return esOr(p);
    } else if (p instanceof NotPredicate) {
      return QueryBuilders.queryString(not(p).toString());
    } else if (p instanceof IndexPredicate) {
      return esfieldQuery((IndexPredicate<ChangeData>) p);
    } else {
      throw new QueryParseException("cannot create query for index: " + p);
    }
  }

  private BaseQueryBuilder esfieldQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    FieldType<?> type = p.getType();
    FieldDef<?,?> field = p.getField();

    String name = p.getField().getName();
    String value = p.getValue();

    if (type == FieldType.INTEGER || type == FieldType.INTEGER_RANGE) {
      // QueryBuilder encodes integer fields as prefix coded bits,
      // which elasticsearch's queryString can't handle.
      // Create integer terms with string representations instead.
      return QueryBuilders.queryString(name + ":" + value);
//      return QueryBuilders.termQuery(name, value);
    } else if (type == FieldType.EXACT) {
      if (p instanceof RegexPredicate) {
        String re = p.getValue();
        if (re.startsWith("^")) {
          re = re.substring(1);
        }
        if (re.endsWith("$") && !re.endsWith("\\$")) {
          re = re.substring(0, re.length() - 1);
        }
        return QueryBuilders.regexpQuery(name + ".keyword", re);
      } else if (name.equals("topic2") && value.isEmpty()) {
          return QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
            FilterBuilders.missingFilter("topic2"));
      } else if (name.equals("file") || name.equals("ref")) {
        return QueryBuilders.termQuery(name + ".keyword", value);
      } else {
        return QueryBuilders.queryString(name + ": " + "\"" + value + "\"");
      }
    } else {
      // For other terms, default to QueryBuilder's implementation.
      return QueryBuilders.queryString(super.fieldQuery(p).toString());
    }
  }

  private BoolQueryBuilder esOr(Predicate<ChangeData> p)
      throws QueryParseException {
    try {
      BoolQueryBuilder q = QueryBuilders.boolQuery();
      for (int i = 0; i < p.getChildCount(); i++) {
        q.should(toQueryBuilder(p.getChild(i)));
      }
      return q;
    } catch (BooleanQuery.TooManyClauses e) {
      throw new QueryParseException("cannot create query for index: " + p, e);
    }
  }
}
