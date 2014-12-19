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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.data.QueryStatsAttribute;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;

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
import java.util.Date;
import java.util.List;

public class QueryProcessor {
  private static final Logger log =
      LoggerFactory.getLogger(QueryProcessor.class);

  public static enum OutputFormat {
    TEXT, JSON
  }

  private final Gson gson = new Gson();
  private final SimpleDateFormat sdf =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");

  private final EventFactory eventFactory;
  private final ChangeQueryBuilder queryBuilder;
  private final ChangeQueryRewriter queryRewriter;
  private final TrackingFooters trackingFooters;
  private final CurrentUser user;
  private final int permittedLimit;

  private OutputFormat outputFormat = OutputFormat.TEXT;
  private int limitFromCaller;
  private int start;
  private boolean includePatchSets;
  private boolean includeCurrentPatchSet;
  private boolean includeApprovals;
  private boolean includeComments;
  private boolean includeFiles;
  private boolean includeCommitMessage;
  private boolean includeDependencies;
  private boolean includeSubmitRecords;
  private boolean includeAllReviewers;

  private OutputStream outputStream = DisabledOutputStream.INSTANCE;
  private PrintWriter out;

  @Inject
  QueryProcessor(EventFactory eventFactory,
      ChangeQueryBuilder.Factory queryBuilder, CurrentUser currentUser,
      ChangeQueryRewriter queryRewriter,
      TrackingFooters trackingFooters) {
    this.eventFactory = eventFactory;
    this.queryBuilder = queryBuilder.create(currentUser);
    this.queryRewriter = queryRewriter;
    this.trackingFooters = trackingFooters;
    this.user = currentUser;
    this.permittedLimit = currentUser.getCapabilities()
      .getRange(GlobalCapability.QUERY_LIMIT)
      .getMax();
  }

  void setLimit(int n) {
    limitFromCaller = n;
  }

