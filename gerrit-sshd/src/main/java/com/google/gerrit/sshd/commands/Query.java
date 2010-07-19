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
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.ChangeAttribute;
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
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

class Query extends BaseCommand {
  static enum OutputFormat {
    TEXT, JSON;
  }

  private final Gson gson = new Gson();

  private final SimpleDateFormat sdf =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");

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

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(name = "-n", aliases = {"--limit"}, metaVar = "CNT", usage = "Maximum number of results to output")
  private int limit = 2000;

  @Option(name = "--sort-key", metaVar = "KEY", usage = "sortKey to resume results at")
  private String sortKey = "z";

  @Option(name = "--current-patch-set", usage = "Include information about current patch set")
  private boolean includeCurrentPatchSet;

  @Option(name = "--patch-sets", usage = "Include information about all patch sets")
  private boolean includePatchSets;

  @Argument(index = 0, required = true, multiValued = true, metaVar = "QUERY", usage = "Query to execute")
  private List<String> query;

  @Override
  public void start(Environment env) {
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
            return b.getChange().getSortKey().compareTo(
                a.getChange().getSortKey());
          }
        });

        if (limit < results.size()) {
          results = results.subList(0, limit);
        }

        for (ChangeData d : results) {
          ChangeAttribute c = eventFactory.asChangeAttribute(d.getChange());
          eventFactory.extend(c, d.getChange());
          eventFactory.addTrackingIds(c, d.trackingIds(db));

          if (includePatchSets) {
            eventFactory.addPatchSets(c, d.patches(db));
          }

          if (includeCurrentPatchSet) {
            PatchSet current = d.currentPatchSet(db);
            if (current != null) {
              c.currentPatchSet = eventFactory.asPatchSetAttribute(current);
              eventFactory.addApprovals(c.currentPatchSet, //
                  d.approvalsFor(db, current.getId()));
            }
          }

          show(c);
        }

        stats.rowCount = results.size();
        stats.runTimeMilliseconds =
            System.currentTimeMillis() - stats.runTimeMilliseconds;
        show(stats);
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
      err.write(Constants
          .encodeASCII("warning: assuming 'status:open' in query\n"));
      err.flush();
      s = queryRewriter.rewrite(Predicate.and(queryBuilder.status_open(), q));
    }

    if (!(s instanceof ChangeDataSource)) {
      throw new UnloggedFailure(2, "fatal: unsupported query: " + s);
    }

    return s;
  }

  private void show(Object data) throws Failure {
    switch (format) {
      default:
      case TEXT:
        if (data instanceof ChangeAttribute) {
          stdout.print("change ");
          stdout.print(((ChangeAttribute) data).id);
          stdout.print("\n");
          showText(data, 1);
        } else {
          showText(data, 0);
        }
        stdout.print('\n');
        break;

      case JSON:
        stdout.print(gson.toJson(data));
        stdout.print('\n');
        break;
    }
  }

  private void showText(Object data, int depth) throws Failure {
    for (Field f : fieldsOf(data.getClass())) {
      Object val;
      try {
        val = f.get(data);
      } catch (IllegalArgumentException err) {
        throw new Failure(4, "fatal: Cannot convert results to text", err);
      } catch (IllegalAccessException err) {
        throw new Failure(4, "fatal: Cannot convert results to text", err);
      }
      if (val == null) {
        continue;
      }

      indent(depth);
      stdout.print(f.getName());
      stdout.print(":");

      if (val instanceof Long && isDateField(f.getName())) {
        stdout.print(' ');
        stdout.print(sdf.format(new Date(((Long) val) * 1000L)));
        stdout.print('\n');
      } else {
        showTextValue(val, depth);
      }
    }
  }

  private void indent(int depth) {
    for (int i = 0; i < depth; i++) {
      stdout.print("  ");
    }
  }

  @SuppressWarnings( {"cast", "unchecked"})
  private void showTextValue(Object value, int depth) throws Failure {
    if (isPrimitive(value)) {
      stdout.print(' ');
      stdout.print(value);
      stdout.print('\n');

    } else if (value instanceof Collection) {
      stdout.print('\n');
      for (Object thing : ((Collection) value)) {
        if (isPrimitive(thing)) {
          stdout.print(' ');
          stdout.print(value);
          stdout.print('\n');
        } else {
          showText(thing, depth + 1);
          stdout.print('\n');
        }
      }
    } else {
      stdout.print('\n');
      showText(value, depth + 1);
    }
  }

  @SuppressWarnings("unchecked")
  private static boolean isPrimitive(Object value) {
    return value instanceof String //
        || value instanceof Number //
        || value instanceof Boolean //
        || value instanceof Enum;
  }

  private static boolean isDateField(String name) {
    return "lastUpdated".equals(name) //
        || "grantedOn".equals(name);
  }

  private List<Field> fieldsOf(Class<?> type) {
    List<Field> r = new ArrayList<Field>();
    if (type.getSuperclass() != null) {
      r.addAll(fieldsOf(type.getSuperclass()));
    }
    r.addAll(Arrays.asList(type.getDeclaredFields()));
    return r;
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
