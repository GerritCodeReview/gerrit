// Copyright 2008 Google Inc.
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

package com.google.codereview.manager.merge;

import com.google.codereview.internal.PostBuildResult.PostBuildResultResponse;
import com.google.codereview.manager.InvalidRepositoryException;
import com.google.codereview.manager.RepositoryCache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;

public class UpdateOp {
  private static final Log LOG = LogFactory.getLog(UpdateOp.class);

  private final RepositoryCache repoCache;
  private final PostBuildResultResponse in;
  private Repository db;
  private RevCommit newCommit;
  private RevWalk rw;
  private RefUpdate branch;

  UpdateOp(final RepositoryCache rc, final PostBuildResultResponse mergeInfo) {
    repoCache = rc;
    in = mergeInfo;
  }

  boolean update() {
    final String loc = in.getDestProjectName() + " " + in.getDestBranchName();
    LOG.debug("Updating " + loc);
    try {
      updateImpl();
      return true;
    } catch (MergeException ee) {
      LOG.error("Error updating " + loc, ee);
      return false;
    } finally {
      if (db != null && rw != null) {
        unpinMerge();
      }
    }
  }

  private void unpinMerge() {
    final String name = MergeOp.mergePinName(in.getRevisionId());
    try {
      final RefUpdate ru = db.updateRef(name);
      ru.setNewObjectId(ru.getOldObjectId());
      ru.delete(rw);
    } catch (IOException err) {
      LOG.warn("Cannot remove " + name, err);
    }
  }

  private void updateImpl() throws MergeException {
    openRepository();
    openBranch();
    parseCommit();
    updateBranch();
  }

  private void openRepository() throws MergeException {
    final String name = in.getDestProjectName();
    try {
      db = repoCache.get(name);
    } catch (InvalidRepositoryException notGit) {
      final String m = "Repository \"" + name + "\" unknown.";
      throw new MergeException(m, notGit);
    }
    rw = new RevWalk(db);
  }

  private void openBranch() throws MergeException {
    try {
      branch = db.updateRef(in.getDestBranchName());
    } catch (IOException e) {
      throw new MergeException("Cannot open branch", e);
    }
  }

  private void parseCommit() throws MergeException {
    try {
      newCommit = rw.parseCommit(ObjectId.fromString(in.getRevisionId()));
    } catch (IllegalArgumentException e) {
      throw new MergeException("Not a commit name: " + in.getRevisionId());
    } catch (IOException e) {
      throw new MergeException("Not a commit name: " + in.getRevisionId(), e);
    }
  }

  private void updateBranch() throws MergeException {
    branch.setForceUpdate(false);
    branch.setNewObjectId(newCommit);
    branch.setRefLogMessage(newCommit.getShortMessage(), false);

    final RefUpdate.Result r;
    try {
      r = branch.update(rw);
    } catch (IOException err) {
      final String m = "Failure updating " + branch.getName();
      throw new MergeException(m, err);
    }

    if (r == RefUpdate.Result.NEW) {
    } else if (r == RefUpdate.Result.FAST_FORWARD) {
    } else if (r == RefUpdate.Result.NO_CHANGE) {
    } else {
      final String m = "Failure updating " + branch.getName() + ": " + r.name();
      throw new MergeException(m);
    }
  }
}
