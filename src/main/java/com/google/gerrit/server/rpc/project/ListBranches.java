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

package com.google.gerrit.server.rpc.project;

import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.rpc.Handler;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class ListBranches extends Handler<List<Branch>> {
  interface Factory {
    ListBranches create(@Assisted Project.NameKey name);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final GerritServer gerritServer;

  private final Project.NameKey projectName;

  @Inject
  ListBranches(final ProjectControl.Factory projectControlFactory,
      final GerritServer gerritServer,

      @Assisted final Project.NameKey name) {
    this.projectControlFactory = projectControlFactory;
    this.gerritServer = gerritServer;

    this.projectName = name;
  }

  @Override
  public List<Branch> call() throws NoSuchProjectException,
      RepositoryNotFoundException {
    final ProjectControl projectControl =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);

    final List<Branch> branches = new ArrayList<Branch>();
    final Repository db = gerritServer.openRepository(projectName.get());
    try {
      for (final Ref ref : db.getAllRefs().values()) {
        final String name = ref.getOrigName();
        if (name.startsWith(Constants.R_HEADS)) {
          final Branch b = new Branch(new Branch.NameKey(projectName, name));
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
    return branches;
  }
}
