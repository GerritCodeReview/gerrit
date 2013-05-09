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
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListBranches implements RestReadView<ProjectResource> {

  private final GitRepositoryManager repoManager;

  @Inject
  public ListBranches(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public List<BranchInfo> apply(ProjectResource rsrc)
      throws ResourceNotFoundException, IOException {
    List<BranchInfo> branches = Lists.newArrayList();

    BranchInfo headBranch = null;
    BranchInfo configBranch = null;
    final Set<String> targets = new HashSet<String>();

    final Repository db;
    try {
      db = repoManager.openRepository(rsrc.getNameKey());
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException();
    }

    try {
      final Map<String, Ref> all = db.getAllRefs();

      if (!all.containsKey(Constants.HEAD)) {
        // The branch pointed to by HEAD doesn't exist yet, so getAllRefs
        // filtered it out. If we ask for it individually we can find the
        // underlying target and put it into the map anyway.
        //
        try {
          Ref head = db.getRef(Constants.HEAD);
          if (head != null) {
            all.put(Constants.HEAD, head);
          }
        } catch (IOException e) {
          // Ignore the failure reading HEAD.
        }
      }

      for (final Ref ref : all.values()) {
        if (ref.isSymbolic()) {
          targets.add(ref.getTarget().getName());
        }
      }

      for (final Ref ref : all.values()) {
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

          BranchInfo b = new BranchInfo();
          b.ref = ref.getName();
          b.revision = target;

          if (Constants.HEAD.equals(ref.getName())) {
            b.setCanDelete(false);
            headBranch = b;
          } else {
            b.setCanDelete(targetRefControl.canDelete());
            branches.add(b);
          }
          continue;
        }

        final RefControl refControl = rsrc.getControl().controlForRef(ref.getName());
        if (refControl.isVisible()) {
          if (ref.getName().startsWith(Constants.R_HEADS)) {
            branches.add(createBranchInfo(ref, refControl, targets));
          } else if (GitRepositoryManager.REF_CONFIG.equals(ref.getName())) {
            configBranch = createBranchInfo(ref, refControl, targets);
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
    BranchInfo b = new BranchInfo();
    b.ref = ref.getName();
    if (ref.getObjectId() != null) {
      b.revision = ref.getObjectId().name();
    }
    b.setCanDelete(!targets.contains(ref.getName()) && refControl.canDelete());
    return b;
  }

  public class BranchInfo {
    public String ref;
    public String revision;
    public Boolean canDelete;

    void setCanDelete(boolean canDelete) {
      this.canDelete = canDelete ? true : null;
    }
  }
}
