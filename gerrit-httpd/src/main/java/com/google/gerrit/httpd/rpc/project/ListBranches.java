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

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
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
import java.util.List;
import java.util.Map;

class ListBranches extends Handler<List<Branch>> {
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
  public List<Branch> call() throws NoSuchProjectException,
      RepositoryNotFoundException {
    projectControlFactory.validateFor(projectName, ProjectControl.OWNER
        | ProjectControl.VISIBLE);

    final List<Branch> branches = new ArrayList<Branch>();
    Branch headBranch = null;
    final Repository db = repoManager.openRepository(projectName.get());
    try {
      final Map<String, Ref> all = db.getAllRefs();

      if (!all.containsKey(Constants.HEAD)) {
        // The branch pointed to by HEAD doesn't exist yet. Fake
        // that it exists by returning a Ref with no ObjectId.
        //
        try {
          final String head = db.getFullBranch();
          if (head != null && head.startsWith(Constants.R_REFS)) {
            all.put(Constants.HEAD, new Ref(Ref.Storage.LOOSE, Constants.HEAD,
                head, null));
          }
        } catch (IOException e) {
          // Ignore the failure reading HEAD.
        }
      }

      for (final Ref ref : all.values()) {
        if (Constants.HEAD.equals(ref.getOrigName())
            && !ref.getOrigName().equals(ref.getName())) {
          // HEAD is a symbolic reference to another branch, instead of
          // showing the resolved value, show the name it references.
          //
          headBranch = createBranch(Constants.HEAD);
          String target = ref.getName();
          if (target.startsWith(Constants.R_HEADS)) {
            target = target.substring(Constants.R_HEADS.length());
          }
          headBranch.setRevision(new RevId(target));
          continue;
        }

        if (ref.getName().startsWith(Constants.R_HEADS)) {
          final Branch b = createBranch(ref.getName());
          if (ref.getObjectId() != null) {
            b.setRevision(new RevId(ref.getObjectId().name()));
          }
          branches.add(b);
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
    if (headBranch != null) {
      branches.add(0, headBranch);
    }
    return branches;
  }

  private Branch createBranch(final String name) {
    return new Branch(new Branch.NameKey(projectName, name));
  }
}
