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
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** List projects visible to the calling user. */
public class ListDashboards {
  private static final Logger log = LoggerFactory.getLogger(ListDashboards.class);
  private static String REFS_DASHBOARDS = "refs/meta/dashboards/";

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
      final Map<String, DashboardInfo> output;
      if (level != null) {
        switch (level) {
          case PROJECT:
            output = allDashboardsFor(new Project.NameKey(entityName));
            break;
          default:
            throw new IllegalStateException("unsupported dashboard level: " + level);
        }
      } else {
        output = Maps.newTreeMap();
      }

      format.newGson().toJson(output,
          new TypeToken<Map<String, DashboardInfo>>() {}.getType(), stdout);
      stdout.print('\n');
    } finally {
      stdout.flush();
    }
  }

  private Map<String, DashboardInfo> allDashboardsFor(
      final Project.NameKey projectName) {
    ProjectState projectState = projectCache.get(projectName);
    Project.NameKey parent;

    Map<String, DashboardInfo> dashboards = Maps.newTreeMap();
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    seen.add(projectName);
    do {
      dashboards = addProjectDashboards(projectState, dashboards);

      parent = projectState.getProject().getParent();
      if (parent == null || !seen.add(parent)) {
        break;
      }

      projectState = projectCache.get(parent);
    } while (projectState  != null);

    projectState = projectCache.getAllProjects();
    parent = projectState.getProject().getNameKey();
    if (seen.add(parent)) {
      dashboards = addProjectDashboards(projectState, dashboards);
    }

    return dashboards;
  }

  private Map<String, DashboardInfo> addProjectDashboards(
      final ProjectState projectState, Map<String, DashboardInfo> all) {
    final Map<String, DashboardInfo> dashboards = projectDashboards(projectState);
    dashboards.putAll(all);
    return dashboards;
  }

  private Map<String, DashboardInfo> projectDashboards(final ProjectState projectState) {
    final Map<String, DashboardInfo> output = Maps.newTreeMap();

    final ProjectControl projectControl = projectState.controlFor(currentUser);
    if (projectState == null || !projectControl.isVisible()) {
      return output;
    }

    final Project.NameKey projectName = projectState.getProject().getNameKey();
    Repository repo = null;
    RevWalk revWalk = null;
    try {
      repo = repoManager.openRepository(projectName);
      final Map<String, Ref> refs = repo.getRefDatabase().getRefs(REFS_DASHBOARDS);
      for (final Ref ref : refs.values()) {
        if (projectControl.controlForRef(ref.getName()).canRead()) {
          revWalk = new RevWalk(repo);
          output.putAll(loadDashboards(repo, revWalk, ref));
        }
      }
    } catch (IOException e) {
      log.warn("Failed to load dashboards", e);
    } finally {
      if (revWalk != null) {
        revWalk.release();
      }
      if (repo != null) {
        repo.close();
      }
    }

    return output;
  }

  private Map<String, DashboardInfo> loadDashboards(final Repository repo,
      final RevWalk revWalk, final Ref ref) throws IOException {
    final Map<String, DashboardInfo> output = Maps.newTreeMap();
    final RevCommit commit = revWalk.parseCommit(ref.getObjectId());
    final RevTree tree = commit.getTree();
    TreeWalk treeWalk = new TreeWalk(repo);
    try {
      treeWalk.addTree(tree);
      treeWalk.setRecursive(true);
      while (treeWalk.next()) {
        DashboardInfo info = new DashboardInfo();
        final String path = treeWalk.getPathString();
        info.name = ref.getName().substring(REFS_DASHBOARDS.length()) + " " + path;
        final ObjectLoader loader = repo.open(treeWalk.getObjectId(0));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        loader.copyTo(out);
        Config dashboardConfig = new Config();
        try {
          dashboardConfig.fromText(new String(out.toByteArray(), "UTF-8"));
        } catch (ConfigInvalidException e) {
          log.warn("Failed to load dashboards", e);
        }

        info.description = dashboardConfig.getString("main", null, "description");

        final StringBuilder query = new StringBuilder();
        query.append("title=");
        query.append(path.replaceAll(" ", "+"));
        final Set<String> sections = dashboardConfig.getSubsections("section");
        for (final String section : sections) {
          query.append("&");
          query.append(section.replaceAll(" ", "+"));
          query.append("=");
          query.append(dashboardConfig.getString("section", section, "query"));
        }
        info.parameters = query.toString();

        output.put(info.name, info);
      }
    } finally {
      treeWalk.release();
    }
    return output;
  }

  @SuppressWarnings("unused")
  private static class DashboardInfo {
    final String kind = "gerritcodereview#dashboard";
    String name;
    String description;
    String parameters;
  }
}
