// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.query.doc;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Option;

import java.util.List;

public class QueryDocs implements RestReadView<TopLevelResource> {
  private final DocQueryProcessor imp;
  private boolean reverse;

  public static class DocResult {
    public String title;
    public String url;
    public String content;
  }

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY",
      multiValued = true, usage = "Query string")
  private List<String> queries;

  @Inject
  QueryDocs(DocQueryProcessor imp) {
    this.imp = imp;
  }

  public void addQuery(String query) {
    if (queries == null) {
      queries = Lists.newArrayList();
    }
    queries.add(query);
  }

  @Override
  public Object apply(TopLevelResource rsrc)
      throws OrmException, QueryParseException {
    if (imp.isDisabled()) {
      throw new QueryParseException("query disabled");
    }
    if (queries == null || queries.isEmpty()) {
      throw new QueryParseException("no query");
    }

    return imp.queryDocs(queries);
  }
}
