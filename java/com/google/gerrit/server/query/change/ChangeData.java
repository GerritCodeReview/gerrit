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
import static com.google.gerrit.server.ApprovalsUtil.sortApprovals;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.StarredChangesUtil.StarRef;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.change.PureRevert;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
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

public class ChangeData {
  private static final int BATCH_SIZE = 50;

  public static List<Change> asChanges(List<ChangeData> changeDatas) throws OrmException {
    List<Change> result = new ArrayList<>(changeDatas.size());
    for (ChangeData cd : changeDatas) {
      result.add(cd.change());
    }
    return result;
  }

  public static Map<Change.Id, ChangeData> asMap(List<ChangeData> changes) {
    return changes.stream().collect(toMap(ChangeData::getId, cd -> cd));
  }

  public static void ensureChangeLoaded(Iterable<ChangeData> changes) throws OrmException {
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
    for (ChangeNotes notes : first.notesFactory.create(first.db, missing.keySet())) {
      missing.get(notes.getChangeId()).change = notes.getChange();
    }
  }

  public static void ensureAllPatchSetsLoaded(Iterable<ChangeData> changes) throws OrmException {
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

  public static void ensureCurrentPatchSetLoaded(Iterable<ChangeData> changes) throws OrmException {
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

  public static void ensureMessagesLoaded(Iterable<ChangeData> changes) throws OrmException {
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

  public static void ensureReviewedByLoadedForOpenChanges(Iterable<ChangeData> changes)
      throws OrmException {
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

  public static class Factory {
    private final AssistedFactory assistedFactory;

    @Inject
    Factory(AssistedFactory assistedFactory) {
      this.assistedFactory = assistedFactory;
    }

    public ChangeData create(ReviewDb db, Project.NameKey project, Change.Id id) {
      return assistedFactory.create(db, project, id, null, null);
    }

    public ChangeData create(ReviewDb db, Change change) {
      return assistedFactory.create(db, change.getProject(), change.getId(), change, null);
    }

    public ChangeData create(ReviewDb db, ChangeNotes notes) {
      return assistedFactory.create(
          db, notes.getChange().getProject(), notes.getChangeId(), notes.getChange(), notes);
    }
  }

  public interface AssistedFactory {
    ChangeData create(
        ReviewDb db,
        Project.NameKey project,
        Change.Id id,
        @Nullable Change change,
        @Nullable ChangeNotes notes);
  }

  /**
   * Create an instance for testing only.
   *
   * <p>Attempting to lazy load data will fail with NPEs. Callers may consider manually setting
   * fields that can be set.
   *
   * @param id change ID
   * @return instance for testing.
   */
  public static ChangeData createForTest(
      Project.NameKey project, Change.Id id, int currentPatchSetId) {
    ChangeData cd =
        new ChangeData(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, project, id, null, null);
    cd.currentPatchSet = new PatchSet(new PatchSet.Id(id, currentPatchSetId));
    return cd;
  }

  // Injected fields.
  private @Nullable final StarredChangesUtil starredChangesUtil;
  private final AllUsersName allUsersName;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeNotes.Factory notesFactory;
  private final CommentsUtil commentsUtil;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser.GenericFactory userFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final MergeabilityCache mergeabilityCache;
  private final NotesMigration notesMigration;
  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final ProjectCache projectCache;
  private final TrackingFooters trackingFooters;
  private final PureRevert pureRevert;
  private final SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory;

  // Required assisted injected fields.
  private final ReviewDb db;
  private final Project.NameKey project;
  private final Change.Id legacyId;

  // Lazily populated fields, including optional assisted injected fields.

  private final Map<SubmitRuleOptions, List<SubmitRecord>> submitRecords =
      Maps.newLinkedHashMapWithExpectedSize(1);

  private boolean lazyLoad = true;
  private Change change;
  private ChangeNotes notes;
  private String commitMessage;
  private List<FooterLine> commitFooters;
  private PatchSet currentPatchSet;
  private Collection<PatchSet> patchSets;
  private ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals;
  private List<PatchSetApproval> currentApprovals;
  private List<String> currentFiles;
  private Optional<DiffSummary> diffSummary;
  private Collection<Comment> publishedComments;
  private Collection<RobotComment> robotComments;
  private CurrentUser visibleTo;
  private List<ChangeMessage> messages;
  private Optional<ChangedLines> changedLines;
  private SubmitTypeRecord submitTypeRecord;
  private Boolean mergeable;
  private Set<String> hashtags;
  private Map<Account.Id, Ref> editsByUser;
  private Set<Account.Id> reviewedBy;
  private Map<Account.Id, Ref> draftsByUser;
  private ImmutableListMultimap<Account.Id, String> stars;
  private StarsOf starsOf;
  private ImmutableMap<Account.Id, StarRef> starRefs;
  private ReviewerSet reviewers;
  private ReviewerByEmailSet reviewersByEmail;
  private ReviewerSet pendingReviewers;
  private ReviewerByEmailSet pendingReviewersByEmail;
  private List<ReviewerStatusUpdate> reviewerUpdates;
  private PersonIdent author;
  private PersonIdent committer;
  private int parentCount;
  private Integer unresolvedCommentCount;
  private LabelTypes labelTypes;

  private ImmutableList<byte[]> refStates;
  private ImmutableList<byte[]> refStatePatterns;

  @Inject
  private ChangeData(
      @Nullable StarredChangesUtil starredChangesUtil,
      ApprovalsUtil approvalsUtil,
      AllUsersName allUsersName,
      ChangeMessagesUtil cmUtil,
      ChangeNotes.Factory notesFactory,
      CommentsUtil commentsUtil,
      GitRepositoryManager repoManager,
      IdentifiedUser.GenericFactory userFactory,
      MergeUtil.Factory mergeUtilFactory,
      MergeabilityCache mergeabilityCache,
      NotesMigration notesMigration,
      PatchListCache patchListCache,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      TrackingFooters trackingFooters,
      PureRevert pureRevert,
      SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory,
      @Assisted ReviewDb db,
      @Assisted Project.NameKey project,
      @Assisted Change.Id id,
      @Assisted @Nullable Change change,
      @Assisted @Nullable ChangeNotes notes) {
    this.approvalsUtil = approvalsUtil;
    this.allUsersName = allUsersName;
    this.cmUtil = cmUtil;
    this.notesFactory = notesFactory;
    this.commentsUtil = commentsUtil;
    this.repoManager = repoManager;
    this.userFactory = userFactory;
    this.mergeUtilFactory = mergeUtilFactory;
    this.mergeabilityCache = mergeabilityCache;
    this.notesMigration = notesMigration;
    this.patchListCache = patchListCache;
    this.psUtil = psUtil;
    this.projectCache = projectCache;
    this.starredChangesUtil = starredChangesUtil;
    this.trackingFooters = trackingFooters;
    this.pureRevert = pureRevert;
    this.submitRuleEvaluatorFactory = submitRuleEvaluatorFactory;

    // May be null in tests when created via createForTest above, in which case lazy-loading will
    // intentionally fail with NPE. Still not marked @Nullable in the constructor, to force callers
    // using Guice to pass a non-null value.
    this.db = db;

    this.project = project;
    this.legacyId = id;

    this.change = change;
    this.notes = notes;
  }

  /**
   * If false, omit fields that require database/repo IO.
   *
   * <p>This is used to enforce that the dashboard is rendered from the index only. If {@code
   * lazyLoad} is on, the {@code ChangeData} object will load from the database ("lazily") when a
   * field accessor is called.
   */
  public ChangeData setLazyLoad(boolean load) {
    lazyLoad = load;
    return this;
  }

  public ReviewDb db() {
    return db;
  }

  public AllUsersName getAllUsersNameForIndexing() {
    return allUsersName;
  }

  public void setCurrentFilePaths(List<String> filePaths) throws OrmException {
    PatchSet ps = currentPatchSet();
    if (ps != null) {
      currentFiles = ImmutableList.copyOf(filePaths);
    }
  }

  public List<String> currentFilePaths() throws IOException, OrmException {
    if (currentFiles == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      Optional<DiffSummary> p = getDiffSummary();
      currentFiles = p.map(DiffSummary::getPaths).orElse(Collections.emptyList());
    }
    return currentFiles;
  }

  private Optional<DiffSummary> getDiffSummary() throws OrmException, IOException {
    if (diffSummary == null) {
      if (!lazyLoad) {
        return Optional.empty();
      }

      Change c = change();
      PatchSet ps = currentPatchSet();
      if (c == null || ps == null || !loadCommitData()) {
        return Optional.empty();
      }

      ObjectId id = ObjectId.fromString(ps.getRevision().get());
      Whitespace ws = Whitespace.IGNORE_NONE;
      PatchListKey pk =
          parentCount > 1
              ? PatchListKey.againstParentNum(1, id, ws)
              : PatchListKey.againstDefaultBase(id, ws);
      DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
      try {
        diffSummary = Optional.of(patchListCache.getDiffSummary(key, c.getProject()));
      } catch (PatchListNotAvailableException e) {
        diffSummary = Optional.empty();
      }
    }
    return diffSummary;
  }

  private Optional<ChangedLines> computeChangedLines() throws OrmException, IOException {
    Optional<DiffSummary> ds = getDiffSummary();
    if (ds.isPresent()) {
      return Optional.of(ds.get().getChangedLines());
    }
    return Optional.empty();
  }

  public Optional<ChangedLines> changedLines() throws OrmException, IOException {
    if (changedLines == null) {
      if (!lazyLoad) {
        return Optional.empty();
      }
      changedLines = computeChangedLines();
    }
    return changedLines;
  }

  public void setChangedLines(int insertions, int deletions) {
    changedLines = Optional.of(new ChangedLines(insertions, deletions));
  }

  public void setNoChangedLines() {
    changedLines = Optional.empty();
  }

  public Change.Id getId() {
    return legacyId;
  }

  public Project.NameKey project() {
    return project;
  }

  boolean fastIsVisibleTo(CurrentUser user) {
    return visibleTo == user;
  }

  void cacheVisibleTo(CurrentUser user) {
    visibleTo = user;
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
    try {
      notes = notesFactory.createChecked(db, project, legacyId);
    } catch (NoSuchChangeException e) {
      throw new OrmException("Unable to load change " + legacyId, e);
    }
    change = notes.getChange();
    setPatchSets(null);
    return change;
  }

  public LabelTypes getLabelTypes() throws OrmException {
    if (labelTypes == null) {
      ProjectState state;
      try {
        state = projectCache.checkedGet(project());
      } catch (IOException e) {
        throw new OrmException("project state not available", e);
      }
      labelTypes = state.getLabelTypes(change().getDest(), userFactory.create(change().getOwner()));
    }
    return labelTypes;
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

  public List<PatchSetApproval> currentApprovals() throws OrmException {
    if (currentApprovals == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      Change c = change();
      if (c == null) {
        currentApprovals = Collections.emptyList();
      } else {
        try {
          currentApprovals =
              ImmutableList.copyOf(
                  approvalsUtil.byPatchSet(
                      db,
                      notes(),
                      userFactory.create(c.getOwner()),
                      c.currentPatchSetId(),
                      null,
                      null));
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

  public ListMultimap<String, String> trackingFooters() throws IOException, OrmException {
    return trackingFooters.extract(commitFooters());
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

  private boolean loadCommitData()
      throws OrmException, RepositoryNotFoundException, IOException, MissingObjectException,
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
      parentCount = c.getParentCount();
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
   * @return all patch set approvals for the change, keyed by ID, ordered by timestamp within each
   *     patch set.
   * @throws OrmException an error occurred reading the database.
   */
  public ListMultimap<PatchSet.Id, PatchSetApproval> approvals() throws OrmException {
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
  public Optional<PatchSetApproval> getSubmitApproval() throws OrmException {
    return currentApprovals().stream().filter(PatchSetApproval::isLegacySubmit).findFirst();
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

  public ReviewerByEmailSet reviewersByEmail() throws OrmException {
    if (reviewersByEmail == null) {
      if (!lazyLoad) {
        return ReviewerByEmailSet.empty();
      }
      reviewersByEmail = notes().getReviewersByEmail();
    }
    return reviewersByEmail;
  }

  public void setReviewersByEmail(ReviewerByEmailSet reviewersByEmail) {
    this.reviewersByEmail = reviewersByEmail;
  }

  public ReviewerByEmailSet getReviewersByEmail() {
    return reviewersByEmail;
  }

  public void setPendingReviewers(ReviewerSet pendingReviewers) {
    this.pendingReviewers = pendingReviewers;
  }

  public ReviewerSet getPendingReviewers() {
    return this.pendingReviewers;
  }

  public ReviewerSet pendingReviewers() throws OrmException {
    if (pendingReviewers == null) {
      if (!lazyLoad) {
        return ReviewerSet.empty();
      }
      pendingReviewers = notes().getPendingReviewers();
    }
    return pendingReviewers;
  }

  public void setPendingReviewersByEmail(ReviewerByEmailSet pendingReviewersByEmail) {
    this.pendingReviewersByEmail = pendingReviewersByEmail;
  }

  public ReviewerByEmailSet getPendingReviewersByEmail() {
    return pendingReviewersByEmail;
  }

  public ReviewerByEmailSet pendingReviewersByEmail() throws OrmException {
    if (pendingReviewersByEmail == null) {
      if (!lazyLoad) {
        return ReviewerByEmailSet.empty();
      }
      pendingReviewersByEmail = notes().getPendingReviewersByEmail();
    }
    return pendingReviewersByEmail;
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

  public Collection<Comment> publishedComments() throws OrmException {
    if (publishedComments == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      publishedComments = commentsUtil.publishedByChange(db, notes());
    }
    return publishedComments;
  }

  public Collection<RobotComment> robotComments() throws OrmException {
    if (robotComments == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      robotComments = commentsUtil.robotCommentsByChange(notes());
    }
    return robotComments;
  }

  public Integer unresolvedCommentCount() throws OrmException {
    if (unresolvedCommentCount == null) {
      if (!lazyLoad) {
        return null;
      }

      List<Comment> comments =
          Stream.concat(publishedComments().stream(), robotComments().stream()).collect(toList());

      // Build a map of uuid to list of direct descendants.
      Map<String, List<Comment>> forest = new HashMap<>();
      for (Comment comment : comments) {
        List<Comment> siblings = forest.get(comment.parentUuid);
        if (siblings == null) {
          siblings = new ArrayList<>();
          forest.put(comment.parentUuid, siblings);
        }
        siblings.add(comment);
      }

      // Find latest comment in each thread and apply to unresolved counter.
      int unresolved = 0;
      if (forest.containsKey(null)) {
        for (Comment root : forest.get(null)) {
          if (getLatestComment(forest, root).unresolved) {
            unresolved++;
          }
        }
      }
      unresolvedCommentCount = unresolved;
    }

    return unresolvedCommentCount;
  }

  protected Comment getLatestComment(Map<String, List<Comment>> forest, Comment root) {
    List<Comment> children = forest.get(root.key.uuid);
    if (children == null) {
      return root;
    }
    Comment latest = null;
    for (Comment comment : children) {
      Comment branchLatest = getLatestComment(forest, comment);
      if (latest == null || branchLatest.writtenOn.after(latest.writtenOn)) {
        latest = branchLatest;
      }
    }
    return latest;
  }

  public void setUnresolvedCommentCount(Integer count) {
    this.unresolvedCommentCount = count;
  }

  public List<ChangeMessage> messages() throws OrmException {
    if (messages == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      messages = cmUtil.byChange(db, notes());
    }
    return messages;
  }

  public List<SubmitRecord> submitRecords(SubmitRuleOptions options) {
    List<SubmitRecord> records = submitRecords.get(options);
    if (records == null) {
      if (!lazyLoad) {
        return Collections.emptyList();
      }
      records = submitRuleEvaluatorFactory.create(options).evaluate(this);
      submitRecords.put(options, records);
    }
    return records;
  }

  @Nullable
  public List<SubmitRecord> getSubmitRecords(SubmitRuleOptions options) {
    return submitRecords.get(options);
  }

  public void setSubmitRecords(SubmitRuleOptions options, List<SubmitRecord> records) {
    submitRecords.put(options, records);
  }

  public SubmitTypeRecord submitTypeRecord() {
    if (submitTypeRecord == null) {
      submitTypeRecord =
          submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults()).getSubmitType(this);
    }
    return submitTypeRecord;
  }

  public void setMergeable(Boolean mergeable) {
    this.mergeable = mergeable;
  }

  @Nullable
  public Boolean isMergeable() throws OrmException {
    if (mergeable == null) {
      Change c = change();
      if (c == null) {
        return null;
      }
      if (c.getStatus() == Change.Status.MERGED) {
        mergeable = true;
      } else if (c.getStatus() == Change.Status.ABANDONED) {
        return null;
      } else if (c.isWorkInProgress()) {
        return null;
      } else {
        if (!lazyLoad) {
          return null;
        }
        PatchSet ps = currentPatchSet();
        if (ps == null) {
          return null;
        }

        try (Repository repo = repoManager.openRepository(project())) {
          Ref ref = repo.getRefDatabase().exactRef(c.getDest().get());
          SubmitTypeRecord str = submitTypeRecord();
          if (!str.isOk()) {
            // If submit type rules are broken, it's definitely not mergeable.
            // No need to log, as SubmitRuleEvaluator already did it for us.
            return false;
          }
          String mergeStrategy =
              mergeUtilFactory.create(projectCache.get(project())).mergeStrategyName();
          mergeable =
              mergeabilityCache.get(
                  ObjectId.fromString(ps.getRevision().get()),
                  ref,
                  str.type,
                  mergeStrategy,
                  c.getDest(),
                  repo);
        } catch (IOException e) {
          throw new OrmException(e);
        }
      }
    }
    return mergeable;
  }

  public Set<Account.Id> editsByUser() throws OrmException {
    return editRefs().keySet();
  }

  public Map<Account.Id, Ref> editRefs() throws OrmException {
    if (editsByUser == null) {
      if (!lazyLoad) {
        return Collections.emptyMap();
      }
      Change c = change();
      if (c == null) {
        return Collections.emptyMap();
      }
      editsByUser = new HashMap<>();
      Change.Id id = checkNotNull(change.getId());
      try (Repository repo = repoManager.openRepository(project())) {
        for (Map.Entry<String, Ref> e :
            repo.getRefDatabase().getRefs(RefNames.REFS_USERS).entrySet()) {
          if (id.equals(Change.Id.fromEditRefPart(e.getKey()))) {
            Account.Id accountId = Account.Id.fromRefPart(e.getKey());
            if (accountId != null) {
              editsByUser.put(accountId, e.getValue());
            }
          }
        }
      } catch (IOException e) {
        throw new OrmException(e);
      }
    }
    return editsByUser;
  }

  public Set<Account.Id> draftsByUser() throws OrmException {
    return draftRefs().keySet();
  }

  public Map<Account.Id, Ref> draftRefs() throws OrmException {
    if (draftsByUser == null) {
      if (!lazyLoad) {
        return Collections.emptyMap();
      }
      Change c = change();
      if (c == null) {
        return Collections.emptyMap();
      }

      draftsByUser = new HashMap<>();
      if (notesMigration.readChanges()) {
        for (Ref ref : commentsUtil.getDraftRefs(notes.getChangeId())) {
          Account.Id account = Account.Id.fromRefSuffix(ref.getName());
          if (account != null
              // Double-check that any drafts exist for this user after
              // filtering out zombies. If some but not all drafts in the ref
              // were zombies, the returned Ref still includes those zombies;
              // this is suboptimal, but is ok for the purposes of
              // draftsByUser(), and easier than trying to rebuild the change at
              // this point.
              && !notes().getDraftComments(account, ref).isEmpty()) {
            draftsByUser.put(account, ref);
          }
        }
      } else {
        for (Comment sc : commentsUtil.draftByChange(db, notes())) {
          draftsByUser.put(sc.author.getId(), null);
        }
      }
    }
    return draftsByUser;
  }

  public boolean isReviewedBy(Account.Id accountId) throws OrmException {
    Collection<String> stars = stars(accountId);

    if (stars.contains(
        StarredChangesUtil.REVIEWED_LABEL + "/" + currentPatchSet().getPatchSetId())) {
      return true;
    }

    if (stars.contains(
        StarredChangesUtil.UNREVIEWED_LABEL + "/" + currentPatchSet().getPatchSetId())) {
      return false;
    }

    return reviewedBy().contains(accountId);
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

  public ImmutableListMultimap<Account.Id, String> stars() throws OrmException {
    if (stars == null) {
      if (!lazyLoad) {
        return ImmutableListMultimap.of();
      }
      ImmutableListMultimap.Builder<Account.Id, String> b = ImmutableListMultimap.builder();
      for (Map.Entry<Account.Id, StarRef> e : starRefs().entrySet()) {
        b.putAll(e.getKey(), e.getValue().labels());
      }
      return b.build();
    }
    return stars;
  }

  public void setStars(ListMultimap<Account.Id, String> stars) {
    this.stars = ImmutableListMultimap.copyOf(stars);
  }

  public ImmutableMap<Account.Id, StarRef> starRefs() throws OrmException {
    if (starRefs == null) {
      if (!lazyLoad) {
        return ImmutableMap.of();
      }
      starRefs = checkNotNull(starredChangesUtil).byChange(legacyId);
    }
    return starRefs;
  }

  public Set<String> stars(Account.Id accountId) throws OrmException {
    if (starsOf != null) {
      if (!starsOf.accountId().equals(accountId)) {
        starsOf = null;
      }
    }
    if (starsOf == null) {
      if (stars != null) {
        starsOf = StarsOf.create(accountId, stars.get(accountId));
      } else {
        if (!lazyLoad) {
          return ImmutableSet.of();
        }
        starsOf = StarsOf.create(accountId, starredChangesUtil.getLabels(accountId, legacyId));
      }
    }
    return starsOf.stars();
  }

  /**
   * @return {@code null} if {@code revertOf} is {@code null}; true if the change is a pure revert;
   *     false otherwise.
   */
  @Nullable
  public Boolean isPureRevert() throws OrmException {
    if (change().getRevertOf() == null) {
      return null;
    }
    try {
      return pureRevert.get(notes(), null).isPureRevert;
    } catch (IOException | BadRequestException | ResourceConflictException e) {
      throw new OrmException("could not compute pure revert", e);
    }
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

    public ChangedLines(int insertions, int deletions) {
      this.insertions = insertions;
      this.deletions = deletions;
    }
  }

  public ImmutableList<byte[]> getRefStates() {
    return refStates;
  }

  public void setRefStates(Iterable<byte[]> refStates) {
    this.refStates = ImmutableList.copyOf(refStates);
  }

  public ImmutableList<byte[]> getRefStatePatterns() {
    return refStatePatterns;
  }

  public void setRefStatePatterns(Iterable<byte[]> refStatePatterns) {
    this.refStatePatterns = ImmutableList.copyOf(refStatePatterns);
  }

  @AutoValue
  abstract static class ReviewedByEvent {
    private static ReviewedByEvent create(ChangeMessage msg) {
      return new AutoValue_ChangeData_ReviewedByEvent(msg.getAuthor(), msg.getWrittenOn());
    }

    public abstract Account.Id author();

    public abstract Timestamp ts();
  }

  @AutoValue
  abstract static class StarsOf {
    private static StarsOf create(Account.Id accountId, Iterable<String> stars) {
      return new AutoValue_ChangeData_StarsOf(accountId, ImmutableSortedSet.copyOf(stars));
    }

    public abstract Account.Id accountId();

    public abstract ImmutableSortedSet<String> stars();
  }
}
