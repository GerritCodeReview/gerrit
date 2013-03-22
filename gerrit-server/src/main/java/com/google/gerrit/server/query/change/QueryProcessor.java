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

package com.google.gerrit.server.query.change;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.ChangeAttribute;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.events.PatchSetAttribute;
import com.google.gerrit.server.events.QueryStats;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

public class QueryProcessor {
  private static final Logger log =
      LoggerFactory.getLogger(QueryProcessor.class);

  private final Comparator<ChangeData> cmpAfter =
      new Comparator<ChangeData>() {
        @Override
        public int compare(ChangeData a, ChangeData b) {
          try {
            return a.change(db).getSortKey().compareTo(b.change(db).getSortKey());
          } catch (OrmException e) {
            return 0;
          }
        }
      };

  private final Comparator<ChangeData> cmpBefore =
      new Comparator<ChangeData>() {
        @Override
        public int compare(ChangeData a, ChangeData b) {
          try {
            return b.change(db).getSortKey().compareTo(a.change(db).getSortKey());
          } catch (OrmException e) {
            return 0;
          }
        }
      };

  public static enum OutputFormat {
    TEXT, JSON;
  }

  private final Gson gson = new Gson();
  private final SimpleDateFormat sdf =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");

  private final EventFactory eventFactory;
  private final ChangeQueryBuilder queryBuilder;
  private final ChangeQueryRewriter queryRewriter;
  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final ChangeControl.Factory changeControlFactory;
  private final int maxLimit;

  private OutputFormat outputFormat = OutputFormat.TEXT;
  private int limit;
  private String sortkeyAfter;
  private String sortkeyBefore;
  private boolean includePatchSets;
  private boolean includeCurrentPatchSet;
  private boolean includeApprovals;
  private boolean includeComments;
  private boolean includeFiles;
  private boolean includeCommitMessage;
  private boolean includeDependencies;
  private boolean includeSubmitRecords;

  private OutputStream outputStream = DisabledOutputStream.INSTANCE;
  private PrintWriter out;
  private boolean moreResults;

  @Inject
  QueryProcessor(EventFactory eventFactory,
      ChangeQueryBuilder.Factory queryBuilder, CurrentUser currentUser,
      ChangeQueryRewriter queryRewriter, Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      ChangeControl.Factory changeControlFactory) {
    this.eventFactory = eventFactory;
    this.queryBuilder = queryBuilder.create(currentUser);
    this.queryRewriter = queryRewriter;
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.maxLimit = currentUser.getCapabilities()
      .getRange(GlobalCapability.QUERY_LIMIT)
      .getMax();
    this.moreResults = false;
  }

  int getLimit() {
    return limit;
  }

  void setLimit(int n) {
    limit = n;
  }

  void setSortkeyAfter(String sortkey) {
    sortkeyAfter = sortkey;
  }

  void setSortkeyBefore(String sortkey) {
    sortkeyBefore = sortkey;
  }

  public void setIncludePatchSets(boolean on) {
    includePatchSets = on;
  }

  public boolean getIncludePatchSets() {
    return includePatchSets;
  }

  public void setIncludeCurrentPatchSet(boolean on) {
    includeCurrentPatchSet = on;
  }

  public boolean getIncludeCurrentPatchSet() {
    return includeCurrentPatchSet;
  }

  public void setIncludeApprovals(boolean on) {
    includeApprovals = on;
  }

  public void setIncludeComments(boolean on) {
    includeComments = on;
  }

  public void setIncludeFiles(boolean on) {
    includeFiles = on;
  }

  public boolean getIncludeFiles() {
    return includeFiles;
  }

  public void setIncludeDependencies(boolean on) {
    includeDependencies = on;
  }

  public boolean getIncludeDependencies() {
    return includeDependencies;
  }

  public void setIncludeCommitMessage(boolean on) {
    includeCommitMessage = on;
  }

  public void setIncludeSubmitRecords(boolean on) {
    includeSubmitRecords = on;
  }

  public void setOutput(OutputStream out, OutputFormat fmt) {
    this.outputStream = out;
    this.outputFormat = fmt;
  }

