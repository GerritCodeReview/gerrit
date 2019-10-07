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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.entities.RefNames.REFS_DASHBOARDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Option;

public class ListDashboards implements RestReadView<ProjectResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager gitManager;
  private final PermissionBackend permissionBackend;

  @Option(name = "--inherited", usage = "include inherited dashboards")
  private boolean inherited;

  @Inject
  ListDashboards(GitRepositoryManager gitManager, PermissionBackend permissionBackend) {
    this.gitManager = gitManager;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<List<?>> apply(ProjectResource rsrc)
      throws ResourceNotFoundException, IOException, PermissionBackendException {
    String project = rsrc.getName();
    if (!inherited) {
      return Response.ok(scan(rsrc.getProjectState(), project, true));
    }

    List<List<DashboardInfo>> all = new ArrayList<>();
    boolean setDefault = true;
    for (ProjectState ps : tree(rsrc)) {
      List<DashboardInfo> list = scan(ps, project, setDefault);
      for (DashboardInfo d : list) {
        if (d.isDefault != null && Boolean.TRUE.equals(d.isDefault)) {
          setDefault = false;
        }
      }
      if (!list.isEmpty()) {
        all.add(list);
      }
    }
    return Response.ok(all);
  }

  private Collection<ProjectState> tree(ProjectResource rsrc) throws PermissionBackendException {
    Map<Project.NameKey, ProjectState> tree = new LinkedHashMap<>();
    for (ProjectState ps : rsrc.getProjectState().tree()) {
      if (ps.statePermitsRead()) {
        tree.put(ps.getNameKey(), ps);
      }
    }

    tree.keySet()
        .retainAll(permissionBackend.currentUser().filter(ProjectPermission.ACCESS, tree.keySet()));
    return tree.values();
  }

  private List<DashboardInfo> scan(ProjectState state, String project, boolean setDefault)
      throws ResourceNotFoundException, IOException, PermissionBackendException {
    if (!state.statePermitsRead()) {
      return ImmutableList.of();
    }

    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(state.getNameKey());
    try (Repository git = gitManager.openRepository(state.getNameKey());
        RevWalk rw = new RevWalk(git)) {
      List<DashboardInfo> all = new ArrayList<>();
      for (Ref ref : git.getRefDatabase().getRefsByPrefix(REFS_DASHBOARDS)) {
        try {
          perm.ref(ref.getName()).check(RefPermission.READ);
          all.addAll(scanDashboards(state.getProject(), git, rw, ref, project, setDefault));
        } catch (AuthException e) {
          // Do nothing.
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
            logger.atWarning().log(
                "Cannot parse dashboard %s:%s:%s: %s",
                definingProject.getName(), ref.getName(), tw.getPathString(), e.getMessage());
          }
        }
      }
    }
    return list;
  }
}
