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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.git.GitRepositoryManager.REFS_DASHBOARDS;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.DashboardsCollection.DashboardInfo;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;

class ListDashboards implements RestReadView<ProjectResource> {
  private static final Logger log = LoggerFactory.getLogger(DashboardsCollection.class);
  private final GitRepositoryManager gitManager;
  private final ProjectControl.GenericFactory projectFactory;

  @Option(name = "--inherited", usage = "include inherited dashboards")
  private boolean inherited;

  @Inject
  ListDashboards(GitRepositoryManager gitManager,
      ProjectControl.GenericFactory projectFactory) {
    this.gitManager = gitManager;
    this.projectFactory = projectFactory;
  }

  @Override
  public Object apply(ProjectResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    if (!inherited) {
      return scan(resource.getControl());
    }

    List<List<DashboardInfo>> all = Lists.newArrayList();
    ProjectControl ctl = resource.getControl();
    Set<Project.NameKey> seen = Sets.newHashSet();
    for (;;) {
      if (ctl.isVisible()) {
        List<DashboardInfo> list = scan(ctl);
        for (DashboardInfo d : list) {
          d.project = ctl.getProject().getName();
        }
        if (!list.isEmpty()) {
          all.add(list);
        }
      }

      ProjectState ps = ctl.getProjectState().getParentState();
      if (ps == null) {
        break;
      }

      Project.NameKey name = ps.getProject().getNameKey();
      if (!seen.add(name)) {
        break;
      }

      ctl = projectFactory.controlFor(name, ctl.getCurrentUser());
    }
    return all;
  }

  private List<DashboardInfo> scan(ProjectControl ctl) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    Repository git;
    try {
      git = gitManager.openRepository(ctl.getProject().getNameKey());
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException();
    }
    try {
      RevWalk rw = new RevWalk(git);
      try {
        List<DashboardInfo> all = Lists.newArrayList();
        for (Ref ref : git.getRefDatabase().getRefs(REFS_DASHBOARDS).values()) {
          if (ctl.controlForRef(ref.getName()).canRead()) {
            all.addAll(scanDashboards(ctl.getProject(), git, rw, ref));
          }
        }
        return all;
      } finally {
        rw.release();
      }
    } finally {
      git.close();
    }
  }

  private List<DashboardInfo> scanDashboards(Project project,
      Repository git, RevWalk rw, Ref ref) throws IOException {
    List<DashboardInfo> list = Lists.newArrayList();
    TreeWalk tw = new TreeWalk(rw.getObjectReader());
    try {
      tw.addTree(rw.parseTree(ref.getObjectId()));
      tw.setRecursive(true);
      while (tw.next()) {
        if (tw.getFileMode(0) == FileMode.REGULAR_FILE) {
          try {
            list.add(DashboardsCollection.parse(
                project,
                ref.getName().substring(REFS_DASHBOARDS.length()),
                tw.getPathString(),
                new BlobBasedConfig(null, git, tw.getObjectId(0))));
          } catch (ConfigInvalidException e) {
            log.warn(String.format(
                "Cannot parse dashboard %s:%s:%s: %s",
                project.getName(), ref.getName(), tw.getPathString(),
                e.getMessage()));
          }
        }
      }
    } finally {
      tw.release();
    }
    return list;
  }
}