  /**
   * Query for changes that match the query string.
   * <p>
   * If a limit was specified using {@link #setLimit(int)} this method may
   * return up to {@code limit + 1} results, allowing the caller to determine if
   * there are more than {@code limit} matches and suggest to its own caller
   * that the query could be retried with {@link #setSortkeyBefore(String)}.
   */
  public List<ChangeData> queryChanges(final String queryString)
      throws OrmException, QueryParseException {
    final Predicate<ChangeData> visibleToMe = queryBuilder.is_visible();
    Predicate<ChangeData> s = compileQuery(queryString, visibleToMe);
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

    Collections.sort(results, sortkeyAfter != null ? cmpAfter : cmpBefore);
    int limit = limit(s);
    if (results.size() > maxLimit) {
      moreResults = true;
    }
    if (limit < results.size()) {
      results = results.subList(0, limit);
    }
    if (sortkeyAfter != null) {
      Collections.reverse(results);
    }
    return results;
  }

  public void query(String queryString) throws IOException {
    out = new PrintWriter( //
        new BufferedWriter( //
            new OutputStreamWriter(outputStream, "UTF-8")));
    try {
      if (isDisabled()) {
        ErrorMessage m = new ErrorMessage();
        m.message = "query disabled";
        show(m);
        return;
      }

      try {
        final QueryStats stats = new QueryStats();
        stats.runTimeMilliseconds = System.currentTimeMillis();

        List<ChangeData> results = queryChanges(queryString);
        ChangeAttribute c = null;
        for (ChangeData d : results) {
          LabelTypes labelTypes = changeControlFactory.controlFor(d.getChange())
              .getLabelTypes();
          c = eventFactory.asChangeAttribute(d.getChange());
          eventFactory.extend(c, d.getChange());
          eventFactory.addTrackingIds(c, d.trackingIds(db));

          if (includeSubmitRecords) {
            PatchSet.Id psId = d.getChange().currentPatchSetId();
            PatchSet patchSet = db.get().patchSets().get(psId);
            List<SubmitRecord> submitResult = d.changeControl().canSubmit( //
                db.get(), patchSet, null, false, true, true);
            eventFactory.addSubmitRecords(c, submitResult);
          }

          if (includeCommitMessage) {
            eventFactory.addCommitMessage(c, d.commitMessage(repoManager, db));
          }

          if (includePatchSets) {
            if (includeFiles) {
              eventFactory.addPatchSets(c, d.patches(db),
                includeApprovals ? d.approvalsMap(db).asMap() : null,
                includeFiles, d.change(db), labelTypes);
            } else {
              eventFactory.addPatchSets(c, d.patches(db),
                  includeApprovals ? d.approvalsMap(db).asMap() : null,
                  labelTypes);
            }
          }

          if (includeCurrentPatchSet) {
            PatchSet current = d.currentPatchSet(db);
            if (current != null) {
              c.currentPatchSet = eventFactory.asPatchSetAttribute(current);
              eventFactory.addApprovals(c.currentPatchSet,
                  d.currentApprovals(db), labelTypes);

              if (includeFiles) {
                eventFactory.addPatchSetFileNames(c.currentPatchSet,
                    d.change(db), d.currentPatchSet(db));
              }
            }
          }

          if (includeComments) {
            eventFactory.addComments(c, d.messages(db));
            if (includePatchSets) {
              for (PatchSetAttribute attribute : c.patchSets) {
                eventFactory.addPatchSetComments(attribute,  d.comments(db));
              }
            }
          }

          if (includeDependencies) {
            eventFactory.addDependencies(c, d.getChange());
          }

          show(c);
        }

        stats.rowCount = results.size();
        if (moreResults) {
          stats.resumeSortKey = c.sortKey;
        }
        stats.runTimeMilliseconds =
            System.currentTimeMillis() - stats.runTimeMilliseconds;
        show(stats);
      } catch (OrmException err) {
        log.error("Cannot execute query: " + queryString, err);

        ErrorMessage m = new ErrorMessage();
        m.message = "cannot query database";
        show(m);

      } catch (QueryParseException e) {
        ErrorMessage m = new ErrorMessage();
        m.message = e.getMessage();
        show(m);
      } catch (NoSuchChangeException e) {
        log.error("Missing change: " + e.getMessage(), e);
        ErrorMessage m = new ErrorMessage();
        m.message = "missing change " + e.getMessage();
        show(m);
      }
    } finally {
      try {
        out.flush();
      } finally {
        out = null;
      }
    }
  }

  boolean isDisabled() {
    return maxLimit <= 0;
  }

  private int limit(Predicate<ChangeData> s) {
    int n = queryBuilder.hasLimit(s) ? queryBuilder.getLimit(s) : maxLimit;
    return limit > 0 ? Math.min(n, limit) + 1 : n + 1;
  }

