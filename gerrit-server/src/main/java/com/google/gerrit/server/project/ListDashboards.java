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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_DASHBOARDS;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.DashboardsCollection.DashboardInfo;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

class ListDashboards implements RestReadView<ProjectResource> {
  private static final Logger log = LoggerFactory.getLogger(ListDashboards.class);

  private final GitRepositoryManager gitManager;

  @Option(name = "--inherited", usage = "include inherited dashboards")
  private boolean inherited;

  @Inject
  ListDashboards(GitRepositoryManager gitManager) {
    this.gitManager = gitManager;
  }

  @Override
  public List<?> apply(ProjectResource resource) throws ResourceNotFoundException, IOException {
    ProjectControl ctl = resource.getControl();
    String project = ctl.getProject().getName();
    if (!inherited) {
      return scan(resource.getControl(), project, true);
    }

    List<List<DashboardInfo>> all = new ArrayList<>();
    boolean setDefault = true;
    for (ProjectState ps : ctl.getProjectState().tree()) {
      ctl = ps.controlFor(ctl.getUser());
      if (ctl.isVisible()) {
        List<DashboardInfo> list = scan(ctl, project, setDefault);
        for (DashboardInfo d : list) {
          if (d.isDefault != null && Boolean.TRUE.equals(d.isDefault)) {
            setDefault = false;
          }
        }
        if (!list.isEmpty()) {
          all.add(list);
        }
      }
    }
    return all;
  }

  private List<DashboardInfo> scan(ProjectControl ctl, String project, boolean setDefault)
      throws ResourceNotFoundException, IOException {
    Project.NameKey projectName = ctl.getProject().getNameKey();
    try (Repository git = gitManager.openRepository(projectName);
        RevWalk rw = new RevWalk(git)) {
      List<DashboardInfo> all = new ArrayList<>();
      for (Ref ref : git.getRefDatabase().getRefs(REFS_DASHBOARDS).values()) {
        if (ctl.controlForRef(ref.getName()).canRead()) {
          all.addAll(scanDashboards(ctl.getProject(), git, rw, ref, project, setDefault));
        }
      }
      return all;
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException();
    }
  }

  private List<DashboardInfo> scanDashboards(
      Project definingProject,
      Repository git,
      RevWalk rw,
      Ref ref,
      String project,
      boolean setDefault)
      throws IOException {
    List<DashboardInfo> list = new ArrayList<>();
    try (TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
      tw.addTree(rw.parseTree(ref.getObjectId()));
      tw.setRecursive(true);
      while (tw.next()) {
        if (tw.getFileMode(0) == FileMode.REGULAR_FILE) {
          try {
            list.add(
                DashboardsCollection.parse(
                    definingProject,
                    ref.getName().substring(REFS_DASHBOARDS.length()),
                    tw.getPathString(),
                    new BlobBasedConfig(null, git, tw.getObjectId(0)),
                    project,
                    setDefault));
          } catch (ConfigInvalidException e) {
            log.warn(
                String.format(
                    "Cannot parse dashboard %s:%s:%s: %s",
                    definingProject.getName(), ref.getName(), tw.getPathString(), e.getMessage()));
          }
        }
      }
    }
    return list;
  }
}
