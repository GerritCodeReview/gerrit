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

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.SubmitRecord;
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
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChangeData {
  private static Ordering<PatchSetApproval> SORT_APPROVALS = Ordering.natural()
      .onResultOf(new Function<PatchSetApproval, Timestamp>() {
            @Override
            public Timestamp apply(PatchSetApproval a) {
              return a.getGranted();
            }
          });

  public static List<PatchSetApproval> sortApprovals(
      Iterable<PatchSetApproval> approvals) {
    return SORT_APPROVALS.sortedCopy(approvals);
  }

  public static void ensureChangeLoaded(
      Provider<ReviewDb> db, List<ChangeData> changes) throws OrmException {
    Map<Change.Id, ChangeData> missing = Maps.newHashMap();
    for (ChangeData cd : changes) {
      if (cd.change == null) {
        missing.put(cd.getId(), cd);
      }
    }
    if (!missing.isEmpty()) {
      for (Change change : db.get().changes().get(missing.keySet())) {
        missing.get(change.getId()).change = change;
      }
    }
  }

  public static void ensureAllPatchSetsLoaded(Provider<ReviewDb> db,
      List<ChangeData> changes) throws OrmException {
    for (ChangeData cd : changes) {
      cd.patches(db);
    }
  }

  public static void ensureCurrentPatchSetLoaded(
      Provider<ReviewDb> db, List<ChangeData> changes) throws OrmException {
    Map<PatchSet.Id, ChangeData> missing = Maps.newHashMap();
    for (ChangeData cd : changes) {
      if (cd.currentPatchSet == null && cd.patches == null) {
        missing.put(cd.change(db).currentPatchSetId(), cd);
      }
    }
    if (!missing.isEmpty()) {
      for (PatchSet ps : db.get().patchSets().get(missing.keySet())) {
        ChangeData cd = missing.get(ps.getId());
        cd.currentPatchSet = ps;
        if (cd.limitedIds == null) {
          cd.patches = Lists.newArrayList(ps);
        }
      }
    }
  }

  public static void ensureCurrentApprovalsLoaded(
      Provider<ReviewDb> db, List<ChangeData> changes) throws OrmException {
    List<ResultSet<PatchSetApproval>> pending = Lists.newArrayList();
    for (ChangeData cd : changes) {
      if (cd.currentApprovals == null && cd.limitedApprovals == null) {
        pending.add(db.get().patchSetApprovals()
            .byPatchSet(cd.change(db).currentPatchSetId()));
      }
    }
    if (!pending.isEmpty()) {
      int idx = 0;
      for (ChangeData cd : changes) {
        if (cd.currentApprovals == null && cd.limitedApprovals == null) {
          cd.currentApprovals = sortApprovals(pending.get(idx++));
        }
      }
    }
  }

  private final Change.Id legacyId;
  private Change change;
  private String commitMessage;
  private PatchSet currentPatchSet;
  private Set<PatchSet.Id> limitedIds;
  private Collection<PatchSet> patches;
  private ListMultimap<PatchSet.Id, PatchSetApproval> limitedApprovals;
  private ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals;
  private List<PatchSetApproval> currentApprovals;
  private String[] currentFiles;
  private Collection<PatchLineComment> comments;
  private Collection<TrackingId> trackingIds;
  private CurrentUser visibleTo;
  private ChangeControl changeControl;
  private List<ChangeMessage> messages;
  private List<SubmitRecord> submitRecords;
  private boolean patchesLoaded;

  public ChangeData(final Change.Id id) {
    legacyId = id;
  }

  public ChangeData(final Change c) {
    legacyId = c.getId();
    change = c;
  }

  public ChangeData(final ChangeControl c) {
    legacyId = c.getChange().getId();
    change = c.getChange();
    changeControl = c;
  }

  public void limitToPatchSets(Collection<PatchSet.Id> ids) {
    limitedIds = Sets.newLinkedHashSetWithExpectedSize(ids.size());
    for (PatchSet.Id id : ids) {
      if (!id.getParentKey().equals(legacyId)) {
        throw new IllegalArgumentException(String.format(
            "invalid patch set %s for change %s", id, legacyId));
      }
      limitedIds.add(id);
    }
  }

  public Collection<PatchSet.Id> getLimitedPatchSets() {
    return limitedIds;
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

      PatchList p;
      try {
        p = cache.get(c, ps);
      } catch (PatchListNotAvailableException e) {
        currentFiles = new String[0];
        return currentFiles;
      }

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

          case REWRITE:
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

  public ChangeControl changeControl() {
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
    if (currentPatchSet == null) {
      Change c = change(db);
      if (c == null) {
        return null;
      }
      for (PatchSet p : patches(db)) {
        if (p.getId().equals(c.currentPatchSetId())) {
          currentPatchSet = p;
          return p;
        }
      }
    }
    return currentPatchSet;
  }

  public List<PatchSetApproval> currentApprovals(Provider<ReviewDb> db)
      throws OrmException {
    if (currentApprovals == null) {
      Change c = change(db);
      if (c == null) {
        currentApprovals = Collections.emptyList();
      } else if (allApprovals != null) {
        return allApprovals.get(c.currentPatchSetId());
      } else if (limitedApprovals != null &&
          (limitedIds == null || limitedIds.contains(c.currentPatchSetId()))) {
        return limitedApprovals.get(c.currentPatchSetId());
      } else {
        currentApprovals = sortApprovals(db.get().patchSetApprovals()
            .byPatchSet(c.currentPatchSetId()));
      }
    }
    return currentApprovals;
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

  /**
   * @param db review database.
   * @return patches for the change. If {@link #limitToPatchSets(Collection)}
   *     was previously called, only contains patches with the specified IDs.
   * @throws OrmException an error occurred reading the database.
   */
  public Collection<PatchSet> patches(Provider<ReviewDb> db)
      throws OrmException {
    if (patches == null || !patchesLoaded) {
      if (limitedIds != null) {
        patches = Lists.newArrayList();
        for (PatchSet ps : db.get().patchSets().byChange(legacyId)) {
          if (limitedIds.contains(ps.getId())) {
            patches.add(ps);
          }
        }
      } else {
        patches = db.get().patchSets().byChange(legacyId).toList();
      }
      patchesLoaded = true;
    }
    return patches;
  }

  /**
   * @param db review database.
   * @return patch set approvals for the change in timestamp order. If
   *     {@link #limitToPatchSets(Collection)} was previously called, only contains
   *     approvals for the patches with the specified IDs.
   * @throws OrmException an error occurred reading the database.
   */
  public List<PatchSetApproval> approvals(Provider<ReviewDb> db)
      throws OrmException {
    return ImmutableList.copyOf(approvalsMap(db).values());
  }

  /**
   * @param db review database.
   * @return patch set approvals for the change, keyed by ID, ordered by
   *     timestamp within each patch set. If
   *     {@link #limitToPatchSets(Collection)} was previously called, only
   *     contains approvals for the patches with the specified IDs.
   * @throws OrmException an error occurred reading the database.
   */
  public ListMultimap<PatchSet.Id, PatchSetApproval> approvalsMap(
      Provider<ReviewDb> db) throws OrmException {
    if (limitedApprovals == null) {
      limitedApprovals = ArrayListMultimap.create();
      if (allApprovals != null) {
        for (PatchSet.Id id : limitedIds) {
          limitedApprovals.putAll(id, allApprovals.get(id));
        }
      } else {
        for (PatchSetApproval psa : sortApprovals(
            db.get().patchSetApprovals().byChange(legacyId))) {
          if (limitedIds == null || limitedIds.contains(legacyId)) {
            limitedApprovals.put(psa.getPatchSetId(), psa);
          }
        }
      }
    }
    return limitedApprovals;
  }

  /**
   * @param db review database.
   * @return all patch set approvals for the change in timestamp order
   *     (regardless of whether {@link #limitToPatchSets(Collection)} was
   *     previously called).
   * @throws OrmException an error occurred reading the database.
   */
  public List<PatchSetApproval> allApprovals(Provider<ReviewDb> db)
      throws OrmException {
    return ImmutableList.copyOf(allApprovalsMap(db).values());
  }

  /**
   * @param db review database.
   * @return all patch set approvals for the change (regardless of whether
   *     {@link #limitToPatchSets(Collection)} was previously called), keyed by
   *     ID, ordered by timestamp within each patch set.
   * @throws OrmException an error occurred reading the database.
   */
  public ListMultimap<PatchSet.Id, PatchSetApproval> allApprovalsMap(
      Provider<ReviewDb> db) throws OrmException {
    if (allApprovals == null) {
      allApprovals = ArrayListMultimap.create();
      for (PatchSetApproval psa : sortApprovals(
          db.get().patchSetApprovals().byChange(legacyId))) {
        allApprovals.put(psa.getPatchSetId(), psa);
      }
    }
    return allApprovals;
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

  public void setSubmitRecords(List<SubmitRecord> records) {
    submitRecords = records;
  }

  public List<SubmitRecord> getSubmitRecords() {
    return submitRecords;
  }
}
