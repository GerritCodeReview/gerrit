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

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.index.RefState;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.StarredChangesUtil.StarRef;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.CommentThread;
import com.google.gerrit.server.change.CommentThreads;
import com.google.gerrit.server.change.MergeabilityCache;
import com.google.gerrit.server.change.PureRevert;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.RobotCommentNotes;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRequirementsAdapter;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.project.SubmitRequirementsUtil;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * ChangeData provides lazily loaded interface to change metadata loaded from NoteDb. It can be
 * constructed by loading from NoteDb, or calling setters. The latter happens when ChangeData is
 * retrieved through the change index. This happens for Applications that are performance sensitive
 * (eg. dashboard loads, git protocol negotiation) but can tolerate staleness. In that case, setting
 * lazyLoad=false disables loading from NoteDb, so we don't accidentally enable a slow path.
 */
public class ChangeData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public enum StorageConstraint {
    /**
     * This instance was loaded from the change index. Backfilling missing data from NoteDb is not
     * allowed.
     */
    INDEX_ONLY,
    /**
     * This instance was loaded from the change index. Backfilling missing data from NoteDb is
     * allowed.
     */
    INDEX_PRIMARY_NOTEDB_SECONDARY,
    /** This instance was loaded from NoteDb. */
    NOTEDB_ONLY
  }

  public static List<Change> asChanges(List<ChangeData> changeDatas) {
    List<Change> result = new ArrayList<>(changeDatas.size());
    for (ChangeData cd : changeDatas) {
      result.add(cd.change());
    }
    return result;
  }

  public static Map<Change.Id, ChangeData> asMap(List<ChangeData> changes) {
    return changes.stream().collect(toMap(ChangeData::getId, Function.identity()));
  }

  public static void ensureChangeLoaded(Iterable<ChangeData> changes) {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    }

    for (ChangeData cd : changes) {
      cd.change();
    }
  }

  public static void ensureAllPatchSetsLoaded(Iterable<ChangeData> changes) {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    }

    for (ChangeData cd : changes) {
      cd.patchSets();
    }
  }

  public static void ensureCurrentPatchSetLoaded(Iterable<ChangeData> changes) {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    }

    for (ChangeData cd : changes) {
      cd.currentPatchSet();
    }
  }

  public static void ensureCurrentApprovalsLoaded(Iterable<ChangeData> changes) {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    }

    for (ChangeData cd : changes) {
      cd.currentApprovals();
    }
  }

  public static void ensureMessagesLoaded(Iterable<ChangeData> changes) {
    ChangeData first = Iterables.getFirst(changes, null);
    if (first == null) {
      return;
    }

    for (ChangeData cd : changes) {
      cd.messages();
    }
  }

  public static void ensureReviewedByLoadedForOpenChanges(Iterable<ChangeData> changes) {
    List<ChangeData> pending = new ArrayList<>();
    for (ChangeData cd : changes) {
      if (cd.reviewedBy == null && cd.change().isNew()) {
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

    public ChangeData create(Project.NameKey project, Change.Id id) {
      return assistedFactory.create(project, id, null, null);
    }

    public ChangeData create(Change change) {
      return assistedFactory.create(change.getProject(), change.getId(), change, null);
    }

    public ChangeData create(ChangeNotes notes) {
      return assistedFactory.create(
          notes.getChange().getProject(), notes.getChangeId(), notes.getChange(), notes);
    }
  }

  public interface AssistedFactory {
    ChangeData create(
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
   * @param project project name
   * @param id change ID
   * @param currentPatchSetId current patchset number
   * @param commitId commit SHA1 of the current patchset
   * @return instance for testing.
   */
  public static ChangeData createForTest(
      Project.NameKey project, Change.Id id, int currentPatchSetId, ObjectId commitId) {
    return createForTest(project, id, currentPatchSetId, commitId, null, null, null);
  }

  /**
   * Create an instance for testing only.
   *
   * <p>Attempting to lazy load data will fail with NPEs. Callers may consider manually setting
   * fields that can be set.
   *
   * @param project project name
   * @param id change ID
   * @param currentPatchSetId current patchset number
   * @param commitId commit SHA1 of the current patchset
   * @param serverId Gerrit server id
   * @param virtualIdAlgo algorithm for virtualising the Change number
   * @param changeNotes notes associated with the Change
   * @return instance for testing.
   */
  public static ChangeData createForTest(
      Project.NameKey project,
      Change.Id id,
      int currentPatchSetId,
      ObjectId commitId,
      @Nullable String serverId,
      @Nullable ChangeNumberVirtualIdAlgorithm virtualIdAlgo,
      @Nullable ChangeNotes changeNotes) {
    ChangeData cd =
        new ChangeData(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            serverId,
            virtualIdAlgo,
            project,
            id,
            null,
            changeNotes);
    cd.currentPatchSet =
        PatchSet.builder()
            .id(PatchSet.id(id, currentPatchSetId))
            .commitId(commitId)
            .uploader(Account.id(1000))
            .realUploader(Account.id(1000))
            .createdOn(TimeUtil.now())
            .build();
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
  private final MergeUtilFactory mergeUtilFactory;
  private final MergeabilityCache mergeabilityCache;
  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final ProjectCache projectCache;
  private final TrackingFooters trackingFooters;
  private final PureRevert pureRevert;
  private final SubmitRequirementsEvaluator submitRequirementsEvaluator;
  private final SubmitRequirementsUtil submitRequirementsUtil;
  private final SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory;

  // Required assisted injected fields.
  private final Project.NameKey project;
  private final Change.Id legacyId;

  // Lazily populated fields, including optional assisted injected fields.

  private final Map<SubmitRuleOptions, List<SubmitRecord>> submitRecords =
      Maps.newLinkedHashMapWithExpectedSize(1);

  private Map<SubmitRequirement, SubmitRequirementResult> submitRequirements;

  private StorageConstraint storageConstraint = StorageConstraint.NOTEDB_ONLY;
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
  private Collection<HumanComment> publishedComments;
  private Collection<RobotComment> robotComments;
  private CurrentUser visibleTo;
  private List<ChangeMessage> messages;
  private Optional<ChangedLines> changedLines;
  private SubmitTypeRecord submitTypeRecord;
  private Boolean mergeable;
  private Set<String> hashtags;
  private ImmutableMap<String, String> customKeyedValues;
  /**
   * Map from {@link com.google.gerrit.entities.Account.Id} to the tip of the edit ref for this
   * change and a given user.
   */
  private Table<Account.Id, PatchSet.Id, Ref> editRefsByUser;

  private Set<Account.Id> reviewedBy;
  /**
   * Map from {@link com.google.gerrit.entities.Account.Id} to the tip of the draft comments ref for
   * this change and the user.
   */
  private Map<Account.Id, ObjectId> draftsByUser;

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
  private ImmutableSet<AttentionSetUpdate> attentionSet;
  private Integer parentCount;
  private Integer unresolvedCommentCount;
  private Integer totalCommentCount;
  private LabelTypes labelTypes;
  private Optional<Instant> mergedOn;
  private ImmutableSetMultimap<NameKey, RefState> refStates;
  private ImmutableList<byte[]> refStatePatterns;
  private String gerritServerId;
  private String changeServerId;
  private ChangeNumberVirtualIdAlgorithm virtualIdFunc;

  @Inject
  private ChangeData(
      @Nullable StarredChangesUtil starredChangesUtil,
      ApprovalsUtil approvalsUtil,
      AllUsersName allUsersName,
      ChangeMessagesUtil cmUtil,
      ChangeNotes.Factory notesFactory,
      CommentsUtil commentsUtil,
      GitRepositoryManager repoManager,
      MergeUtilFactory mergeUtilFactory,
      MergeabilityCache mergeabilityCache,
      PatchListCache patchListCache,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      TrackingFooters trackingFooters,
      PureRevert pureRevert,
      SubmitRequirementsEvaluator submitRequirementsEvaluator,
      SubmitRequirementsUtil submitRequirementsUtil,
      SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory,
      @GerritServerId String gerritServerId,
      ChangeNumberVirtualIdAlgorithm virtualIdFunc,
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
    this.mergeUtilFactory = mergeUtilFactory;
    this.mergeabilityCache = mergeabilityCache;
    this.patchListCache = patchListCache;
    this.psUtil = psUtil;
    this.projectCache = projectCache;
    this.starredChangesUtil = starredChangesUtil;
    this.trackingFooters = trackingFooters;
    this.pureRevert = pureRevert;
    this.submitRequirementsEvaluator = submitRequirementsEvaluator;
    this.submitRequirementsUtil = submitRequirementsUtil;
    this.submitRuleEvaluatorFactory = submitRuleEvaluatorFactory;

    this.project = project;
    this.legacyId = id;

    this.change = change;
    this.notes = notes;

    this.changeServerId = notes == null ? null : notes.getServerId();
    this.gerritServerId = gerritServerId;
    this.virtualIdFunc = virtualIdFunc;
  }

  /**
   * If false, omit fields that require database/repo IO.
   *
   * <p>This is used to enforce that the dashboard is rendered from the index only. If {@code
   * lazyLoad} is on, the {@code ChangeData} object will load from the database ("lazily") when a
   * field accessor is called.
   */
  public ChangeData setStorageConstraint(StorageConstraint storageConstraint) {
    this.storageConstraint = storageConstraint;
    return this;
  }

  public StorageConstraint getStorageConstraint() {
    return storageConstraint;
  }

  /** Returns {@code true} if we allow reading data from NoteDb. */
  public boolean lazyload() {
    return storageConstraint.ordinal()
        >= StorageConstraint.INDEX_PRIMARY_NOTEDB_SECONDARY.ordinal();
  }

  public AllUsersName getAllUsersNameForIndexing() {
    return allUsersName;
  }

  @VisibleForTesting
  public void setCurrentFilePaths(List<String> filePaths) {
    PatchSet ps = currentPatchSet();
    if (ps != null) {
      currentFiles = ImmutableList.copyOf(filePaths);
    }
  }

  public List<String> currentFilePaths() {
    if (currentFiles == null) {
      if (!lazyload()) {
        return Collections.emptyList();
      }
      Optional<DiffSummary> p = getDiffSummary();
      currentFiles = p.map(DiffSummary::getPaths).orElse(Collections.emptyList());
    }
    return currentFiles;
  }

  private Optional<DiffSummary> getDiffSummary() {
    if (diffSummary == null) {
      if (!lazyload()) {
        return Optional.empty();
      }

      Change c = change();
      PatchSet ps = currentPatchSet();
      if (c == null || ps == null || !loadCommitData()) {
        return Optional.empty();
      }

      PatchListKey pk = PatchListKey.againstBase(ps.commitId(), parentCount);
      DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
      try {
        diffSummary = Optional.of(patchListCache.getDiffSummary(key, c.getProject()));
      } catch (PatchListNotAvailableException e) {
        diffSummary = Optional.empty();
      }
    }
    return diffSummary;
  }

  private Optional<ChangedLines> computeChangedLines() {
    Optional<DiffSummary> ds = getDiffSummary();
    if (ds.isPresent()) {
      return Optional.of(ds.get().getChangedLines());
    }
    return Optional.empty();
  }

  public Optional<ChangedLines> changedLines() {
    if (changedLines == null) {
      if (!lazyload()) {
        return Optional.empty();
      }
      changedLines = computeChangedLines();
    }
    return changedLines;
  }

  public void setChangedLines(int insertions, int deletions) {
    changedLines = Optional.of(new ChangedLines(insertions, deletions));
  }

  public void setLinesInserted(int insertions) {
    changedLines =
        Optional.of(
            new ChangedLines(
                insertions,
                changedLines != null && changedLines.isPresent()
                    ? changedLines.get().deletions
                    : -1));
  }

  public void setLinesDeleted(int deletions) {
    changedLines =
        Optional.of(
            new ChangedLines(
                changedLines != null && changedLines.isPresent()
                    ? changedLines.get().insertions
                    : -1,
                deletions));
  }

  public void setNoChangedLines() {
    changedLines = Optional.empty();
  }

  public Change.Id getId() {
    return legacyId;
  }

  public Change.Id getVirtualId() {
    if (virtualIdFunc == null || changeServerId == null || changeServerId.equals(gerritServerId)) {
      return legacyId;
    }

    return Change.id(virtualIdFunc.apply(changeServerId, legacyId.get()));
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

  public Change change() {
    if (change == null && lazyload()) {
      reloadChange();
    }
    return change;
  }

  public void setChange(Change c) {
    change = c;
  }

  public Change reloadChange() {
    try {
      notes = notesFactory.createChecked(project, legacyId);
    } catch (NoSuchChangeException e) {
      throw new StorageException("Unable to load change " + legacyId, e);
    }
    change = notes.getChange();
    changeServerId = notes.getServerId();
    setPatchSets(null);
    return change;
  }

  public LabelTypes getLabelTypes() {
    if (labelTypes == null) {
      ProjectState state = projectCache.get(project()).orElseThrow(illegalState(project()));
      labelTypes = state.getLabelTypes(change().getDest());
    }
    return labelTypes;
  }

  public ChangeNotes notes() {
    if (notes == null) {
      if (!lazyload()) {
        throw new StorageException("ChangeNotes not available, lazyLoad = false");
      }
      notes = notesFactory.create(project(), legacyId);
    }
    return notes;
  }

  @Nullable
  public PatchSet currentPatchSet() {
    if (currentPatchSet == null) {
      Change c = change();
      if (c == null) {
        return null;
      }
      for (PatchSet p : patchSets()) {
        if (p.id().equals(c.currentPatchSetId())) {
          currentPatchSet = p;
          return p;
        }
      }
    }
    return currentPatchSet;
  }

  public List<PatchSetApproval> currentApprovals() {
    if (currentApprovals == null) {
      if (!lazyload()) {
        return Collections.emptyList();
      }
      Change c = change();
      if (c == null) {
        currentApprovals = Collections.emptyList();
      } else {
        try {
          currentApprovals =
              ImmutableList.copyOf(approvalsUtil.byPatchSet(notes(), c.currentPatchSetId()));
        } catch (StorageException e) {
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

  @Nullable
  public String commitMessage() {
    if (commitMessage == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return commitMessage;
  }

  /** Returns the list of commit footers (which may be empty). */
  public List<FooterLine> commitFooters() {
    if (commitFooters == null) {
      if (!loadCommitData()) {
        return ImmutableList.of();
      }
    }
    return commitFooters;
  }

  public ListMultimap<String, String> trackingFooters() {
    return trackingFooters.extract(commitFooters());
  }

  @Nullable
  public PersonIdent getAuthor() {
    if (author == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return author;
  }

  @Nullable
  public PersonIdent getCommitter() {
    if (committer == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return committer;
  }

  private boolean loadCommitData() {
    PatchSet ps = currentPatchSet();
    if (ps == null) {
      return false;
    }
    try (Repository repo = repoManager.openRepository(project());
        RevWalk walk = new RevWalk(repo)) {
      RevCommit c = walk.parseCommit(ps.commitId());
      commitMessage = c.getFullMessage();
      commitFooters = c.getFooterLines();
      author = c.getAuthorIdent();
      committer = c.getCommitterIdent();
      parentCount = c.getParentCount();
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Loading commit %s for ps %d of change %d failed.",
              ps.commitId(), ps.id().get(), ps.id().changeId().get()),
          e);
    }
    return true;
  }

  /** Returns the most recent update (i.e. status) per user. */
  public ImmutableSet<AttentionSetUpdate> attentionSet() {
    if (attentionSet == null) {
      if (!lazyload()) {
        return ImmutableSet.of();
      }
      attentionSet = notes().getAttentionSet();
    }
    return attentionSet;
  }

  /**
   * Returns the {@link Optional} value of time when the change was merged.
   *
   * <p>The value can be set from index field, see {@link ChangeData#setMergedOn} or loaded from the
   * database (available in {@link ChangeNotes})
   *
   * @return {@link Optional} value of time when the change was merged.
   * @throws StorageException if {@code lazyLoad} is off, {@link ChangeNotes} can not be loaded
   *     because we do not expect to call the database.
   */
  public Optional<Instant> getMergedOn() throws StorageException {
    if (mergedOn == null) {
      // The value was not loaded yet, try to get from the database.
      mergedOn = notes().getMergedOn();
    }
    return mergedOn;
  }

  /** Sets the value e.g. when loading from index. */
  public void setMergedOn(@Nullable Instant mergedOn) {
    this.mergedOn = Optional.ofNullable(mergedOn);
  }

  /**
   * Sets the specified attention set. If two or more entries refer to the same user, throws an
   * {@link IllegalStateException}.
   */
  public void setAttentionSet(ImmutableSet<AttentionSetUpdate> attentionSet) {
    if (attentionSet.stream().map(AttentionSetUpdate::account).distinct().count()
        != attentionSet.size()) {
      throw new IllegalStateException(
          String.format(
              "Stored attention set for change %d contains duplicate update",
              change.getId().get()));
    }
    this.attentionSet = attentionSet;
  }

  /** Returns patches for the change, in patch set ID order. */
  public Collection<PatchSet> patchSets() {
    if (patchSets == null) {
      patchSets = psUtil.byChange(notes());
    }
    return patchSets;
  }

  public void setPatchSets(Collection<PatchSet> patchSets) {
    this.currentPatchSet = null;
    this.patchSets = patchSets;
  }

  /** Returns patch with the given ID, or null if it does not exist. */
  @Nullable
  public PatchSet patchSet(PatchSet.Id psId) {
    if (currentPatchSet != null && currentPatchSet.id().equals(psId)) {
      return currentPatchSet;
    }
    for (PatchSet ps : patchSets()) {
      if (ps.id().equals(psId)) {
        return ps;
      }
    }
    return null;
  }

  /**
   * Returns all patch set approvals for the change, keyed by ID, ordered by timestamp within each
   * patch set.
   */
  public ListMultimap<PatchSet.Id, PatchSetApproval> approvals() {
    if (allApprovals == null) {
      if (!lazyload()) {
        return ImmutableListMultimap.of();
      }
      allApprovals = approvalsUtil.byChangeExcludingCopiedApprovals(notes());
    }
    return allApprovals;
  }

  /* @return legacy submit ('SUBM') approval label */
  // TODO(mariasavtchouk): Deprecate legacy submit label,
  // see com.google.gerrit.entities.LabelId.LEGACY_SUBMIT_NAME
  public Optional<PatchSetApproval> getSubmitApproval() {
    return currentApprovals().stream().filter(PatchSetApproval::isLegacySubmit).findFirst();
  }

  public ReviewerSet reviewers() {
    if (reviewers == null) {
      if (!lazyload()) {
        // We are not allowed to load values from NoteDb. Reviewers were not populated with values
        // from the index. However, we need these values for permission checks.
        throw new IllegalStateException("reviewers not populated");
      }
      reviewers = approvalsUtil.getReviewers(notes());
    }
    return reviewers;
  }

  public void setReviewers(ReviewerSet reviewers) {
    this.reviewers = reviewers;
  }

  public ReviewerByEmailSet reviewersByEmail() {
    if (reviewersByEmail == null) {
      if (!lazyload()) {
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

  public ReviewerSet pendingReviewers() {
    if (pendingReviewers == null) {
      if (!lazyload()) {
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

  public ReviewerByEmailSet pendingReviewersByEmail() {
    if (pendingReviewersByEmail == null) {
      if (!lazyload()) {
        return ReviewerByEmailSet.empty();
      }
      pendingReviewersByEmail = notes().getPendingReviewersByEmail();
    }
    return pendingReviewersByEmail;
  }

  public List<ReviewerStatusUpdate> reviewerUpdates() {
    if (reviewerUpdates == null) {
      if (!lazyload()) {
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

  public Collection<HumanComment> publishedComments() {
    if (publishedComments == null) {
      if (!lazyload()) {
        return Collections.emptyList();
      }
      publishedComments = commentsUtil.publishedHumanCommentsByChange(notes());
    }
    return publishedComments;
  }

  public Collection<RobotComment> robotComments() {
    if (robotComments == null) {
      if (!lazyload()) {
        return Collections.emptyList();
      }
      robotComments = commentsUtil.robotCommentsByChange(notes());
    }
    return robotComments;
  }

  @Nullable
  public Integer unresolvedCommentCount() {
    if (unresolvedCommentCount == null) {
      if (!lazyload()) {
        return null;
      }

      List<Comment> comments =
          Stream.concat(publishedComments().stream(), robotComments().stream()).collect(toList());

      ImmutableSet<CommentThread<Comment>> commentThreads =
          CommentThreads.forComments(comments).getThreads();
      unresolvedCommentCount =
          (int) commentThreads.stream().filter(CommentThread::unresolved).count();
    }

    return unresolvedCommentCount;
  }

  public void setUnresolvedCommentCount(Integer count) {
    this.unresolvedCommentCount = count;
  }

  @Nullable
  public Integer totalCommentCount() {
    if (totalCommentCount == null) {
      if (!lazyload()) {
        return null;
      }

      // Fail on overflow.
      totalCommentCount =
          Ints.checkedCast((long) publishedComments().size() + robotComments().size());
    }
    return totalCommentCount;
  }

  public void setTotalCommentCount(Integer count) {
    this.totalCommentCount = count;
  }

  public List<ChangeMessage> messages() {
    if (messages == null) {
      if (!lazyload()) {
        return Collections.emptyList();
      }
      messages = cmUtil.byChange(notes());
    }
    return messages;
  }

  /**
   * Similar to {@link #submitRequirements()}, except that it also converts submit records resulting
   * from the evaluation of legacy submit rules to submit requirements.
   */
  public Map<SubmitRequirement, SubmitRequirementResult> submitRequirementsIncludingLegacy() {
    Map<SubmitRequirement, SubmitRequirementResult> projectConfigReqs = submitRequirements();
    Map<SubmitRequirement, SubmitRequirementResult> legacyReqs =
        SubmitRequirementsAdapter.getLegacyRequirements(this);
    return submitRequirementsUtil.mergeLegacyAndNonLegacyRequirements(
        projectConfigReqs, legacyReqs, this);
  }

  /**
   * Get all evaluated submit requirements for this change, including those from parent projects.
   * For closed changes, submit requirements are read from the change notes. For active changes,
   * submit requirements are evaluated online.
   *
   * <p>For changes loaded from the index, the value will be set from index field {@link
   * com.google.gerrit.server.index.change.ChangeField#STORED_SUBMIT_REQUIREMENTS_FIELD}.
   */
  public Map<SubmitRequirement, SubmitRequirementResult> submitRequirements() {
    if (submitRequirements == null) {
      if (!lazyload()) {
        return Collections.emptyMap();
      }
      Change c = change();
      if (c == null || !c.isClosed()) {
        // Open changes: Evaluate submit requirements online.
        submitRequirements = submitRequirementsEvaluator.evaluateAllRequirements(this);
        return submitRequirements;
      }
      // Closed changes: Load submit requirement results from NoteDb.
      submitRequirements =
          notes().getSubmitRequirementsResult().stream()
              .filter(r -> !r.isLegacy())
              .collect(Collectors.toMap(r -> r.submitRequirement(), Function.identity()));
    }
    return submitRequirements;
  }

  public void setSubmitRequirements(
      Map<SubmitRequirement, SubmitRequirementResult> submitRequirements) {
    this.submitRequirements = submitRequirements;
  }

  public List<SubmitRecord> submitRecords(SubmitRuleOptions options) {
    // If the change is not submitted yet, 'strict' and 'lenient' both have the same result. If the
    // change is submitted, SubmitRecord requested with 'strict' will contain just a single entry
    // that with status=CLOSED. The latter is cheap to evaluate as we don't have to run any actual
    // evaluation.
    List<SubmitRecord> records = submitRecords.get(options);
    if (records == null) {
      if (storageConstraint != StorageConstraint.NOTEDB_ONLY) {
        // Submit requirements are expensive. We allow loading them only if this change did not
        // originate from the change index and we can invest the extra time.
        logger.atWarning().log(
            "Tried to load SubmitRecords for change fetched from index %s: %d",
            project(), getId().get());
        return Collections.emptyList();
      }
      records = submitRuleEvaluatorFactory.create(options).evaluate(this);
      submitRecords.put(options, records);
      if (!change().isClosed() && submitRecords.size() == 1) {
        // Cache the SubmitRecord with allowClosed = !allowClosed as the SubmitRecord are the same.
        submitRecords.put(
            options
                .toBuilder()
                .recomputeOnClosedChanges(!options.recomputeOnClosedChanges())
                .build(),
            records);
      }
    }
    return records;
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
  public Boolean isMergeable() {
    if (mergeable == null) {
      Change c = change();
      if (c == null) {
        return null;
      }
      if (c.isMerged()) {
        mergeable = true;
      } else if (c.isAbandoned()) {
        return null;
      } else if (c.isWorkInProgress()) {
        return null;
      } else {
        if (!lazyload()) {
          return null;
        }
        PatchSet ps = currentPatchSet();
        if (ps == null) {
          return null;
        }

        try (Repository repo = repoManager.openRepository(project())) {
          Ref ref = repo.getRefDatabase().exactRef(c.getDest().branch());
          SubmitTypeRecord str = submitTypeRecord();
          if (!str.isOk()) {
            // If submit type rules are broken, it's definitely not mergeable.
            // No need to log, as SubmitRuleEvaluator already did it for us.
            return false;
          }
          String mergeStrategy =
              mergeUtilFactory
                  .create(projectCache.get(project()).orElseThrow(illegalState(project())))
                  .mergeStrategyName();
          mergeable =
              mergeabilityCache.get(ps.commitId(), ref, str.type, mergeStrategy, c.getDest(), repo);
        } catch (IOException e) {
          throw new StorageException(e);
        }
      }
    }
    return mergeable;
  }

  @Nullable
  public Boolean isMerge() {
    if (parentCount == null) {
      if (!loadCommitData()) {
        return null;
      }
    }
    return parentCount > 1;
  }

  public Set<Account.Id> editsByUser() {
    return editRefs().rowKeySet();
  }

  public Table<Account.Id, PatchSet.Id, Ref> editRefs() {
    if (editRefsByUser == null) {
      if (!lazyload()) {
        return HashBasedTable.create();
      }
      Change c = change();
      if (c == null) {
        return HashBasedTable.create();
      }
      editRefsByUser = HashBasedTable.create();
      Change.Id id = requireNonNull(change.getId());
      try (Repository repo = repoManager.openRepository(project())) {
        for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_USERS)) {
          if (!RefNames.isRefsEdit(ref.getName())) {
            continue;
          }
          PatchSet.Id ps = PatchSet.Id.fromEditRef(ref.getName());
          if (id.equals(ps.changeId())) {
            Account.Id accountId = Account.Id.fromRef(ref.getName());
            if (accountId != null) {
              editRefsByUser.put(accountId, ps, ref);
            }
          }
        }
      } catch (IOException e) {
        throw new StorageException(e);
      }
    }
    return editRefsByUser;
  }

  public Set<Account.Id> draftsByUser() {
    return draftRefs().keySet();
  }

  public boolean isReviewedBy(Account.Id accountId) {
    return reviewedBy().contains(accountId);
  }

  public Set<Account.Id> reviewedBy() {
    if (reviewedBy == null) {
      if (!lazyload()) {
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

  public Set<String> hashtags() {
    if (hashtags == null) {
      if (!lazyload()) {
        return Collections.emptySet();
      }
      hashtags = notes().getHashtags();
    }
    return hashtags;
  }

  public void setHashtags(Set<String> hashtags) {
    this.hashtags = hashtags;
  }

  public Map<String, String> customKeyedValues() {
    if (customKeyedValues == null) {
      if (!lazyload()) {
        return Collections.emptyMap();
      }
      customKeyedValues = notes().getCustomKeyedValues();
    }
    return customKeyedValues;
  }

  public ImmutableListMultimap<Account.Id, String> stars() {
    if (stars == null) {
      if (!lazyload()) {
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

  private ImmutableMap<Account.Id, StarRef> starRefs() {
    if (starRefs == null) {
      if (!lazyload()) {
        return ImmutableMap.of();
      }
      starRefs = requireNonNull(starredChangesUtil).byChange(legacyId);
    }
    return starRefs;
  }

  public Set<String> stars(Account.Id accountId) {
    if (starsOf != null) {
      if (!starsOf.accountId().equals(accountId)) {
        starsOf = null;
      }
    }
    if (starsOf == null) {
      if (stars != null) {
        starsOf = StarsOf.create(accountId, stars.get(accountId));
      } else {
        if (!lazyload()) {
          return ImmutableSet.of();
        }
        starsOf = StarsOf.create(accountId, starredChangesUtil.getLabels(accountId, legacyId));
      }
    }
    return starsOf.stars();
  }

  /**
   * Returns {@code null} if {@code revertOf} is {@code null}; true if the change is a pure revert;
   * false otherwise.
   */
  @Nullable
  public Boolean isPureRevert() {
    if (change().getRevertOf() == null) {
      return null;
    }
    try {
      return pureRevert.get(notes(), Optional.empty());
    } catch (IOException | BadRequestException | ResourceConflictException e) {
      throw new StorageException("could not compute pure revert", e);
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

  public SetMultimap<NameKey, RefState> getRefStates() {
    if (refStates == null) {
      if (!lazyload()) {
        return ImmutableSetMultimap.of();
      }

      ImmutableSetMultimap.Builder<NameKey, RefState> result = ImmutableSetMultimap.builder();
      for (Table.Cell<Account.Id, PatchSet.Id, Ref> edit : editRefs().cellSet()) {
        result.put(
            project,
            RefState.create(
                RefNames.refsEdit(
                    edit.getRowKey(), edit.getColumnKey().changeId(), edit.getColumnKey()),
                edit.getValue().getObjectId()));
      }

      // TODO: instantiating the notes is too much. We don't want to parse NoteDb, we just want the
      // refs.
      result.put(project, RefState.create(notes().getRefName(), notes().getMetaId()));
      notes().getRobotComments(); // Force loading robot comments.
      RobotCommentNotes robotNotes = notes().getRobotCommentNotes();
      result.put(project, RefState.create(robotNotes.getRefName(), robotNotes.getMetaId()));

      refStates = result.build();
    }

    return refStates;
  }

  public void setRefStates(ImmutableSetMultimap<Project.NameKey, RefState> refStates) {
    this.refStates = refStates;
    if (draftsByUser == null) {
      // Recover draft refs as well. Draft comments are represented as refs in the repository.
      // ChangeData exposes #draftsByUser which just provides a Set of Account.Ids of users who
      // have drafts comments on this change. Recovering this list from RefStates makes it
      // available even on ChangeData instances retrieved from the index.
      draftsByUser = new HashMap<>();
      if (refStates.containsKey(allUsersName)) {
        refStates.get(allUsersName).stream()
            .filter(r -> RefNames.isRefsDraftsComments(r.ref()))
            .forEach(r -> draftsByUser.put(Account.Id.fromRef(r.ref()), r.id()));
      }
    }
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

    public abstract Instant ts();
  }

  @AutoValue
  abstract static class StarsOf {
    private static StarsOf create(Account.Id accountId, Iterable<String> stars) {
      return new AutoValue_ChangeData_StarsOf(accountId, ImmutableSortedSet.copyOf(stars));
    }

    public abstract Account.Id accountId();

    public abstract ImmutableSortedSet<String> stars();
  }

  private Map<Account.Id, ObjectId> draftRefs() {
    if (draftsByUser == null) {
      if (!lazyload()) {
        return Collections.emptyMap();
      }
      Change c = change();
      if (c == null) {
        return Collections.emptyMap();
      }

      draftsByUser = new HashMap<>();
      for (Ref ref : commentsUtil.getDraftRefs(notes().getChangeId())) {
        Account.Id account = Account.Id.fromRefSuffix(ref.getName());
        if (account != null
            // Double-check that any drafts exist for this user after
            // filtering out zombies. If some but not all drafts in the ref
            // were zombies, the returned Ref still includes those zombies;
            // this is suboptimal, but is ok for the purposes of
            // draftsByUser(), and easier than trying to rebuild the change at
            // this point.
            && !notes().getDraftComments(account, ref).isEmpty()) {
          draftsByUser.put(account, ref.getObjectId());
        }
      }
    }
    return draftsByUser;
  }
}