  public void setStart(int n) {
    start = n;
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

  public void setIncludeAllReviewers(boolean on) {
    includeAllReviewers = on;
  }

  public void setOutput(OutputStream out, OutputFormat fmt) {
    this.outputStream = out;
    this.outputFormat = fmt;
  }

  /**
   * Query for changes that match the query string.
   *
   * @see #queryChanges(List)
   * @param queryString the query string to parse.
   * @return results of the query.
   */
  public QueryResult queryByString(String queryString)
      throws OrmException, QueryParseException {
    return queryByStrings(ImmutableList.of(queryString)).get(0);
  }

  /**
   * Perform multiple queries over a list of query strings.
   *
   * @see #queryChanges(List)
   * @param queryStrings the query strings to parse.
   * @return results of the queries, one list per input query.
   */
  public List<QueryResult> queryByStrings(List<String> queryStrings)
      throws OrmException, QueryParseException {
    List<Predicate<ChangeData>> queries = new ArrayList<>(queryStrings.size());
    for (String qs : queryStrings) {
      queries.add(queryBuilder.parse(qs));
    }
    return queryChanges(queries);
  }

  /**
   * Query for changes that match a structured query.
   *
   * @see #queryChanges(List)
   * @param query the query.
   * @return results of the query.
   */
  public QueryResult queryChanges(Predicate<ChangeData> query)
      throws OrmException, QueryParseException {
    return queryChanges(ImmutableList.of(query)).get(0);
  }

  /*
   * Perform multiple queries over a list of query strings.
   * <p>
   * If a limit was specified using {@link #setLimit(int)} this method may
   * return up to {@code limit + 1} results, allowing the caller to determine if
   * there are more than {@code limit} matches and suggest to its own caller
   * that the query could be retried with {@link #setStart(int)}.
   *
   * @param queries the queries.
   * @return results of the queries, one list per input query.
   */
  public List<QueryResult> queryChanges(List<Predicate<ChangeData>> queries)
      throws OrmException, QueryParseException {
    return queryChanges(null, queries);
  }

  private List<QueryResult> queryChanges(List<String> queryStrings,
      List<Predicate<ChangeData>> queries)
      throws OrmException, QueryParseException {
    Predicate<ChangeData> visibleToMe = queryBuilder.is_visible();
    int cnt = queries.size();

    // Parse and rewrite all queries.
    List<Integer> limits = new ArrayList<>(cnt);
    List<Predicate<ChangeData>> predicates = new ArrayList<>(cnt);
    List<ChangeDataSource> sources = new ArrayList<>(cnt);
    for (Predicate<ChangeData> q : queries) {
      q = Predicate.and(q, visibleToMe);
      int limit = getEffectiveLimit(q);
      limits.add(limit);

      // Always bump limit by 1, even if this results in exceeding the permitted
      // max for this user. The only way to see if there are more changes is to
      // ask for one more result from the query.
      Predicate<ChangeData> s = queryRewriter.rewrite(q, start, limit + 1);
      if (!(s instanceof ChangeDataSource)) {
        q = Predicate.and(queryBuilder.status_open(), q);
        s = queryRewriter.rewrite(q, start, limit);
      }
      if (!(s instanceof ChangeDataSource)) {
        throw new QueryParseException("invalid query: " + s);
      }
      predicates.add(s);

      // Don't trust QueryRewriter to have left the visible predicate.
      // TODO(dborowitz): Probably we can.
      AndSource a = new AndSource(ImmutableList.of(s, visibleToMe), start);
      sources.add(a);
    }

    // Run each query asynchronously, if supported.
    List<ResultSet<ChangeData>> matches = new ArrayList<>(cnt);
    for (ChangeDataSource s : sources) {
      matches.add(s.read());
    }

    List<QueryResult> out = new ArrayList<>(cnt);
    for (int i = 0; i < cnt; i++) {
      out.add(QueryResult.create(
          queryStrings != null ? queryStrings.get(i) : null,
          predicates.get(i),
          limits.get(i),
          matches.get(i).toList()));
    }
    return out;
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
        final QueryStatsAttribute stats = new QueryStatsAttribute();
        stats.runTimeMilliseconds = TimeUtil.nowMs();

        QueryResult results = queryByString(queryString);
        ChangeAttribute c = null;
        for (ChangeData d : results.changes()) {
          ChangeControl cc = d.changeControl().forUser(user);

          LabelTypes labelTypes = cc.getLabelTypes();
          c = eventFactory.asChangeAttribute(d.change());
          eventFactory.extend(c, d.change());

          if (!trackingFooters.isEmpty()) {
            eventFactory.addTrackingIds(c,
                trackingFooters.extract(d.commitFooters()));
          }

          if (includeAllReviewers) {
            eventFactory.addAllReviewers(c, d.notes());
          }

          if (includeSubmitRecords) {
            eventFactory.addSubmitRecords(c, new SubmitRuleEvaluator(d)
                .setAllowClosed(true)
                .setAllowDraft(true)
                .canSubmit());
          }

          if (includeCommitMessage) {
            eventFactory.addCommitMessage(c, d.commitMessage());
          }

          if (includePatchSets) {
            if (includeFiles) {
              eventFactory.addPatchSets(c, d.patches(),
                includeApprovals ? d.approvals().asMap() : null,
                includeFiles, d.change(), labelTypes);
            } else {
              eventFactory.addPatchSets(c, d.patches(),
                  includeApprovals ? d.approvals().asMap() : null,
                  labelTypes);
            }
          }

          if (includeCurrentPatchSet) {
            PatchSet current = d.currentPatchSet();
            if (current != null) {
              c.currentPatchSet = eventFactory.asPatchSetAttribute(current);
              eventFactory.addApprovals(c.currentPatchSet,
                  d.currentApprovals(), labelTypes);

              if (includeFiles) {
                eventFactory.addPatchSetFileNames(c.currentPatchSet,
                    d.change(), d.currentPatchSet());
              }
            }
          }

          if (includeComments) {
            eventFactory.addComments(c, d.messages());
            if (includePatchSets) {
              for (PatchSetAttribute attribute : c.patchSets) {
                eventFactory.addPatchSetComments(attribute,  d.publishedComments());
              }
            }
          }

          if (includeDependencies) {
            eventFactory.addDependencies(c, d.change());
          }

          show(c);
        }

        stats.rowCount = results.changes().size();
        if (results.moreChanges()) {
          stats.resumeSortKey = c.sortKey;
        }
        stats.runTimeMilliseconds =
            TimeUtil.nowMs() - stats.runTimeMilliseconds;
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
    return permittedLimit <= 0;
  }

  private int getEffectiveLimit(Predicate<ChangeData> p) {
    List<Integer> possibleLimits = new ArrayList<>(3);
    possibleLimits.add(permittedLimit);
    if (limitFromCaller > 0) {
      possibleLimits.add(limitFromCaller);
    }
    Integer limitFromPredicate = LimitPredicate.getLimit(p);
    if (limitFromPredicate != null) {
      possibleLimits.add(limitFromPredicate);
    }
    return Ordering.natural().min(possibleLimits);
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
    List<Field> r = new ArrayList<>();
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
