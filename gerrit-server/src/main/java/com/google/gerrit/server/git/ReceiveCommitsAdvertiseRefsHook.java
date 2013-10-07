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

package com.google.gerrit.server.git;

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.BaseReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Exposes only the non refs/changes/ reference names. */
public class ReceiveCommitsAdvertiseRefsHook implements AdvertiseRefsHook {
  private static final Logger log = LoggerFactory
      .getLogger(ReceiveCommitsAdvertiseRefsHook.class);

  private final ReviewDb db;
  private final Project.NameKey projectName;

  public ReceiveCommitsAdvertiseRefsHook(ReviewDb db,
      Project.NameKey projectName) {
    this.db = db;
    this.projectName = projectName;
  }

  @Override
  public void advertiseRefs(UploadPack us) {
    throw new UnsupportedOperationException(
        "ReceiveCommitsAdvertiseRefsHook cannot be used for UploadPack");
  }

  @Override
  public void advertiseRefs(BaseReceivePack rp)
      throws ServiceMayNotContinueException {
    Map<String, Ref> oldRefs = rp.getAdvertisedRefs();
    if (oldRefs == null) {
      try {
        oldRefs = rp.getRepository().getRefDatabase().getRefs(ALL);
      } catch (IOException e) {
        ServiceMayNotContinueException ex =
            new ServiceMayNotContinueException(e.getMessage());
        ex.initCause(e);
        throw ex;
      }
    }
    Map<String, Ref> r = Maps.newHashMapWithExpectedSize(oldRefs.size());
    for (Map.Entry<String, Ref> e : oldRefs.entrySet()) {
      String name = e.getKey();
      if (!skip(name)) {
        r.put(name, e.getValue());
      }
    }
    rp.setAdvertisedRefs(r, advertiseHistory(r.values(), rp));
  }

  private Set<ObjectId> advertiseHistory(
      Iterable<Ref> sending,
      BaseReceivePack rp) {
    Set<ObjectId> toInclude = Sets.newHashSet();

    // Advertise some recent open changes, in case a commit is based one.
    try {
      Set<PatchSet.Id> toGet = Sets.newHashSetWithExpectedSize(32);
      for (Change c : db.changes().byProjectOpenNext(projectName, "z", 32)) {
        PatchSet.Id id = c.currentPatchSetId();
        if (id != null) {
          toGet.add(id);
        }
      }
      for (PatchSet ps : db.patchSets().get(toGet)) {
        if (ps.getRevision() != null && ps.getRevision().get() != null) {
          toInclude.add(ObjectId.fromString(ps.getRevision().get()));
        }
      }
    } catch (OrmException err) {
      log.error("Cannot list open changes of " + projectName, err);
    }

    // Size of an additional ".have" line.
    final int haveLineLen = 4 + Constants.OBJECT_ID_STRING_LENGTH + 1 + 5 + 1;

    // Maximum number of bytes to "waste" in the advertisement with
    // a peek at this repository's current reachable history.
    final int maxExtraSize = 8192;

    // Number of recent commits to advertise immediately, hoping to
    // show a client a nearby merge base.
    final int base = 64;

    // Number of commits to skip once base has already been shown.
    final int step = 16;

    // Total number of commits to extract from the history.
    final int max = maxExtraSize / haveLineLen;

    // Scan history until the advertisement is full.
    Set<ObjectId> alreadySending = Sets.newHashSet();
    RevWalk rw = rp.getRevWalk();
    for (Ref ref : sending) {
      try {
        if (ref.getObjectId() != null) {
          alreadySending.add(ref.getObjectId());
          rw.markStart(rw.parseCommit(ref.getObjectId()));
        }
      } catch (IOException badCommit) {
        continue;
      }
    }

    int stepCnt = 0;
    RevCommit c;
    try {
      while ((c = rw.next()) != null && toInclude.size() < max) {
        if (alreadySending.contains(c)) {
        } else if (toInclude.contains(c)) {
        } else if (c.getParentCount() > 1) {
        } else if (toInclude.size() < base) {
          toInclude.add(c);
        } else {
          stepCnt = ++stepCnt % step;
          if (stepCnt == 0) {
            toInclude.add(c);
          }
        }
      }
    } catch (IOException err) {
      log.error("Error trying to advertise history on " + projectName, err);
    }
    rw.reset();
    return toInclude;
  }

  private static boolean skip(String name) {
    return name.startsWith("refs/changes/")
        || name.startsWith(GitRepositoryManager.REFS_CACHE_AUTOMERGE)
        || MagicBranch.isMagicBranch(name);
  }
}