  @SuppressWarnings("unchecked")
  private Predicate<ChangeData> compileQuery(String queryString,
      final Predicate<ChangeData> visibleToMe) throws QueryParseException {

    Predicate<ChangeData> q = queryBuilder.parse(queryString);
    if (!queryBuilder.hasSortKey(q)) {
      if (sortkeyBefore != null) {
        q = Predicate.and(q, queryBuilder.sortkey_before(sortkeyBefore));
      } else if (sortkeyAfter != null) {
        q = Predicate.and(q, queryBuilder.sortkey_after(sortkeyAfter));
      } else {
        q = Predicate.and(q, queryBuilder.sortkey_before("z"));
      }
    }
    q = Predicate.and(q,
        queryBuilder.limit(limit > 0 ? Math.min(limit, maxLimit) + 1 : maxLimit),
        visibleToMe);

    Predicate<ChangeData> s = queryRewriter.rewrite(q);
    if (!(s instanceof ChangeDataSource)) {
      s = queryRewriter.rewrite(Predicate.and(queryBuilder.status_open(), q));
    }

    if (!(s instanceof ChangeDataSource)) {
      throw new QueryParseException("invalid query: " + s);
    }

    return s;
  }

  private void show(Object data) {
    switch (outputFormat) {
      default:
      case TEXT:
        if (data instanceof ChangeAttribute) {
          out.print("change ");
          out.print(((ChangeAttribute) data).id);
          out.print("\n");
          showText(data, 1);
        } else {
          showText(data, 0);
        }
        out.print('\n');
        break;

      case JSON:
        out.print(gson.toJson(data));
        out.print('\n');
        break;
    }
  }

  private void showText(Object data, int depth) {
    for (Field f : fieldsOf(data.getClass())) {
      Object val;
      try {
        val = f.get(data);
      } catch (IllegalArgumentException err) {
        continue;
      } catch (IllegalAccessException err) {
        continue;
      }
      if (val == null) {
        continue;
      }

      showField(f.getName(), val, depth);
    }
  }

  private String indent(int spaces) {
    if (spaces == 0) {
      return "";
    } else {
      return String.format("%" + spaces + "s", " ");
    }
  }

  private void showField(String field, Object value, int depth) {
    final int spacesDepthRatio = 2;
    String indent = indent(depth * spacesDepthRatio);
    out.print(indent);
    out.print(field);
    out.print(':');
    if (value instanceof String && ((String) value).contains("\n")) {
      out.print(' ');
      // Idention for multi-line text is
      // current depth indetion + length of field + length of ": "
      indent = indent(indent.length() + field.length() + spacesDepthRatio);
      out.print(((String) value).replaceAll("\n", "\n" + indent).trim());
      out.print('\n');
    } else if (value instanceof Long && isDateField(field)) {
      out.print(' ');
      out.print(sdf.format(new Date(((Long) value) * 1000L)));
      out.print('\n');
    } else if (isPrimitive(value)) {
      out.print(' ');
      out.print(value);
      out.print('\n');
    } else if (value instanceof Collection) {
      out.print('\n');
      boolean firstElement = true;
      for (Object thing : ((Collection<?>) value)) {
        // The name of the collection was initially printed at the beginning
        // of this routine.  Beginning at the second sub-element, reprint
        // the collection name so humans can separate individual elements
        // with less strain and error.
        //
        if (firstElement) {
          firstElement = false;
        } else {
          out.print(indent);
          out.print(field);
          out.print(":\n");
        }
        if (isPrimitive(thing)) {
          out.print(' ');
          out.print(value);
          out.print('\n');
        } else {
          showText(thing, depth + 1);
        }
      }
    } else {
      out.print('\n');
      showText(value, depth + 1);
    }
  }

  private static boolean isPrimitive(Object value) {
    return value instanceof String //
        || value instanceof Number //
        || value instanceof Boolean //
        || value instanceof Enum;
  }

  private static boolean isDateField(String name) {
    return "lastUpdated".equals(name) //
        || "grantedOn".equals(name) //
        || "timestamp".equals(name) //
        || "createdOn".equals(name);
  }

  private List<Field> fieldsOf(Class<?> type) {
    List<Field> r = new ArrayList<Field>();
    if (type.getSuperclass() != null) {
      r.addAll(fieldsOf(type.getSuperclass()));
    }
    r.addAll(Arrays.asList(type.getDeclaredFields()));
    return r;
  }

  static class ErrorMessage {
    public final String type = "error";
    public String message;
  }
}
