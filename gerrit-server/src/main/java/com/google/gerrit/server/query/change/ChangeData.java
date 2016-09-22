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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.ApprovalsUtil.sortApprovals;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChangeData {
  private static final int BATCH_SIZE = 50;

  public static List<Change> asChanges(List<ChangeData> changeDatas)
      throws OrmException {
    List<Change> result = new ArrayList<>(changeDatas.size());
    for (ChangeData cd : changeDatas) {
      result.add(cd.change());
    }
    return result;
  }

  public static Map<Change.Id, ChangeData> asMap(List<ChangeData> changes) {
    Map<Change.Id, ChangeData> result =
        Maps.newHashMapWithExpectedSize(changes.size());
    for (ChangeData cd : changes) {
      result.put(cd.getId(), cd);
    }
    return result;
  }

  public static void ensureChangeLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    } else if (first.notesMigration.readChanges()) {
      for (ChangeData cd : changes) {
        cd.change();
      }
      return;
    }

    Map<Change.Id, ChangeData> missing = new HashMap<>();
    for (ChangeData cd : changes) {
      if (cd.change == null) {
        missing.put(cd.getId(), cd);
      }
    }
    if (missing.isEmpty()) {
      return;
    }
    for (ChangeNotes notes : first.notesFactory.create(
        first.db, missing.keySet())) {
      missing.get(notes.getChangeId()).change = notes.getChange();
    }
  }

  public static void ensureAllPatchSetsLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    } else if (first.notesMigration.readChanges()) {
      for (ChangeData cd : changes) {
        cd.patchSets();
      }
      return;
    }

    List<ResultSet<PatchSet>> results = new ArrayList<>(BATCH_SIZE);
    for (List<ChangeData> batch : Iterables.partition(changes, BATCH_SIZE)) {
      results.clear();
      for (ChangeData cd : batch) {
        if (cd.patchSets == null) {
          results.add(cd.db.patchSets().byChange(cd.getId()));
        } else {
          results.add(null);
        }
      }
      for (int i = 0; i < batch.size(); i++) {
        ResultSet<PatchSet> result = results.get(i);
        if (result != null) {
          batch.get(i).patchSets = result.toList();
        }
      }
    }
  }

  public static void ensureCurrentPatchSetLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    } else if (first.notesMigration.readChanges()) {
      for (ChangeData cd : changes) {
        cd.currentPatchSet();
      }
      return;
    }

    Map<PatchSet.Id, ChangeData> missing = new HashMap<>();
    for (ChangeData cd : changes) {
      if (cd.currentPatchSet == null && cd.patchSets == null) {
        missing.put(cd.change().currentPatchSetId(), cd);
      }
    }
    if (missing.isEmpty()) {
      return;
    }
    for (PatchSet ps : first.db.patchSets().get(missing.keySet())) {
      missing.get(ps.getId()).currentPatchSet = ps;
    }
  }

  public static void ensureCurrentApprovalsLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    } else if (first.notesMigration.readChanges()) {
      for (ChangeData cd : changes) {
        cd.currentApprovals();
      }
      return;
    }

    List<ResultSet<PatchSetApproval>> results = new ArrayList<>(BATCH_SIZE);
    for (List<ChangeData> batch : Iterables.partition(changes, BATCH_SIZE)) {
      results.clear();
      for (ChangeData cd : batch) {
        if (cd.currentApprovals == null) {
          PatchSet.Id psId = cd.change().currentPatchSetId();
          results.add(cd.db.patchSetApprovals().byPatchSet(psId));
        } else {
          results.add(null);
        }
      }
      for (int i = 0; i < batch.size(); i++) {
        ResultSet<PatchSetApproval> result = results.get(i);
        if (result != null) {
          batch.get(i).currentApprovals = sortApprovals(result);
        }
      }
    }
  }

  public static void ensureMessagesLoaded(Iterable<ChangeData> changes)
      throws OrmException {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    } else if (first.notesMigration.readChanges()) {
      for (ChangeData cd : changes) {
        cd.messages();
      }
      return;
    }

    List<ResultSet<ChangeMessage>> results = new ArrayList<>(BATCH_SIZE);
    for (List<ChangeData> batch : Iterables.partition(changes, BATCH_SIZE)) {
      results.clear();
      for (ChangeData cd : batch) {
        if (cd.messages == null) {
          PatchSet.Id psId = cd.change().currentPatchSetId();
          results.add(cd.db.changeMessages().byPatchSet(psId));
        } else {
          results.add(null);
        }
      }
      for (int i = 0; i < batch.size(); i++) {
        ResultSet<ChangeMessage> result = results.get(i);
        if (result != null) {
          batch.get(i).messages = result.toList();
        }
      }
    }
  }

  public static void ensureReviewedByLoadedForOpenChanges(
      Iterable<ChangeData> changes) throws OrmException {
    List<ChangeData> pending = new ArrayList<>();
    for (ChangeData cd : changes) {
      if (cd.reviewedBy == null && cd.change().getStatus().isOpen()) {
        pending.add(cd);
      }
    }

    if (!pending.isEmpty()) {
      ensureAllPatchSetsLoaded(pending);
      ensureMessagesLoaded(pending);
      for (ChangeData cd : pending) {
        cd.reviewedBy();
      }
    }
  }

  public interface Factory {
    ChangeData create(ReviewDb db, Project.NameKey project, Change.Id id);
    ChangeData create(ReviewDb db, Change c);
    ChangeData create(ReviewDb db, ChangeNotes cn);
    ChangeData create(ReviewDb db, ChangeControl c);

    // TODO(dborowitz): Remove when deleting index schemas <27.
    ChangeData createOnlyWhenNoteDbDisabled(ReviewDb db, Change.Id id);
  }

  /**
   * Create an instance for testing only.
   * <p>
   * Attempting to lazy load data will fail with NPEs. Callers may consider
   * manually setting fields that can be set.
   *
   * @param id change ID
   * @return instance for testing.
   */
  public static ChangeData createForTest(Project.NameKey project, Change.Id id,
      int currentPatchSetId) {
    ChangeData cd = new ChangeData(null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, project, id);
    cd.currentPatchSet = new PatchSet(new PatchSet.Id(id, currentPatchSetId));
    return cd;
  }

  private boolean lazyLoad = true;
  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ProjectCache projectCache;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;
  private final PatchListCache patchListCache;
  private final NotesMigration notesMigration;
  private final MergeabilityCache mergeabilityCache;
  private final StarredChangesUtil starredChangesUtil;
  private final Change.Id legacyId;
  private Project.NameKey project;
  private Change change;
  private ChangeNotes notes;
  private String commitMessage;
  private List<FooterLine> commitFooters;
  private PatchSet currentPatchSet;
  private Collection<PatchSet> patchSets;
  private ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals;
  private List<PatchSetApproval> currentApprovals;
  private Map<Integer, List<String>> files;
  private Map<Integer, Optional<PatchList>> patchLists;
  private Collection<Comment> publishedComments;
  private CurrentUser visibleTo;
  private ChangeControl changeControl;
  private List<ChangeMessage> messages;
  private List<SubmitRecord> submitRecords;
  private Optional<ChangedLines> changedLines;
  private SubmitTypeRecord submitTypeRecord;
  private Boolean mergeable;
  private Account.Id assignee;
  private Set<String> hashtags;
  private Set<Account.Id> editsByUser;
  private Set<Account.Id> reviewedBy;
  private Set<Account.Id> draftsByUser;
  @Deprecated
  private Set<Account.Id> starredByUser;
  private ImmutableMultimap<Account.Id, String> stars;
  private ReviewerSet reviewers;
  private List<ReviewerStatusUpdate> reviewerUpdates;
  private PersonIdent author;
  private PersonIdent committer;

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
      @Nullable StarredChangesUtil starredChangesUtil,
      @Assisted ReviewDb db,
      @Assisted Project.NameKey project,
      @Assisted Change.Id id) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    this.starredChangesUtil = starredChangesUtil;
    this.project = project;
    this.legacyId = id;
  }

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
      @Nullable StarredChangesUtil starredChangesUtil,
      @Assisted ReviewDb db,
      @Assisted Change c) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    this.starredChangesUtil = starredChangesUtil;
    legacyId = c.getId();
    change = c;
    project = c.getProject();
  }

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
      @Nullable StarredChangesUtil starredChangesUtil,
      @Assisted ReviewDb db,
      @Assisted ChangeNotes cn) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    this.starredChangesUtil = starredChangesUtil;
    legacyId = cn.getChangeId();
    change = cn.getChange();
    project = cn.getProjectName();
    notes = cn;
  }

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
      @Nullable StarredChangesUtil starredChangesUtil,
      @Assisted ReviewDb db,
      @Assisted ChangeControl c) {
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    this.starredChangesUtil = starredChangesUtil;
    legacyId = c.getId();
    change = c.getChange();
    changeControl = c;
    notes = c.getNotes();
    project = notes.getProjectName();
  }

  @AssistedInject
  private ChangeData(
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
      @Nullable StarredChangesUtil starredChangesUtil,
      @Assisted ReviewDb db,
      @Assisted Change.Id id) {
    checkState(!notesMigration.readChanges(),
        "do not call createOnlyWhenNoteDbDisabled when NoteDb is enabled");
    this.db = db;
    this.repoManager = repoManager;
    this.changeControlFactory = changeControlFactory;
    this.userFactory = userFactory;
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    this.starredChangesUtil = starredChangesUtil;
    this.legacyId = id;
    this.project = null;
  }

  public ChangeData setLazyLoad(boolean load) {
    lazyLoad = load;
    return this;
  }

  public ReviewDb db() {
    return db;
  }

  private Map<Integer, List<String>> initFiles() {
    if (files == null) {
      files = new HashMap<>();
    }
    return files;
  }

  public void setCurrentFilePaths(List<String> filePaths) throws OrmException {
    PatchSet ps = currentPatchSet();
    if (ps != null) {
      initFiles().put(ps.getPatchSetId(), ImmutableList.copyOf(filePaths));
    }
  }

  public List<String> currentFilePaths() throws OrmException {
    PatchSet ps = currentPatchSet();
    return ps != null ? filePaths(ps) : null;
  }

  public List<String> filePaths(PatchSet ps) throws OrmException {
    Integer psId = ps.getPatchSetId();
    List<String> r = initFiles().get(psId);
    if (r == null) {
      Change c = change();
      if (c == null) {
        return null;
      }

      Optional<PatchList> p = getPatchList(c, ps);
      if (!p.isPresent()) {
        List<String> emptyFileList = Collections.emptyList();
        if (lazyLoad) {
          files.put(ps.getPatchSetId(), emptyFileList);
        }
        return emptyFileList;
      }

      r = new ArrayList<>(p.get().getPatches().size());
      for (PatchListEntry e : p.get().getPatches()) {
        if (Patch.isMagic(e.getNewName())) {
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
      r = Collections.unmodifiableList(r);
      files.put(psId, r);
    }
    return r;
  }

  private Optional<PatchList> getPatchList(Change c, PatchSet ps) {
    Integer psId = ps.getId().get();
    if (patchLists == null) {
      patchLists = new HashMap<>();
    }
    Optional<PatchList> r = patchLists.get(psId);
    if (r == null) {
      if (!lazyLoad) {
        return Optional.absent();
      }
      try {
        r = Optional.of(patchListCache.get(c, ps));
      } catch (PatchListNotAvailableException e) {
        r = Optional.absent();
      }
      patchLists.put(psId, r);
    }
    return r;
  }

  private Optional<ChangedLines> computeChangedLines() throws OrmException {
    Change c = change();
    if (c == null) {
      return Optional.absent();
    }
    PatchSet ps = currentPatchSet();
    if (ps == null) {
      return Optional.absent();
    }
    Optional<PatchList> p = getPatchList(c, ps);
    if (!p.isPresent()) {
      return Optional.absent();
    }
    return Optional.of(
        new ChangedLines(p.get().getInsertions(), p.get().getDeletions()));
  }

  public Optional<ChangedLines> changedLines() throws OrmException {
    if (changedLines == null) {
      if (!lazyLoad) {
        return Optional.absent();
      }
      changedLines = computeChangedLines();
    }
    return changedLines;
  }

  public void setChangedLines(int insertions, int deletions) {
    changedLines = Optional.of(new ChangedLines(insertions, deletions));
  }

  public void setNoChangedLines() {
    changedLines = Optional.absent();
  }

  public Change.Id getId() {
    return legacyId;
  }

  public Project.NameKey project() throws OrmException {
    if (project == null) {
      checkState(!notesMigration.readChanges(), "should not have created "
          + " ChangeData without a project when NoteDb is enabled");
      project = change().getProject();
    }
    return project;
  }

  boolean fastIsVisibleTo(CurrentUser user) {
    return visibleTo == user;
  }

  public boolean hasChangeControl() {
    return changeControl != null;
  }

  public ChangeControl changeControl() throws OrmException {
    if (changeControl == null) {
      Change c = change();
      try {
        changeControl = changeControlFactory.controlFor(
            db, c, userFactory.create(c.getOwner()));
      } catch (NoSuchChangeException e) {
        throw new OrmException(e);
      }
    }
    return changeControl;
  }

  public ChangeControl changeControl(CurrentUser user) throws OrmException {
    if (changeControl != null) {
      CurrentUser oldUser = user;
      if (sameUser(user, oldUser)) {
        return changeControl;
      }
      throw new IllegalStateException(
          "user already specified: " + changeControl.getUser());
    }
    try {
      if (change != null) {
        changeControl = changeControlFactory.controlFor(db, change, user);
      } else {
        changeControl =
            changeControlFactory.controlFor(db, project(), legacyId, user);
      }
    } catch (NoSuchChangeException e) {
      throw new OrmException(e);
    }
    return changeControl;
  }

  private static boolean sameUser(CurrentUser a, CurrentUser b) {
    // TODO(dborowitz): This is a hack; general CurrentUser equality would be
    // better.
    if (a.isInternalUser() && b.isInternalUser()) {
      return true;
    } else if (a instanceof AnonymousUser && b instanceof AnonymousUser) {
      return true;
    } else if (a.isIdentifiedUser() && b.isIdentifiedUser()) {
      return a.getAccountId().equals(b.getAccountId());
    }
    return false;
  }

  void cacheVisibleTo(ChangeControl ctl) {
    visibleTo = ctl.getUser();
    changeControl = ctl;
  }

  public Change change() throws OrmException {
    if (change == null && lazyLoad) {
      reloadChange();
    }
    return change;
  }

  public void setChange(Change c) {
    change = c;
  }

  public Change reloadChange() throws OrmException {
    if (project == null) {
      notes = notesFactory.createFromIdOnlyWhenNoteDbDisabled(db, legacyId);
    } else {
      notes = notesFactory.create(db, project, legacyId);
    }
    change = notes.getChange();
    if (change == null) {
      throw new OrmException("Unable to load change " + legacyId);
    }
    setPatchSets(null);
    return change;
  }

  public ChangeNotes notes() throws OrmException {
    if (notes == null) {
      if (!lazyLoad) {
        throw new OrmException("ChangeNotes not available, lazyLoad = false");
      }
      notes = notesFactory.create(db, project(), legacyId);
    }
    return notes;
  }

  public PatchSet currentPatchSet() throws OrmException {
    if (currentPatchSet == null) {
      Change c = change();
      if (c == null) {
        return null;
      }
      for (PatchSet p : patchSets()) {
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
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      Change c = change();
      if (c == null) {
        currentApprovals = Collections.emptyList();
      } else {
        try {
          currentApprovals = ImmutableList.copyOf(approvalsUtil.byPatchSet(
              db, changeControl(), c.currentPatchSetId()));
        } catch (OrmException e) {
          if (e.getCause() instanceof NoSuchChangeException) {
            currentApprovals = Collections.emptyList();
          } else {
            throw e;
          }
        }
      }
    }
    return currentApprovals;
  }

  public void setCurrentApprovals(List<PatchSetApproval> approvals) {
    currentApprovals = approvals;
  }

  public String commitMessage() throws IOException, OrmException {
    if (commitMessage == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return commitMessage;
  }

  public List<FooterLine> commitFooters() throws IOException, OrmException {
    if (commitFooters == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return commitFooters;
  }

  public PersonIdent getAuthor() throws IOException, OrmException {
    if (author == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return author;
  }

  public PersonIdent getCommitter() throws IOException, OrmException {
    if (committer == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return committer;
  }

  private boolean loadCommitData() throws OrmException,
      RepositoryNotFoundException, IOException, MissingObjectException,
      IncorrectObjectTypeException {
    PatchSet ps = currentPatchSet();
    if (ps == null) {
      return false;
    }
    String sha1 = ps.getRevision().get();
    try (Repository repo = repoManager.openRepository(project());
        RevWalk walk = new RevWalk(repo)) {
      RevCommit c = walk.parseCommit(ObjectId.fromString(sha1));
      commitMessage = c.getFullMessage();
      commitFooters = c.getFooterLines();
      author = c.getAuthorIdent();
      committer = c.getCommitterIdent();
    }
    return true;
  }

  /**
   * @return patches for the change, in patch set ID order.
   * @throws OrmException an error occurred reading the database.
   */
  public Collection<PatchSet> patchSets() throws OrmException {
    if (patchSets == null) {
      patchSets = psUtil.byChange(db, notes());
    }
    return patchSets;
  }

  /**
   * @return patches for the change visible to the current user.
   * @throws OrmException an error occurred reading the database.
   */
  public Collection<PatchSet> visiblePatchSets() throws OrmException {
    Predicate<PatchSet> predicate = ps -> {
      try {
        return changeControl().isPatchVisible(ps, db);
      } catch (OrmException e) {
        return false;
      }
    };
    return FluentIterable.from(patchSets()).filter(predicate).toList();
  }

  public void setPatchSets(Collection<PatchSet> patchSets) {
    this.currentPatchSet = null;
    this.patchSets = patchSets;
  }

  /**
   * @return patch with the given ID, or null if it does not exist.
   * @throws OrmException an error occurred reading the database.
   */
  public PatchSet patchSet(PatchSet.Id psId) throws OrmException {
    if (currentPatchSet != null && currentPatchSet.getId().equals(psId)) {
      return currentPatchSet;
    }
    for (PatchSet ps : patchSets()) {
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
      if (!lazyLoad) {
        return ImmutableListMultimap.of();
      }
      allApprovals = approvalsUtil.byChange(db, notes());
    }
    return allApprovals;
  }

  /**
   * @return The submit ('SUBM') approval label
   * @throws OrmException an error occurred reading the database.
   */
  public Optional<PatchSetApproval> getSubmitApproval()
    throws OrmException {
    for (PatchSetApproval psa : currentApprovals()) {
      if (psa.isLegacySubmit()) {
        return Optional.fromNullable(psa);
      }
    }
    return Optional.absent();
  }

  public ReviewerSet reviewers() throws OrmException {
    if (reviewers == null) {
      if (!lazyLoad) {
        return ReviewerSet.empty();
      }
      reviewers = approvalsUtil.getReviewers(notes(), approvals().values());
    }
    return reviewers;
  }

  public void setReviewers(ReviewerSet reviewers) {
    this.reviewers = reviewers;
  }

  public ReviewerSet getReviewers() {
    return reviewers;
  }

  public List<ReviewerStatusUpdate> reviewerUpdates() throws OrmException {
    if (reviewerUpdates == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      reviewerUpdates = approvalsUtil.getReviewerUpdates(notes());
    }
    return reviewerUpdates;
  }

  public void setReviewerUpdates(List<ReviewerStatusUpdate> reviewerUpdates) {
    this.reviewerUpdates = reviewerUpdates;
  }

  public List<ReviewerStatusUpdate> getReviewerUpdates() {
    return reviewerUpdates;
  }

  public Collection<Comment> publishedComments()
      throws OrmException {
    if (publishedComments == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      publishedComments = commentsUtil.publishedByChange(db, notes());
    }
    return publishedComments;
  }

  public List<ChangeMessage> messages()
      throws OrmException {
    if (messages == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      messages = cmUtil.byChange(db, notes());
    }
    return messages;
  }

  public void setSubmitRecords(List<SubmitRecord> records) {
    submitRecords = records;
  }

  public List<SubmitRecord> getSubmitRecords() {
    return submitRecords;
  }

  public SubmitTypeRecord submitTypeRecord() throws OrmException {
    if (submitTypeRecord == null) {
      submitTypeRecord = new SubmitRuleEvaluator(this).getSubmitType();
    }
    return submitTypeRecord;
  }

  public void setMergeable(Boolean mergeable) {
    this.mergeable = mergeable;
  }

  public Boolean isMergeable() throws OrmException {
    if (mergeable == null) {
      Change c = change();
      if (c == null) {
        return null;
      }
      if (c.getStatus() == Change.Status.MERGED) {
        mergeable = true;
      } else {
        if (!lazyLoad) {
          return null;
        }
        PatchSet ps = currentPatchSet();
        try {
          if (ps == null || !changeControl().isPatchVisible(ps, db)) {
            return null;
          }
        } catch (OrmException e) {
          if (e.getCause() instanceof NoSuchChangeException) {
            return null;
          }
          throw e;
        }

        try (Repository repo = repoManager.openRepository(project())) {
          Ref ref = repo.getRefDatabase().exactRef(c.getDest().get());
          SubmitTypeRecord str = submitTypeRecord();
          if (!str.isOk()) {
            // If submit type rules are broken, it's definitely not mergeable.
            // No need to log, as SubmitRuleEvaluator already did it for us.
            return false;
          }
          String mergeStrategy = mergeUtilFactory
              .create(projectCache.get(project()))
              .mergeStrategyName();
          mergeable = mergeabilityCache.get(
              ObjectId.fromString(ps.getRevision().get()),
              ref, str.type, mergeStrategy, c.getDest(), repo);
        } catch (IOException e) {
          throw new OrmException(e);
        }
      }
    }
    return mergeable;
  }

  public Set<Account.Id> editsByUser() throws OrmException {
    if (editsByUser == null) {
      if (!lazyLoad) {
        return Collections.emptySet();
      }
      Change c = change();
      if (c == null) {
        return Collections.emptySet();
      }
      editsByUser = new HashSet<>();
      Change.Id id = checkNotNull(change.getId());
      try (Repository repo = repoManager.openRepository(project())) {
        for (String ref
            : repo.getRefDatabase().getRefs(RefNames.REFS_USERS).keySet()) {
          if (id.equals(Change.Id.fromEditRefPart(ref))) {
            editsByUser.add(Account.Id.fromRefPart(ref));
          }
        }
      } catch (IOException e) {
        throw new OrmException(e);
      }
    }
    return editsByUser;
  }

  public Set<Account.Id> draftsByUser() throws OrmException {
    if (draftsByUser == null) {
      if (!lazyLoad) {
        return Collections.emptySet();
      }
      Change c = change();
      if (c == null) {
        return Collections.emptySet();
      }
      draftsByUser = new HashSet<>();
      for (Comment sc : commentsUtil.draftByChange(db, notes)) {
        draftsByUser.add(sc.author.getId());
      }
    }
    return draftsByUser;
  }

  public Set<Account.Id> reviewedBy() throws OrmException {
    if (reviewedBy == null) {
      if (!lazyLoad) {
        return Collections.emptySet();
      }
      Change c = change();
      if (c == null) {
        return Collections.emptySet();
      }
      List<ReviewedByEvent> events = new ArrayList<>();
      for (ChangeMessage msg : messages()) {
        if (msg.getAuthor() != null) {
          events.add(ReviewedByEvent.create(msg));
        }
      }
      events = Lists.reverse(events);
      reviewedBy = new LinkedHashSet<>();
      Account.Id owner = c.getOwner();
      for (ReviewedByEvent event : events) {
        if (owner.equals(event.author())) {
          break;
        }
        reviewedBy.add(event.author());
      }
    }
    return reviewedBy;
  }

  public void setReviewedBy(Set<Account.Id> reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public Account.Id assignee() throws OrmException {
    if (assignee == null) {
      if (!lazyLoad) {
        return null;
      }
      assignee = notes().getAssignee();
    }
    return assignee;
  }

  public Set<String> hashtags() throws OrmException {
    if (hashtags == null) {
      if (!lazyLoad) {
        return Collections.emptySet();
      }
      hashtags = notes().getHashtags();
    }
    return hashtags;
  }

  public void setHashtags(Set<String> hashtags) {
    this.hashtags = hashtags;
  }

  @Deprecated
  public Set<Account.Id> starredBy() throws OrmException {
    if (starredByUser == null) {
      if (!lazyLoad) {
        return Collections.emptySet();
      }
      starredByUser = checkNotNull(starredChangesUtil).byChange(
          legacyId, StarredChangesUtil.DEFAULT_LABEL);
    }
    return starredByUser;
  }

  @Deprecated
  public void setStarredBy(Set<Account.Id> starredByUser) {
    this.starredByUser = starredByUser;
  }

  public ImmutableMultimap<Account.Id, String> stars() throws OrmException {
    if (stars == null) {
      if (!lazyLoad) {
        return ImmutableMultimap.of();
      }
      stars = checkNotNull(starredChangesUtil).byChange(legacyId);
    }
    return stars;
  }

  public void setStars(Multimap<Account.Id, String> stars) {
    this.stars = ImmutableMultimap.copyOf(stars);
  }

  @AutoValue
  abstract static class ReviewedByEvent {
    private static ReviewedByEvent create(ChangeMessage msg) {
      return new AutoValue_ChangeData_ReviewedByEvent(
          msg.getAuthor(), msg.getWrittenOn());
    }

    public abstract Account.Id author();
    public abstract Timestamp ts();
  }

  @Override
  public String toString() {
    MoreObjects.ToStringHelper h = MoreObjects.toStringHelper(this);
    if (change != null) {
      h.addValue(change);
    } else {
      h.addValue(legacyId);
    }
    return h.toString();
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
