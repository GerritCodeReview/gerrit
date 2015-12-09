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

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
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
    }

    Map<Change.Id, ChangeData> missing = Maps.newHashMap();
    for (ChangeData cd : changes) {
      if (cd.change == null) {
        missing.put(cd.getId(), cd);
      }
    }
    if (missing.isEmpty()) {
      return;
    }
    for (Change change : first.db.changes().get(missing.keySet())) {
      missing.get(change.getId()).change = change;
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
    }

    Map<PatchSet.Id, ChangeData> missing = Maps.newHashMap();
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
    ChangeData create(ReviewDb db, Change.Id id);
    ChangeData create(ReviewDb db, Change c);
    ChangeData create(ReviewDb db, ChangeControl c);
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
  public static ChangeData createForTest(Change.Id id, int currentPatchSetId) {
    ChangeData cd = new ChangeData(null, null, null, null, null, null, null,
        null, null, null, null, null, null, id);
    cd.currentPatchSet = new PatchSet(new PatchSet.Id(id, currentPatchSetId));
    return cd;
  }

  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ProjectCache projectCache;
  private final MergeUtil.Factory mergeUtilFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final PatchLineCommentsUtil plcUtil;
  private final PatchListCache patchListCache;
  private final NotesMigration notesMigration;
  private final MergeabilityCache mergeabilityCache;
  private final Change.Id legacyId;
  private ChangeDataSource returnedBySource;
  private Change change;
  private ChangeNotes notes;
  private String commitMessage;
  private List<FooterLine> commitFooters;
  private PatchSet currentPatchSet;
  private Collection<PatchSet> patchSets;
  private ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals;
  private List<PatchSetApproval> currentApprovals;
  private Map<Integer, List<String>> files = new HashMap<>();
  private Collection<PatchLineComment> publishedComments;
  private CurrentUser visibleTo;
  private ChangeControl changeControl;
  private List<ChangeMessage> messages;
  private List<SubmitRecord> submitRecords;
  private ChangedLines changedLines;
  private Boolean mergeable;
  private Set<Account.Id> editsByUser;
  private Set<Account.Id> reviewedBy;
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
      PatchLineCommentsUtil plcUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
      @Assisted ReviewDb db,
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
    this.plcUtil = plcUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    legacyId = id;
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
      PatchLineCommentsUtil plcUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
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
    this.plcUtil = plcUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    legacyId = c.getId();
    change = c;
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
      PatchLineCommentsUtil plcUtil,
      PatchListCache patchListCache,
      NotesMigration notesMigration,
      MergeabilityCache mergeabilityCache,
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
    this.plcUtil = plcUtil;
    this.patchListCache = patchListCache;
    this.notesMigration = notesMigration;
    this.mergeabilityCache = mergeabilityCache;
    legacyId = c.getId();
    change = c.getChange();
    changeControl = c;
    notes = c.getNotes();
  }

  public ReviewDb db() {
    return db;
  }

  public boolean isFromSource(ChangeDataSource s) {
    return s == returnedBySource;
  }

  public void cacheFromSource(ChangeDataSource s) {
    returnedBySource = s;
  }

  public void setCurrentFilePaths(List<String> filePaths) throws OrmException {
    PatchSet ps = currentPatchSet();
    if (ps != null) {
      files.put(ps.getPatchSetId(), ImmutableList.copyOf(filePaths));
    }
  }

  public List<String> currentFilePaths() throws OrmException {
    PatchSet ps = currentPatchSet();
    if (ps == null) {
      return null;
    }
    return filePaths(currentPatchSet);
  }

  public List<String> filePaths(PatchSet ps) throws OrmException {
    if (!files.containsKey(ps.getPatchSetId())) {
      Change c = change();
      if (c == null) {
        return null;
      }

      PatchList p;
      try {
        p = patchListCache.get(c, ps);
      } catch (PatchListNotAvailableException e) {
        List<String> emptyFileList = Collections.emptyList();
        files.put(ps.getPatchSetId(), emptyFileList);
        return emptyFileList;
      }

      List<String> r = new ArrayList<>(p.getPatches().size());
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
      files.put(ps.getPatchSetId(), Collections.unmodifiableList(r));
    }
    return files.get(ps.getPatchSetId());
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

  public void setChangedLines(int insertions, int deletions) {
    changedLines = new ChangedLines(insertions, deletions);
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

  public ChangeControl changeControl() throws OrmException {
    if (changeControl == null) {
      Change c = change();
      try {
        changeControl =
            changeControlFactory.controlFor(c, userFactory.create(c.getOwner()));
      } catch (NoSuchChangeException e) {
        throw new OrmException(e);
      }
    }
    return changeControl;
  }

  public ChangeControl changeControl(CurrentUser user) throws OrmException {
    if (changeControl != null) {
      throw new IllegalStateException(
          "user already specified: " + changeControl.getUser());
    }
    try {
      if (change != null) {
        changeControl = changeControlFactory.controlFor(change, user);
      } else {
        changeControl = changeControlFactory.controlFor(legacyId, user);
      }
    } catch (NoSuchChangeException e) {
      throw new OrmException(e);
    }
    return changeControl;
  }

  void cacheVisibleTo(ChangeControl ctl) {
    visibleTo = ctl.getUser();
    changeControl = ctl;
  }

  public Change change() throws OrmException {
    if (change == null) {
      reloadChange();
    }
    return change;
  }

  public void setChange(Change c) {
    change = c;
  }

  public Change reloadChange() throws OrmException {
    change = db.changes().get(legacyId);
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
      Change c = change();
      if (c == null) {
        currentApprovals = Collections.emptyList();
      } else {
        currentApprovals = ImmutableList.copyOf(approvalsUtil.byPatchSet(
            db, changeControl(), c.currentPatchSetId()));
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
    try (Repository repo = repoManager.openRepository(change().getProject());
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
   * @return patches for the change.
   * @throws OrmException an error occurred reading the database.
   */
  public Collection<PatchSet> patchSets()
      throws OrmException {
    if (patchSets == null) {
      patchSets = db.patchSets().byChange(legacyId).toList();
    }
    return patchSets;
  }

  public void setPatchSets(Collection<PatchSet> patchSets) {
    this.currentPatchSet = null;
    this.patchSets = patchSets;
  }

  /**
   * @return patch set with the given ID, or null if it does not exist.
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
      allApprovals = approvalsUtil.byChange(db, notes());
    }
    return allApprovals;
  }

  public SetMultimap<ReviewerStateInternal, Account.Id> reviewers()
      throws OrmException {
    return approvalsUtil.getReviewers(notes(), approvals().values());
  }

  public Collection<PatchLineComment> publishedComments()
      throws OrmException {
    if (publishedComments == null) {
      publishedComments = plcUtil.publishedByChange(db, notes());
    }
    return publishedComments;
  }

  public List<ChangeMessage> messages()
      throws OrmException {
    if (messages == null) {
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
        PatchSet ps = currentPatchSet();
        if (ps == null || !changeControl().isPatchVisible(ps, db)) {
          return null;
        }
        try (Repository repo = repoManager.openRepository(c.getProject())) {
          Ref ref = repo.getRefDatabase().exactRef(c.getDest().get());
          SubmitTypeRecord rec = new SubmitRuleEvaluator(this)
              .getSubmitType();
          if (rec.status != SubmitTypeRecord.Status.OK) {
            throw new OrmException(
                "Error in mergeability check: " + rec.errorMessage);
          }
          String mergeStrategy = mergeUtilFactory
              .create(projectCache.get(c.getProject()))
              .mergeStrategyName();
          mergeable = mergeabilityCache.get(
              ObjectId.fromString(ps.getRevision().get()),
              ref, rec.type, mergeStrategy, c.getDest(), repo);
        } catch (IOException e) {
          throw new OrmException(e);
        }
      }
    }
    return mergeable;
  }

  public Set<Account.Id> editsByUser() throws OrmException {
    if (editsByUser == null) {
      Change c = change();
      if (c == null) {
        return Collections.emptySet();
      }
      editsByUser = new HashSet<>();
      Change.Id id = change.getId();
      try (Repository repo = repoManager.openRepository(change.getProject())) {
        for (String ref
            : repo.getRefDatabase().getRefs(RefNames.REFS_USERS).keySet()) {
          if (Change.Id.fromEditRefPart(ref).equals(id)) {
            editsByUser.add(Account.Id.fromRefPart(ref));
          }
        }
      } catch (IOException e) {
        throw new OrmException(e);
      }
    }
    return editsByUser;
  }

  public Set<Account.Id> reviewedBy() throws OrmException {
    if (reviewedBy == null) {
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
