// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.util.Providers;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Singleton
public class ListBranches implements RestReadView<ProjectResource> {
  private final GitRepositoryManager repoManager;
  private final DynamicMap<RestView<BranchResource>> branchViews;

  @Inject
  public ListBranches(GitRepositoryManager repoManager,
      DynamicMap<RestView<BranchResource>> branchViews) {
    this.repoManager = repoManager;
    this.branchViews = branchViews;
  }

  @Override
  public List<BranchInfo> apply(ProjectResource rsrc)
      throws ResourceNotFoundException, IOException {
    List<BranchInfo> branches = Lists.newArrayList();

    BranchInfo headBranch = null;
    BranchInfo configBranch = null;
    final Set<String> targets = Sets.newHashSet();

    final Repository db;
    try {
      db = repoManager.openRepository(rsrc.getNameKey());
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException();
    }

    try {
      List<Ref> refs =
          new ArrayList<>(db.getRefDatabase().getRefs(Constants.R_HEADS)
              .values());

        try {
          Ref head = db.getRef(Constants.HEAD);
          if (head != null) {
            refs.add(head);
          }
        } catch (IOException e) {
          // Ignore the failure reading HEAD.
        }
        try {
          Ref config = db.getRef(RefNames.REFS_CONFIG);
          if (config != null) {
            refs.add(config);
          }
        } catch (IOException e) {
          // Ignore the failure reading refs/meta/config.
        }

      for (Ref ref : refs) {
        if (ref.isSymbolic()) {
          targets.add(ref.getTarget().getName());
        }
      }

      for (Ref ref : refs) {
        if (ref.isSymbolic()) {
          // A symbolic reference to another branch, instead of
          // showing the resolved value, show the name it references.
          //
          String target = ref.getTarget().getName();
          RefControl targetRefControl = rsrc.getControl().controlForRef(target);
          if (!targetRefControl.isVisible()) {
            continue;
          }
          if (target.startsWith(Constants.R_HEADS)) {
            target = target.substring(Constants.R_HEADS.length());
          }

          BranchInfo b = new BranchInfo(ref.getName(), target, false);

          if (Constants.HEAD.equals(ref.getName())) {
            headBranch = b;
          } else {
            b.setCanDelete(targetRefControl.canDelete());
            branches.add(b);
          }
          continue;
        }

        final RefControl refControl = rsrc.getControl().controlForRef(ref.getName());
        if (refControl.isVisible()) {
          if (RefNames.REFS_CONFIG.equals(ref.getName())) {
            configBranch = createBranchInfo(ref, refControl, targets);
          } else {
            branches.add(createBranchInfo(ref, refControl, targets));
          }
        }
      }
    } finally {
      db.close();
    }
    Collections.sort(branches, new Comparator<BranchInfo>() {
      @Override
      public int compare(final BranchInfo a, final BranchInfo b) {
        return a.ref.compareTo(b.ref);
      }
    });
    if (configBranch != null) {
      branches.add(0, configBranch);
    }
    if (headBranch != null) {
      branches.add(0, headBranch);
    }
    return branches;
  }

  private BranchInfo createBranchInfo(Ref ref, RefControl refControl,
      Set<String> targets) {
    BranchInfo info = new BranchInfo(ref.getName(),
        ref.getObjectId() != null ? ref.getObjectId().name() : null,
        !targets.contains(ref.getName()) && refControl.canDelete());
    for (UiAction.Description d : UiActions.from(
        branchViews,
        new BranchResource(refControl.getProjectControl(), info),
        Providers.of(refControl.getCurrentUser()))) {
      if (info.actions == null) {
        info.actions = new TreeMap<>();
      }
      info.actions.put(d.getId(), new ActionInfo(d));
    }
    return info;
  }

  public static class BranchInfo {
    public String ref;
    public String revision;
    public Boolean canDelete;
    public Map<String, ActionInfo> actions;

    public BranchInfo(String ref, String revision, boolean canDelete) {
      this.ref = ref;
      this.revision = revision;
      this.canDelete = canDelete;
    }

    void setCanDelete(boolean canDelete) {
      this.canDelete = canDelete ? true : null;
    }
  }
}
