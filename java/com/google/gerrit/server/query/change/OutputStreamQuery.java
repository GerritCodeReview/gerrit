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

package com.google.gerrit.server.query.change;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.account.AccountAttributeLoader;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.data.QueryStatsAttribute;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Change query implementation that outputs to a stream in the style of an SSH command.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class OutputStreamQuery {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final DateTimeFormatter dtf =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz")
          .withLocale(Locale.US)
          .withZone(ZoneId.systemDefault());

  public enum OutputFormat {
    TEXT,
    JSON
  }

  public static final Gson GSON = new Gson();

  private final GitRepositoryManager repoManager;
  private final ChangeQueryBuilder queryBuilder;
  private final ChangeQueryProcessor queryProcessor;
  private final EventFactory eventFactory;
  private final TrackingFooters trackingFooters;
  private final SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory;
  private final AccountAttributeLoader.Factory accountAttributeLoaderFactory;

  private OutputFormat outputFormat = OutputFormat.TEXT;
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
  private ImmutableListMultimap<Change.Id, PluginDefinedInfo> pluginInfosByChange =
      ImmutableListMultimap.of();

  @Inject
  OutputStreamQuery(
      GitRepositoryManager repoManager,
      ChangeQueryBuilder queryBuilder,
      ChangeQueryProcessor queryProcessor,
      EventFactory eventFactory,
      TrackingFooters trackingFooters,
      SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory,
      AccountAttributeLoader.Factory accountAttributeLoaderFactory) {
    this.repoManager = repoManager;
    this.queryBuilder = queryBuilder;
    this.queryProcessor = queryProcessor;
    this.eventFactory = eventFactory;
    this.trackingFooters = trackingFooters;
    this.submitRuleEvaluatorFactory = submitRuleEvaluatorFactory;
    this.accountAttributeLoaderFactory = accountAttributeLoaderFactory;
  }

  void setLimit(int n) {
    queryProcessor.setUserProvidedLimit(n);
  }

  public void setNoLimit(boolean on) {
    queryProcessor.setNoLimit(on);
  }

  public void setStart(int n) {
    queryProcessor.setStart(n);
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

  public void setDynamicBean(String plugin, DynamicOptions.DynamicBean dynamicBean) {
    queryProcessor.setDynamicBean(plugin, dynamicBean);
  }

  public void query(String queryString) throws IOException {
    out =
        new PrintWriter( //
            new BufferedWriter( //
                new OutputStreamWriter(outputStream, UTF_8)));
    try {
      if (queryProcessor.isDisabled()) {
        ErrorMessage m = new ErrorMessage();
        m.message = "query disabled";
        show(m);
        return;
      }

      try {
        final QueryStatsAttribute stats = new QueryStatsAttribute();
        stats.runTimeMilliseconds = TimeUtil.nowMs();

        Map<Project.NameKey, Repository> repos = new HashMap<>();
        Map<Project.NameKey, RevWalk> revWalks = new HashMap<>();
        QueryResult<ChangeData> results = queryProcessor.query(queryBuilder.parse(queryString));
        pluginInfosByChange = queryProcessor.createPluginDefinedInfos(results.entities());
        try {
          AccountAttributeLoader accountLoader = accountAttributeLoaderFactory.create();
          List<ChangeAttribute> changeAttributes = new ArrayList<>();
          for (ChangeData d : results.entities()) {
            changeAttributes.add(buildChangeAttribute(d, repos, revWalks, accountLoader));
          }
          accountLoader.fill();
          changeAttributes.forEach(c -> show(c));
        } finally {
          closeAll(revWalks.values(), repos.values());
        }

        stats.rowCount = results.entities().size();
        stats.moreChanges = results.more();
        stats.runTimeMilliseconds = TimeUtil.nowMs() - stats.runTimeMilliseconds;
        show(stats);
      } catch (StorageException err) {
        logger.atSevere().withCause(err).log("Cannot execute query: %s", queryString);

        ErrorMessage m = new ErrorMessage();
        m.message = "cannot query database";
        show(m);

      } catch (QueryParseException e) {
        ErrorMessage m = new ErrorMessage();
        m.message = e.getMessage();
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

  private ChangeAttribute buildChangeAttribute(
      ChangeData d,
      Map<Project.NameKey, Repository> repos,
      Map<Project.NameKey, RevWalk> revWalks,
      AccountAttributeLoader accountLoader)
      throws IOException {
    LabelTypes labelTypes = d.getLabelTypes();
    ChangeAttribute c = eventFactory.asChangeAttribute(d.change(), accountLoader);
    c.hashtags = Lists.newArrayList(d.hashtags());
    eventFactory.extend(c, d.change());

    if (!trackingFooters.isEmpty()) {
      eventFactory.addTrackingIds(c, d.trackingFooters());
    }

    if (includeAllReviewers) {
      eventFactory.addAllReviewers(c, d.notes(), accountLoader);
    }

    if (includeSubmitRecords) {
      SubmitRuleOptions options =
          SubmitRuleOptions.builder().recomputeOnClosedChanges(true).build();
      eventFactory.addSubmitRecords(
          c, submitRuleEvaluatorFactory.create(options).evaluate(d), accountLoader);
    }

    if (includeCommitMessage) {
      eventFactory.addCommitMessage(c, d.commitMessage());
    }

    RevWalk rw = null;
    if (includePatchSets || includeCurrentPatchSet || includeDependencies) {
      Project.NameKey p = d.change().getProject();
      rw = revWalks.get(p);
      // Cache and reuse repos and revwalks.
      if (rw == null) {
        Repository repo = repoManager.openRepository(p);
        checkState(repos.put(p, repo) == null);
        rw = new RevWalk(repo);
        revWalks.put(p, rw);
      }
    }

    if (includePatchSets) {
      eventFactory.addPatchSets(
          rw,
          c,
          d.patchSets(),
          includeApprovals ? d.approvals().asMap() : null,
          includeFiles,
          d.change(),
          labelTypes,
          accountLoader);
    }

    if (includeCurrentPatchSet) {
      PatchSet current = d.currentPatchSet();
      if (current != null) {
        c.currentPatchSet = eventFactory.asPatchSetAttribute(rw, d.change(), current);
        eventFactory.addApprovals(
            c.currentPatchSet, d.currentApprovals(), labelTypes, accountLoader);

        if (includeFiles) {
          eventFactory.addPatchSetFileNames(c.currentPatchSet, d.change(), d.currentPatchSet());
        }
        if (includeComments) {
          eventFactory.addPatchSetComments(c.currentPatchSet, d.publishedComments(), accountLoader);
        }
      }
    }

    if (includeComments) {
      eventFactory.addComments(c, d.messages(), accountLoader);
      if (includePatchSets) {
        eventFactory.addPatchSets(
            rw,
            c,
            d.patchSets(),
            includeApprovals ? d.approvals().asMap() : null,
            includeFiles,
            d.change(),
            labelTypes,
            accountLoader);
        for (PatchSetAttribute attribute : c.patchSets) {
          eventFactory.addPatchSetComments(attribute, d.publishedComments(), accountLoader);
        }
      }
    }

    if (includeDependencies) {
      eventFactory.addDependencies(rw, c, d.change(), d.currentPatchSet());
    }

    List<PluginDefinedInfo> pluginInfos = pluginInfosByChange.get(d.getId());
    if (!pluginInfos.isEmpty()) {
      c.plugins = pluginInfos;
    }
    return c;
  }

  private static void closeAll(Iterable<RevWalk> revWalks, Iterable<Repository> repos) {
    if (repos != null) {
      for (Repository repo : repos) {
        repo.close();
      }
    }
    if (revWalks != null) {
      for (RevWalk revWalk : revWalks) {
        revWalk.close();
      }
    }
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
        out.print(GSON.toJson(data));
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
    }
    return String.format("%" + spaces + "s", " ");
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
      out.print(((String) value).replace("\n", "\n" + indent).trim());
      out.print('\n');
    } else if (value instanceof Long && isDateField(field)) {
      out.print(' ');
      out.print(dtf.format(Instant.ofEpochSecond((Long) value)));
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
