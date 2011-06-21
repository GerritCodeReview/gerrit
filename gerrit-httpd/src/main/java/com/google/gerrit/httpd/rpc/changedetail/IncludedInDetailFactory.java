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
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Creates a {@link IncludedInDetail} of a {@link Change}. */
public class IncludedInDetailFactory extends Handler<IncludedInDetail> {
  private static final Logger log =
      LoggerFactory.getLogger(IncludedInDetailFactory.class);

  public interface Factory {
    IncludedInDetailFactory create(Change.Id id);
  }

  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;
  private final GitRepositoryManager repoManager;
  private final Change.Id changeId;

  private IncludedInDetail detail;
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

        detail = new IncludedInDetail();
        detail.setBranches(includedIn(repo, rw, rev, Constants.R_HEADS));
        detail.setTags(includedIn(repo, rw, rev, Constants.R_TAGS));

        return detail;
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }

  private List<String> includedIn(final Repository repo, final RevWalk rw,
      final RevCommit rev, final String namespace) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {
    final List<String> result = new ArrayList<String>();
    for (final Ref ref : repo.getRefDatabase().getRefs(namespace).values()) {
      final RevCommit tip;
      try {
        tip = rw.parseCommit(ref.getObjectId());
      } catch (IncorrectObjectTypeException notCommit) {
        // Its OK for a tag reference to point to a blob or a tree, this
        // is common in the Linux kernel or git.git repository.
        //
        continue;
      } catch (MissingObjectException notHere) {
        // Log the problem with this branch, but keep processing.
        //
        log.warn("Reference " + ref.getName() + " in " + repo.getDirectory()
            + " points to dangling object " + ref.getObjectId());
        continue;
      }

      if (rw.isMergedInto(rev, tip)) {
        result.add(ref.getName().substring(namespace.length()));
      }
    }
    return result;
  }
}
