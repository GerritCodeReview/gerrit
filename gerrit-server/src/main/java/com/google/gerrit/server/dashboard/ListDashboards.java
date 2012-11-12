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

package com.google.gerrit.server.dashboard;

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/** List projects visible to the calling user. */
public class ListDashboards {
  private static final Logger log = LoggerFactory.getLogger(ListDashboards.class);

  public static enum Level {
    PROJECT
  };

  private final CurrentUser currentUser;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.JSON;

  private Level level;
  private String entityName;

  @Inject
  protected ListDashboards(CurrentUser currentUser, ProjectCache projectCache,
      GitRepositoryManager repoManager) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
  }

  public OutputFormat getFormat() {
    return format;
  }

  public ListDashboards setFormat(OutputFormat fmt) {
    if (!format.isJson()) {
      throw new IllegalArgumentException(format.name() + " not supported");
    }
    this.format = fmt;
    return this;
  }

  public ListDashboards setLevel(Level level) {
    this.level = level;
    return this;
  }

  public ListDashboards setEntityName(String entityName) {
    this.entityName = entityName;
    return this;
  }

  public void display(OutputStream out) {
    final PrintWriter stdout;
    try {
      stdout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, "UTF-8")));
    } catch (UnsupportedEncodingException e) {
      // Our encoding is required by the specifications for the runtime.
      throw new RuntimeException("JVM lacks UTF-8 encoding", e);
    }

    try {
      if (level == null) {
        throw new IllegalStateException("no dashboard level");
      }

      Map<String, DashboardInfo> output;
      switch (level) {
        case PROJECT:
          output = projectDashboards(new Project.NameKey(entityName));
          break;
        default:
          throw new IllegalStateException("unsupported dashboard level: " + level);
      }

      format.newGson().toJson(output,
          new TypeToken<Map<String, DashboardInfo>>() {}.getType(), stdout);
      stdout.print('\n');
    } finally {
      stdout.flush();
    }
  }

  private Map<String, DashboardInfo> projectDashboards(final Project.NameKey projectName) {
    Map<String, DashboardInfo> output = Maps.newTreeMap();

    final ProjectState projectState = projectCache.get(projectName);
    if (projectState == null || !projectState.controlFor(currentUser).isVisible()) {
      return output;
    }

    DashboardInfo info = new DashboardInfo();
    info.name = "Test Dashboard";
    info.description = "Test Dashboard for " + projectName;
    info.query =
        "title=Gerrit+Dashboard&Open+Changes=project:gerrit+is:open+limit:15&Merged+Changes=project:gerrit+is:merged+limit:10&To+Review=reviewer:self+status:open";
    output.put(info.name, info);

    return output;
  }

  @SuppressWarnings("unused")
  private static class DashboardInfo {
    final String kind = "gerritcodereview#dashboard";
    String name;
    String description;
    String query;
  }
}
