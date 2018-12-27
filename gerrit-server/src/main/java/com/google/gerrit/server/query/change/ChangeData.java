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

import static com.google.gerrit.server.ApprovalsUtil.sortApprovals;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.ReviewerState;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChangeData {
  public static void ensureChangeLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    Map<Change.Id, ChangeData> missing = Maps.newHashMap();
    for (ChangeData cd : changes) {
      if (cd.change == null) {
        missing.put(cd.getId(), cd);
      }
    }
    if (!missing.isEmpty()) {
      ChangeData first = missing.values().iterator().next();
      if (!first.notesMigration.readPatchSetApprovals()) {
        ReviewDb db = missing.values().iterator().next().db;
        for (Change change : db.changes().get(missing.keySet())) {
          missing.get(change.getId()).change = change;
        }
      } else {
        for (ChangeData cd : missing.values()) {
          cd.change();
        }
      }
    }
  }

  public static void ensureAllPatchSetsLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    for (ChangeData cd : changes) {
      cd.patches();
    }
  }

  public static void ensureCurrentPatchSetLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    Map<PatchSet.Id, ChangeData> missing = Maps.newHashMap();
    for (ChangeData cd : changes) {
      if (cd.currentPatchSet == null && cd.patches == null) {
        missing.put(cd.change().currentPatchSetId(), cd);
      }
    }
    if (!missing.isEmpty()) {
      ReviewDb db = missing.values().iterator().next().db;
      for (PatchSet ps : db.patchSets().get(missing.keySet())) {
        ChangeData cd = missing.get(ps.getId());
        cd.currentPatchSet = ps;
      }
    }
  }

  public static void ensureCurrentApprovalsLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    List<ResultSet<PatchSetApproval>> pending = Lists.newArrayList();
    for (ChangeData cd : changes) {
      if (!cd.notesMigration.readPatchSetApprovals()) {
        if (cd.currentApprovals == null) {
          pending.add(cd.db.patchSetApprovals()
              .byPatchSet(cd.change().currentPatchSetId()));
        }
      } else {
        cd.currentApprovals();
      }
    }
    if (!pending.isEmpty()) {
      int idx = 0;
      for (ChangeData cd : changes) {
        if (cd.currentApprovals == null) {
          cd.currentApprovals = sortApprovals(pending.get(idx++));
        }
      }
    }
  }

  public interface Factory {
    ChangeData create(ReviewDb db, Change.Id id);
    ChangeData create(ReviewDb db, Change c);
    ChangeData create(ReviewDb db, ChangeControl c);
  }

  /**
   * Create an instance for testing only.
   * <p>
   * Attempting to lazy load data will fail with NPEs.
   *
   * @param id change ID
   * @return instance for testing.
   */
  static ChangeData createForTest(Change.Id id) {
    return new ChangeData(null, null, null, null, null, null, null, null, id);
  }

  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ApprovalsUtil approvalsUtil;
  private final PatchListCache patchListCache;
  private final NotesMigration notesMigration;
  private final Change.Id legacyId;
  private ChangeDataSource returnedBySource;
  private Change change;
  private ChangeNotes notes;
  private String commitMessage;
  private List<FooterLine> commitFooters;
  private PatchSet currentPatchSet;
  private Collection<PatchSet> patches;
  private ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals;
  private List<PatchSetApproval> currentApprovals;
  private List<String> currentFiles;
  private Collection<PatchLineComment> comments;
  private CurrentUser visibleTo;
  private ChangeControl changeControl;
  private List<ChangeMessage> messages;
  private List<SubmitRecord> submitRecords;
  private ChangedLines changedLines;

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      @Assisted ReviewDb db,
      @Assisted Change.Id id) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    legacyId = id;
  }

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      @Assisted ReviewDb db,
      @Assisted Change c) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    legacyId = c.getId();
    change = c;
  }

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      @Assisted ReviewDb db,
      @Assisted ChangeControl c) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    legacyId = c.getChange().getId();
    change = c.getChange();
    changeControl = c;
    notes = c.getNotes();
  }

  public boolean isFromSource(ChangeDataSource s) {
    return s == returnedBySource;
  }

  public void cacheFromSource(ChangeDataSource s) {
    returnedBySource = s;
  }

  public void setCurrentFilePaths(List<String> filePaths) {
    currentFiles = ImmutableList.copyOf(filePaths);
  }

  public List<String> currentFilePaths() throws OrmException {
    if (currentFiles == null) {
      Change c = change();
      if (c == null) {
        return null;
      }
      PatchSet ps = currentPatchSet();
      if (ps == null) {
        return null;
      }

      PatchList p;
      try {
        p = patchListCache.get(c, ps);
      } catch (PatchListNotAvailableException e) {
        currentFiles = Collections.emptyList();
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
          case REWRITE:
            r.add(e.getNewName());
            break;

          case RENAMED:
            r.add(e.getOldName());
            r.add(e.getNewName());
            break;
        }
      }
      Collections.sort(r);
      currentFiles = Collections.unmodifiableList(r);
    }
    return currentFiles;
  }

  public ChangedLines changedLines() throws OrmException {
    if (changedLines == null) {
      Change c = change();
      if (c == null) {
        return null;
      }

      PatchSet ps = currentPatchSet();
      if (ps == null) {
        return null;
      }

      PatchList p;
      try {
        p = patchListCache.get(c, ps);
      } catch (PatchListNotAvailableException e) {
        return null;
      }

      changedLines = new ChangedLines(p.getInsertions(), p.getDeletions());
    }
    return changedLines;
  }

  public Change.Id getId() {
    return legacyId;
  }

  boolean fastIsVisibleTo(CurrentUser user) {
    return visibleTo == user;
  }

  public boolean hasChangeControl() {
    return changeControl != null;
  }

  public ChangeControl changeControl() throws NoSuchChangeException,
      OrmException {
    if (changeControl == null) {
      Change c = change();
      changeControl =
          changeControlFactory.controlFor(c, userFactory.create(c.getOwner()));
    }
    return changeControl;
  }

  void cacheVisibleTo(ChangeControl ctl) {
    visibleTo = ctl.getCurrentUser();
    changeControl = ctl;
  }

  public Change change() throws OrmException {
    if (change == null) {
      change = db.changes().get(legacyId);
    }
    return change;
  }

  public ChangeNotes notes() throws OrmException {
    if (notes == null) {
      notes = notesFactory.create(change());
    }
    return notes;
  }

  public PatchSet currentPatchSet() throws OrmException {
    if (currentPatchSet == null) {
      Change c = change();
      if (c == null) {
        return null;
      }
      for (PatchSet p : patches()) {
        if (p.getId().equals(c.currentPatchSetId())) {
          currentPatchSet = p;
          return p;
        }
      }
    }
    return currentPatchSet;
  }

  public List<PatchSetApproval> currentApprovals()
      throws OrmException {
    if (currentApprovals == null) {
      Change c = change();
      if (c == null) {
        currentApprovals = Collections.emptyList();
      } else if (allApprovals != null) {
        return allApprovals.get(c.currentPatchSetId());
      } else {
        currentApprovals = approvalsUtil.byPatchSet(
            db, notes(), c.currentPatchSetId());
      }
    }
    return currentApprovals;
  }

  public void setCurrentApprovals(List<PatchSetApproval> approvals) {
    currentApprovals = approvals;
  }

  public String commitMessage() throws NoSuchChangeException, IOException,
      OrmException {
    if (commitMessage == null) {
      loadCommitData();
    }
    return commitMessage;
  }

  public List<FooterLine> commitFooters() throws NoSuchChangeException,
      IOException, OrmException {
    if (commitFooters == null) {
      loadCommitData();
    }
    return commitFooters;
  }

  private void loadCommitData() throws NoSuchChangeException, OrmException,
      RepositoryNotFoundException, IOException, MissingObjectException,
      IncorrectObjectTypeException {
    PatchSet.Id psId = change().currentPatchSetId();
    PatchSet ps = db.patchSets().get(psId);
    if (ps == null) {
      throw new NoSuchChangeException(legacyId);
    }
    String sha1 = ps.getRevision().get();
    Repository repo = repoManager.openRepository(change().getProject());
    try {
      RevWalk walk = new RevWalk(repo);
      try {
        RevCommit c = walk.parseCommit(ObjectId.fromString(sha1));
        commitMessage = c.getFullMessage();
        commitFooters = c.getFooterLines();
      } finally {
        walk.close();
      }
    } finally {
      repo.close();
    }
  }

  /**
   * @return patches for the change.
   * @throws OrmException an error occurred reading the database.
   */
  public Collection<PatchSet> patches()
      throws OrmException {
    if (patches == null) {
      patches = db.patchSets().byChange(legacyId).toList();
    }
    return patches;
  }

  /**
   * @return patch with the given ID, or null if it does not exist.
   * @throws OrmException an error occurred reading the database.
   */
  public PatchSet patch(PatchSet.Id psId) throws OrmException {
    if (currentPatchSet != null && currentPatchSet.getId().equals(psId)) {
      return currentPatchSet;
    }
    for (PatchSet ps : patches()) {
      if (ps.getId().equals(psId)) {
        return ps;
      }
    }
    return null;
  }

  /**
   * @return all patch set approvals for the change, keyed by ID, ordered by
   *     timestamp within each patch set.
   * @throws OrmException an error occurred reading the database.
   */
  public ListMultimap<PatchSet.Id, PatchSetApproval> approvals()
      throws OrmException {
    if (allApprovals == null) {
      allApprovals = approvalsUtil.byChange(db, notes());
    }
    return allApprovals;
  }

  public SetMultimap<ReviewerState, Account.Id> reviewers()
      throws OrmException {
    return approvalsUtil.getReviewers(notes(), approvals().values());
  }

  public Collection<PatchLineComment> comments()
      throws OrmException {
    if (comments == null) {
      comments = db.patchComments().byChange(legacyId).toList();
    }
    return comments;
  }

  public List<ChangeMessage> messages()
      throws OrmException {
    if (messages == null) {
      messages = db.changeMessages().byChange(legacyId).toList();
    }
    return messages;
  }

  public void setSubmitRecords(List<SubmitRecord> records) {
    submitRecords = records;
  }

  public List<SubmitRecord> getSubmitRecords() {
    return submitRecords;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).addValue(getId()).toString();
  }

  public static class ChangedLines {
    public final int insertions;
    public final int deletions;

    ChangedLines(int insertions, int deletions) {
      this.insertions = insertions;
      this.deletions = deletions;
    }
  }
}
