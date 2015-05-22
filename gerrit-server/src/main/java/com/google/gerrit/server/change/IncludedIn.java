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

package com.google.gerrit.server.change;

import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collection;

@Singleton
class IncludedIn implements RestReadView<ChangeResource> {

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;

  @Inject
  IncludedIn(Provider<ReviewDb> db, GitRepositoryManager repoManager) {
    this.db = db;
    this.repoManager = repoManager;
  }

  @Override
  public IncludedInInfo apply(ChangeResource rsrc) throws BadRequestException,
      ResourceConflictException, OrmException, IOException {
    ChangeControl ctl = rsrc.getControl();
    PatchSet ps =
        db.get().patchSets().get(ctl.getChange().currentPatchSetId());
    Project.NameKey project = ctl.getProject().getNameKey();
    try (Repository r = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(r)) {
      rw.setRetainBody(false);
      RevCommit rev;
      try {
        rev = rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
      } catch (IncorrectObjectTypeException err) {
        throw new BadRequestException(err.getMessage());
      } catch (MissingObjectException err) {
        throw new ResourceConflictException(err.getMessage());
      }
      return new IncludedInInfo(IncludedInResolver.resolve(r, rw, rev));
    }
  }

  static class IncludedInInfo {
    Collection<String> branches;
    Collection<String> tags;

    IncludedInInfo(IncludedInDetail in) {
      branches = in.getBranches();
      tags = in.getTags();
    }
  }
}
