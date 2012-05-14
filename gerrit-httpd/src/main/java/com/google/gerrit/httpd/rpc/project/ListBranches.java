// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ListBranches extends Handler<ListBranchesResult> {
  interface Factory {
    ListBranches create(@Assisted Project.NameKey name);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final GitRepositoryManager repoManager;

  private final Project.NameKey projectName;

  @Inject
  ListBranches(final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager repoManager,

      @Assisted final Project.NameKey name) {
    this.projectControlFactory = projectControlFactory;
    this.repoManager = repoManager;

    this.projectName = name;
  }

  @Override
  public ListBranchesResult call() throws NoSuchProjectException, IOException {
    final ProjectControl pctl = projectControlFactory.validateFor( //
        projectName, //
        ProjectControl.OWNER | ProjectControl.VISIBLE);

    final List<Branch> branches = new ArrayList<Branch>();
    Branch headBranch = null;
    Branch configBranch = null;
    final Set<String> targets = new HashSet<String>();

    final Repository db;
    try {
      db = repoManager.openRepository(projectName);
    } catch (RepositoryNotFoundException noGitRepository) {
      return new ListBranchesResult(branches, false, true);
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
          RefControl targetRefControl = pctl.controlForRef(target);
          if (!targetRefControl.isVisible()) {
            continue;
          }
          if (target.startsWith(Constants.R_HEADS)) {
            target = target.substring(Constants.R_HEADS.length());
          }

          Branch b = createBranch(ref.getName());
          b.setRevision(new RevId(target));

          if (Constants.HEAD.equals(ref.getName())) {
            b.setCanDelete(false);
            headBranch = b;
          } else {
            b.setCanDelete(targetRefControl.canDelete());
            branches.add(b);
          }
          continue;
        }

        final RefControl refControl = pctl.controlForRef(ref.getName());
        if (refControl.isVisible()) {
          if (ref.getName().startsWith(Constants.R_HEADS)) {
            branches.add(createBranch(ref, refControl, targets));
          } else if (GitRepositoryManager.REF_CONFIG.equals(ref.getName())) {
            configBranch = createBranch(ref, refControl, targets);
          }
        }
      }
    } finally {
      db.close();
    }
    Collections.sort(branches, new Comparator<Branch>() {
      @Override
      public int compare(final Branch a, final Branch b) {
        return a.getName().compareTo(b.getName());
      }
    });
    if (configBranch != null) {
      branches.add(0, configBranch);
    }
    if (headBranch != null) {
      branches.add(0, headBranch);
    }
    return new ListBranchesResult(branches, pctl.canAddRefs(), false);
  }

  private Branch createBranch(final Ref ref, final RefControl refControl,
      final Set<String> targets) {
    final Branch b = createBranch(ref.getName());
    if (ref.getObjectId() != null) {
      b.setRevision(new RevId(ref.getObjectId().name()));
    }
    b.setCanDelete(!targets.contains(ref.getName()) && refControl.canDelete());
    return b;
  }

  private Branch createBranch(final String name) {
    return new Branch(new Branch.NameKey(projectName, name));
  }
}
