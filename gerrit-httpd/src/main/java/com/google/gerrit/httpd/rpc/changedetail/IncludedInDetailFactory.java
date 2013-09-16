// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.common.errors.InvalidRevisionException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.IncludedInResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/** Creates a {@link IncludedInDetail} of a {@link Change}. */
class IncludedInDetailFactory extends Handler<IncludedInDetail> {

  interface Factory {
    IncludedInDetailFactory create(Change.Id id);
  }

  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;
  private final GitRepositoryManager repoManager;
  private final Change.Id changeId;

  private ChangeControl control;

  @Inject
  IncludedInDetailFactory(final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      final GitRepositoryManager repoManager, @Assisted final Change.Id changeId) {
    this.changeControlFactory = changeControlFactory;
    this.repoManager = repoManager;
    this.changeId = changeId;
    this.db = db;
  }

  @Override
  public IncludedInDetail call() throws OrmException, NoSuchChangeException,
      IOException, InvalidRevisionException {
    control = changeControlFactory.validateFor(changeId);

    final PatchSet patch =
        db.patchSets().get(control.getChange().currentPatchSetId());
    final Repository repo =
        repoManager.openRepository(control.getProject().getNameKey());
    try {
      final RevWalk rw = new RevWalk(repo);
      try {
        rw.setRetainBody(false);

        final RevCommit rev;
        try {
          rev = rw.parseCommit(ObjectId.fromString(patch.getRevision().get()));
        } catch (IncorrectObjectTypeException err) {
          throw new InvalidRevisionException();
        } catch (MissingObjectException err) {
          throw new InvalidRevisionException();
        }

        return IncludedInResolver.resolve(repo, rw, rev);
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }
}