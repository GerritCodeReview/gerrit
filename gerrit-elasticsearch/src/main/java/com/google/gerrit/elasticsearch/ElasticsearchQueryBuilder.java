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
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class ElasticsearchQueryBuilder extends QueryBuilder {
  public ElasticsearchQueryBuilder(Analyzer analyzer) {
    super(analyzer);
  }

  @Override
  protected Query fieldQuery(IndexPredicate<ChangeData> p)
      throws QueryParseException {
    FieldType<?> type = p.getType();
    String value = p.getValue();
    if (type == FieldType.INTEGER || type == FieldType.INTEGER_RANGE) {
      // QueryBuilder encodes integer fields as prefix coded bits,
      // which elasticsearch's queryString can't handle.
      // Create integer terms with string representations instead.
      return new TermQuery(new Term(p.getField().getName(), value));
    } else if (type == FieldType.EXACT) {
      // Exact strings must be quoted.
      return new TermQuery(
          new Term(p.getField().getName(), "\"" + value + "\""));
    } else {
      // For other terms, default to QueryBuilder's implementation.
      return super.fieldQuery(p);
    }
  }
}
