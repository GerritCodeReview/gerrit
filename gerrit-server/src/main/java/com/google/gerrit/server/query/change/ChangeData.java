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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.TrackingId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeData {
  private final Change.Id legacyId;
  private Change change;
  private String commitMessage;
  private Collection<PatchSet> patches;
  private Collection<PatchSetApproval> approvals;
  private Map<PatchSet.Id,Collection<PatchSetApproval>> approvalsMap;
  private Collection<PatchSetApproval> currentApprovals;
  private String[] currentFiles;
  private Collection<PatchLineComment> comments;
  private Collection<TrackingId> trackingIds;
  private CurrentUser visibleTo;
  private ChangeControl changeControl;
  private List<ChangeMessage> messages;

  public ChangeData(final Change.Id id) {
    legacyId = id;
  }

  public ChangeData(final Change c) {
    legacyId = c.getId();
    change = c;
  }

  public void setCurrentFilePaths(String[] filePaths) {
    currentFiles = filePaths;
  }

  public String[] currentFilePaths(Provider<ReviewDb> db,
      PatchListCache cache) throws OrmException {
    if (currentFiles == null) {
      Change c = change(db);
      if (c == null) {
        return null;
      }
      PatchSet ps = currentPatchSet(db);
      if (ps == null) {
        return null;
      }

      PatchList p = cache.get(c, ps);
      List<String> r = new ArrayList<String>(p.getPatches().size());
      for (PatchListEntry e : p.getPatches()) {
        if (Patch.COMMIT_MSG.equals(e.getNewName())) {
          continue;
        }
        switch (e.getChangeType()) {
          case ADDED:
          case MODIFIED:
          case DELETED:
          case COPIED:
            r.add(e.getNewName());
            break;
          case RENAMED:
            r.add(e.getOldName());
            r.add(e.getNewName());
            break;
        }
      }
      currentFiles = r.toArray(new String[r.size()]);
      Arrays.sort(currentFiles);
    }
    return currentFiles;
  }

  public Change.Id getId() {
    return legacyId;
  }

  public Change getChange() {
    return change;
  }

  public boolean hasChange() {
    return change != null;
  }

  boolean fastIsVisibleTo(CurrentUser user) {
    return visibleTo == user;
  }

  ChangeControl changeControl() {
    return changeControl;
  }

  void cacheVisibleTo(ChangeControl ctl) {
    visibleTo = ctl.getCurrentUser();
    changeControl = ctl;
  }

  public Change change(Provider<ReviewDb> db) throws OrmException {
    if (change == null) {
      change = db.get().changes().get(legacyId);
    }
    return change;
  }

  public PatchSet currentPatchSet(Provider<ReviewDb> db) throws OrmException {
    Change c = change(db);
    if (c == null) {
      return null;
    }
    for (PatchSet p : patches(db)) {
      if (p.getId().equals(c.currentPatchSetId())) {
        return p;
      }
    }
    return null;
  }

  public Collection<PatchSetApproval> currentApprovals(Provider<ReviewDb> db)
      throws OrmException {
    if (currentApprovals == null) {
      Change c = change(db);
      if (c == null) {
        currentApprovals = Collections.emptyList();
      } else {
        currentApprovals = approvalsFor(db, c.currentPatchSetId());
      }
    }
    return currentApprovals;
  }

  public Collection<PatchSetApproval> approvalsFor(Provider<ReviewDb> db,
      PatchSet.Id psId) throws OrmException {
    List<PatchSetApproval> r = new ArrayList<PatchSetApproval>();
    for (PatchSetApproval p : approvals(db)) {
      if (p.getPatchSetId().equals(psId)) {
        r.add(p);
      }
    }
    return r;
  }

  public String commitMessage(GitRepositoryManager repoManager,
      Provider<ReviewDb> db) throws IOException, OrmException {
    if (commitMessage == null) {
      PatchSet.Id psId = change(db).currentPatchSetId();
      String sha1 = db.get().patchSets().get(psId).getRevision().get();
      Project.NameKey name = change.getProject();
      Repository repo = repoManager.openRepository(name);
      try {
        RevWalk walk = new RevWalk(repo);
        try {
          RevCommit c = walk.parseCommit(ObjectId.fromString(sha1));
          commitMessage = c.getFullMessage();
        } finally {
          walk.release();
        }
      } finally {
        repo.close();
      }
    }
    return commitMessage;
  }

  public Collection<PatchSet> patches(Provider<ReviewDb> db)
      throws OrmException {
    if (patches == null) {
      patches = db.get().patchSets().byChange(legacyId).toList();
    }
    return patches;
  }

  public Collection<PatchSetApproval> approvals(Provider<ReviewDb> db)
      throws OrmException {
    if (approvals == null) {
      approvals = db.get().patchSetApprovals().byChange(legacyId).toList();
    }
    return approvals;
  }

  public Map<PatchSet.Id,Collection<PatchSetApproval>> approvalsMap(Provider<ReviewDb> db)
      throws OrmException {
    if (approvalsMap == null) {
      Collection<PatchSetApproval> all = approvals(db);
      approvalsMap = new HashMap<PatchSet.Id,Collection<PatchSetApproval>>(all.size());
      for (PatchSetApproval psa : all) {
        Collection<PatchSetApproval> c = approvalsMap.get(psa.getPatchSetId());
        if (c == null) {
          c = new ArrayList<PatchSetApproval>();
          approvalsMap.put(psa.getPatchSetId(), c);
        }
        c.add(psa);
      }
    }
    return approvalsMap;
  }

  public Collection<PatchLineComment> comments(Provider<ReviewDb> db)
      throws OrmException {
    if (comments == null) {
      comments = db.get().patchComments().byChange(legacyId).toList();
    }
    return comments;
  }

  public Collection<TrackingId> trackingIds(Provider<ReviewDb> db)
      throws OrmException {
    if (trackingIds == null) {
      trackingIds = db.get().trackingIds().byChange(legacyId).toList();
    }
    return trackingIds;
  }

  public List<ChangeMessage> messages(Provider<ReviewDb> db)
      throws OrmException {
    if (messages == null) {
      messages = db.get().changeMessages().byChange(legacyId).toList();
    }
    return messages;
  }
}
