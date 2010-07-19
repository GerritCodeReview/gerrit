// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.events.QueryStats;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gson.Gson;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

class Query extends BaseCommand {
  private final Gson gson = new Gson();

  private PrintWriter stdout;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private EventFactory eventFactory;

  @Inject
  private ChangeQueryBuilder queryBuilder;

  @Inject
  private ChangeQueryRewriter queryRewriter;

  @Inject
  private Provider<ReviewDb> db;

  @Option(name = "-n", aliases = {"--limit"}, metaVar = "CNT", usage = "Maximum number of results to output")
  private int limit = 2000;

  @Option(name = "--sort-key", metaVar = "KEY", usage = "sortKey to resume results at")
  private String sortKey = "z";

  @Argument(index = 0, required = true, multiValued = true, metaVar = "QUERY", usage = "Query to execute")
  private List<String> query;

  @Override
  public void start(Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        if (limit <= 0)
          throw new UnloggedFailure(1, "fatal: --limit must be > 0");

        query();
      }
    });
  }

  private void query() throws Failure, IOException {
    stdout = toPrintWriter(out);
    try {
      try {
        final QueryStats stats = new QueryStats();
        stats.runTimeMilliseconds = System.currentTimeMillis();

        final Predicate<ChangeData> visibleToMe =
            queryBuilder.visibleto(currentUser);

        Predicate<ChangeData> s = compileQuery(visibleToMe);
        List<ChangeData> results = new ArrayList<ChangeData>();
        HashSet<Change.Id> want = new HashSet<Change.Id>();
        for (ChangeData d : ((ChangeDataSource) s).read()) {
          if (d.hasChange()) {
            // Checking visibleToMe here should be unnecessary, the
            // query should have already performed it. But we don't
            // want to trust the query rewriter that much yet.
            //
            if (visibleToMe.match(d)) {
              results.add(d);
            }
          } else {
            want.add(d.getId());
          }
        }

        if (!want.isEmpty()) {
          for (Change c : db.get().changes().get(want)) {
            ChangeData d = new ChangeData(c);
            if (visibleToMe.match(d)) {
              results.add(d);
            }
          }
        }

        Collections.sort(results, new Comparator<ChangeData>() {
          @Override
          public int compare(ChangeData a, ChangeData b) {
            return a.getChange().getSortKey().compareTo(
                b.getChange().getSortKey());
          }
        });

        if (limit < results.size()) {
          results = results.subList(0, limit);
        }
        for (ChangeData d : results) {
          stdout.print(gson.toJson(eventFactory
              .asChangeAttribute(d.getChange())));
          stdout.print('\n');
        }

        stats.rowCount = results.size();
        stats.runTimeMilliseconds =
            System.currentTimeMillis() - stats.runTimeMilliseconds;
        stdout.print(gson.toJson(stats) + '\n');
      } catch (OrmException e) {
        throw new Failure(3, "fatal: database query failed", e);
      } catch (QueryParseException e) {
        throw new UnloggedFailure(1, "fatal: " + e.getMessage());
      }
    } finally {
      stdout.flush();
    }
  }

  @SuppressWarnings("unchecked")
  private Predicate<ChangeData> compileQuery(
      final Predicate<ChangeData> visibleToMe) throws QueryParseException,
      IOException, UnloggedFailure {
    Predicate<ChangeData> q = Predicate.and( //
        queryBuilder.parse(join(query, " ")), //
        queryBuilder.sortkey_before(sortKey), //
        queryBuilder.limit(limit), //
        visibleToMe //
        );

    Predicate<ChangeData> s = queryRewriter.rewrite(q);
    if (!(s instanceof ChangeDataSource)) {
      err.write(Constants.encodeASCII("warning: assuming status:open\n"));
      err.flush();
      s = queryRewriter.rewrite(Predicate.and(queryBuilder.status_open(), q));
    }

    if (!(s instanceof ChangeDataSource)) {
      throw new UnloggedFailure(2, "fatal: unsupported query: " + s);
    }

    return s;
  }

  private static String join(List<String> list, String sep) {
    StringBuilder r = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        r.append(sep);
      }
      r.append(list.get(i));
    }
    return r.toString();
  }
}
