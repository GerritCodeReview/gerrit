// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.index;

import com.google.gerrit.reviewdb.Change;
import com.google.gson.Gson;
import com.google.gwtjsonrpc.server.JsonServlet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.ChainedFilter;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredDocIdSet;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;

import java.io.IOException;

abstract class ChangeFilter extends Filter {
  private static final Gson gson = JsonServlet.defaultGsonBuilder().create();

  static ChangeFilter chain(final Filter[] chain) {
    final ChainedFilter src = new ChainedFilter(chain, ChainedFilter.AND);
    return new ChangeFilter() {
      @Override
      boolean match(Change c) {
        for (Filter f : chain) {
          if (((ChangeFilter) f).match(c)) {
            continue;
          } else {
            return false;
          }
        }
        return true;
      }

      @Override
      public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        if (query != null) {
          return getDocIdSet(query.getDocIdSet(reader), reader);
        }else {
          return src.getDocIdSet(reader);
        }
      }
    };
  }

  protected QueryWrapperFilter query;

  void setQuery(Query query) {
    this.query = new QueryWrapperFilter(query);
  }

  abstract boolean match(Change c);

  @Override
  public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
    if (query != null) {
      return getDocIdSet(query.getDocIdSet(reader), reader);
    } else {
      return getDocIdSet(
          new QueryWrapperFilter(new MatchAllDocsQuery()).getDocIdSet(reader),
          reader);
    }
  }

  protected DocIdSet getDocIdSet(DocIdSet base, IndexReader reader) {
    return new MyIdSet(base, reader);
  }

  private class MyIdSet extends FilteredDocIdSet {
    private final IndexReader reader;

    MyIdSet(DocIdSet base, IndexReader reader) {
      super(base);
      this.reader = reader;
    }

    @Override
    protected boolean match(int docid) throws IOException {
      Document doc = reader.document(docid);
      Change c = gson.fromJson(doc.get("json"), Change.class);
      return ChangeFilter.this.match(c);
    }
  }
}
