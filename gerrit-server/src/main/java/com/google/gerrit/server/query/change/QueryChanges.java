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

package com.google.gerrit.server.query.change;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryChanges implements RestReadView<TopLevelResource> {
  private final ChangeJson.Factory json;
  private final ChangeQueryBuilder qb;
  private final QueryProcessor imp;
  private EnumSet<ListChangesOption> options;

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY", usage = "Query string")
  private List<String> queries;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT", usage = "Maximum number of results to return")
  public void setLimit(int limit) {
    imp.setLimit(limit);
  }

  @Option(name = "-o", usage = "Output options per change")
  public void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListChangesOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Option(name = "--start", aliases = {"-S"}, metaVar = "CNT", usage = "Number of changes to skip")
  public void setStart(int start) {
    imp.setStart(start);
  }

  @Inject
  QueryChanges(ChangeJson.Factory json,
      ChangeQueryBuilder qb,
      QueryProcessor qp) {
    this.json = json;
    this.qb = qb;
    this.imp = qp;

    options = EnumSet.noneOf(ListChangesOption.class);
  }

  public void addQuery(String query) {
    if (queries == null) {
      queries = Lists.newArrayList();
    }
    queries.add(query);
  }

  public String getQuery(int i) {
    return queries.get(i);
  }

  @Override
  public List<?> apply(TopLevelResource rsrc)
      throws BadRequestException, AuthException, OrmException {
    List<List<ChangeInfo>> out;
    try {
      out = query();
    } catch (QueryParseException e) {
      // This is a hack to detect an operator that requires authentication.
      Pattern p = Pattern.compile(
          "^Error in operator (.*:self|is:watched|is:owner|is:reviewer|has:.*)$");
      Matcher m = p.matcher(e.getMessage());
      if (m.matches()) {
        String op = m.group(1);
        throw new AuthException("Must be signed-in to use " + op);
      }
      throw new BadRequestException(e.getMessage());
    }
    return out.size() == 1 ? out.get(0) : out;
  }

  private List<List<ChangeInfo>> query()
      throws OrmException, QueryParseException {
    if (imp.isDisabled()) {
      throw new QueryParseException("query disabled");
    }
    if (queries == null || queries.isEmpty()) {
      queries = Collections.singletonList("status:open");
    } else if (queries.size() > 10) {
      // Hard-code a default maximum number of queries to prevent
      // users from submitting too much to the server in a single call.
      throw new QueryParseException("limit of 10 queries");
    }

    return query0();
  }

  private List<List<ChangeInfo>> query0() throws OrmException,
      QueryParseException {
    int cnt = queries.size();
    List<QueryResult> results = imp.queryChanges(qb.parse(queries));
    List<List<ChangeInfo>> res = json.create(options)
        .formatQueryResults(results);
    for (int n = 0; n < cnt; n++) {
      List<ChangeInfo> info = res.get(n);
      if (results.get(n).moreChanges()) {
        info.get(info.size() - 1)._moreChanges = true;
      }
    }
    return res;
  }
}
