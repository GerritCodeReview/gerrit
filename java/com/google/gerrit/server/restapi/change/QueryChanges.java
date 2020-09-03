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

package com.google.gerrit.server.restapi.change;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryRequiresAuthException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import org.kohsuke.args4j.Option;

public class QueryChanges implements RestReadView<TopLevelResource>, DynamicOptions.BeanReceiver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeJson.Factory json;
  private final ChangeQueryBuilder qb;
  private final Provider<ChangeQueryProcessor> queryProcessorProvider;
  private final HashMap<String, DynamicOptions.DynamicBean> dynamicBeans = new HashMap<>();
  private EnumSet<ListChangesOption> options;
  private Integer limit;
  private Integer start;
  private Boolean noLimit;

  @Option(
      name = "--query",
      aliases = {"-q"},
      metaVar = "QUERY",
      usage = "Query string")
  private List<String> queries;

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "Maximum number of results to return")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(name = "-o", usage = "Output options per change")
  public void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListOption.fromBits(ListChangesOption.class, Integer.parseInt(hex, 16)));
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "Number of changes to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Option(name = "--no-limit", usage = "Return all results, overriding the default limit")
  public void setNoLimit(boolean on) {
    this.noLimit = on;
  }

  @Override
  public void setDynamicBean(String plugin, DynamicOptions.DynamicBean dynamicBean) {
    dynamicBeans.put(plugin, dynamicBean);
  }

  @Inject
  QueryChanges(
      ChangeJson.Factory json,
      ChangeQueryBuilder qb,
      Provider<ChangeQueryProcessor> queryProcessorProvider) {
    this.json = json;
    this.qb = qb;
    this.queryProcessorProvider = queryProcessorProvider;

    options = EnumSet.noneOf(ListChangesOption.class);
  }

  public void addQuery(String query) {
    if (queries == null) {
      queries = new ArrayList<>();
    }
    queries.add(query);
  }

  public String getQuery(int i) {
    return queries.get(i);
  }

  @Override
  public Response<List<?>> apply(TopLevelResource rsrc)
      throws BadRequestException, AuthException, PermissionBackendException {
    List<List<ChangeInfo>> out;
    try {
      out = query();
    } catch (QueryRequiresAuthException e) {
      throw new AuthException("Must be signed-in to use this operator", e);
    } catch (QueryParseException e) {
      logger.atFine().withCause(e).log("Reject change query with 400 Bad Request: %s", queries);
      throw new BadRequestException(e.getMessage(), e);
    }
    return Response.ok(out.size() == 1 ? out.get(0) : out);
  }

  private List<List<ChangeInfo>> query() throws QueryParseException, PermissionBackendException {
    ChangeQueryProcessor queryProcessor = queryProcessorProvider.get();
    if (queryProcessor.isDisabled()) {
      throw new QueryParseException("query disabled");
    }

    if (limit != null) {
      queryProcessor.setUserProvidedLimit(limit);
    }
    if (start != null) {
      queryProcessor.setStart(start);
    }
    if (noLimit != null) {
      queryProcessor.setNoLimit(noLimit);
    }
    dynamicBeans.forEach((p, b) -> queryProcessor.setDynamicBean(p, b));

    if (queries == null || queries.isEmpty()) {
      queries = Collections.singletonList("status:open");
    } else if (queries.size() > 10) {
      // Hard-code a default maximum number of queries to prevent
      // users from submitting too much to the server in a single call.
      throw new QueryParseException("limit of 10 queries");
    }

    int cnt = queries.size();
    List<QueryResult<ChangeData>> results = queryProcessor.query(qb.parse(queries));
    List<List<ChangeInfo>> res =
        json.create(
                options, queryProcessor.getAttributesFactory(), queryProcessor.getInfosFactory())
            .format(results);
    for (int n = 0; n < cnt; n++) {
      List<ChangeInfo> info = res.get(n);
      if (results.get(n).more() && !info.isEmpty()) {
        Iterables.getLast(info)._moreChanges = true;
      }
    }
    return res;
  }
}
