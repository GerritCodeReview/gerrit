// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.FormatMethod;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetApprovals;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.git.RefCache;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** View of a single {@link Change} based on the log of its notes branch. */
// TODO(paiking): This class should be refactored to get rid of potentially duplicate or unneeded
// variables, such as allAttentionSetUpdates, reviewerUpdates, and others.

public class ChangeNotes extends AbstractChangeNotes<ChangeNotes> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final Ordering<PatchSetApproval> PSA_BY_TIME =
      Ordering.from(comparing(PatchSetApproval::granted));

  @FormatMethod
  public static ConfigInvalidException parseException(
      Change.Id changeId, String fmt, Object... args) {
    return new ConfigInvalidException("Change " + changeId + ": " + String.format(fmt, args));
  }

  @Singleton
  public static class Factory {
    private final Args args;
    private final Provider<InternalChangeQuery> queryProvider;
    private final ProjectCache projectCache;

    @VisibleForTesting
    @Inject
    public Factory(
        Args args, Provider<InternalChangeQuery> queryProvider, ProjectCache projectCache) {
      this.args = args;
      this.queryProvider = queryProvider;
      this.projectCache = projectCache;
    }

    public static ImmutableMap<Change.Id, ObjectId> scanChangeIds(Repository repo)
        throws IOException {
      ImmutableMap.Builder<Change.Id, ObjectId> metaIdByChange = ImmutableMap.builder();
      for (Ref r : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
        if (r.getName().endsWith(RefNames.META_SUFFIX)) {
          Change.Id id = Change.Id.fromRef(r.getName());
          if (id != null) {
            metaIdByChange.put(id, r.getObjectId());
          }
        }
      }
      return metaIdByChange.build();
    }

    public ChangeNotes createChecked(Change c) {
      return createChecked(c.getProject(), c.getId());
    }

    /**
     * Load the change-notes associated to a project/change-id using an existing open repository
     *
     * @param repo existing open repository
     * @param project project associated with the repository
     * @param changeId change-id associated with the change-notes to load
     * @param metaRevId version of the change-id to load, null for loading the latest
     * @return change-notes object for the change
     */
    @UsedAt(UsedAt.Project.MODULE_GIT_REFS_FILTER)
    public ChangeNotes createChecked(
        Repository repo,
        Project.NameKey project,
        Change.Id changeId,
        @Nullable ObjectId metaRevId) {
      Change change = newChange(project, changeId);
      return new ChangeNotes(args, change, true, null, metaRevId).load(repo);
    }

    public ChangeNotes createChecked(
        Project.NameKey project, Change.Id changeId, @Nullable ObjectId metaRevId) {
      Change change = newChange(project, changeId);
      return new ChangeNotes(args, change, true, null, metaRevId).load();
    }

    public ChangeNotes createChecked(Project.NameKey project, Change.Id changeId) {
      return createChecked(project, changeId, null);
    }

    public static Change newChange(Project.NameKey project, Change.Id changeId) {
      return new Change(
          null, changeId, null, BranchNameKey.create(project, "INVALID_NOTE_DB_ONLY"), null);
    }

    public ChangeNotes create(Project.NameKey project, Change.Id changeId) {
      checkArgument(project != null, "project is required");
      return new ChangeNotes(args, newChange(project, changeId), true, null).load();
    }

    public ChangeNotes create(Repository repository, Project.NameKey project, Change.Id changeId) {
      checkArgument(project != null, "project is required");
      return new ChangeNotes(args, newChange(project, changeId), true, null).load(repository);
    }

    /**
     * Create change notes for a change that was loaded from index. This method should only be used
     * when database access is harmful and potentially stale data from the index is acceptable.
     *
     * @param change change loaded from secondary index
     * @return change notes
     */
    public ChangeNotes createFromIndexedChange(Change change) {
      return new ChangeNotes(args, change, true, null);
    }

    public ChangeNotes createForBatchUpdate(Change change, boolean shouldExist) {
      return new ChangeNotes(args, change, shouldExist, null).load();
    }

    public ChangeNotes create(Change change, RefCache refs) {
      return new ChangeNotes(args, change, true, refs).load();
    }

    /**
     * Create change notes based on a {@link com.google.gerrit.entities.Change.Id}. This requires
     * using the Change index and should only be used when {@link
     * com.google.gerrit.entities.Project.NameKey} and the numeric change ID are not available.
     */
    public ChangeNotes createCheckedUsingIndexLookup(Change.Id changeId) {
      InternalChangeQuery query = queryProvider.get().setLimit(2).noFields();
      List<ChangeData> changes = query.byLegacyChangeId(changeId);
      if (changes.isEmpty()) {
        throw new NoSuchChangeException(changeId);
      }
      if (changes.size() != 1) {
        logger.atSevere().log("Multiple changes found for %d", changeId.get());
        throw new NoSuchChangeException(changeId);
      }
      return changes.get(0).notes();
    }

    /**
     * Create change notes based on a list of {@link com.google.gerrit.entities.Change.Id}s. This
     * requires using the Change index and should only be used when {@link
     * com.google.gerrit.entities.Project.NameKey} and the numeric change ID are not available.
     */
    public List<ChangeNotes> createUsingIndexLookup(Collection<Change.Id> changeIds) {
      List<ChangeNotes> notes = new ArrayList<>();
      for (Change.Id changeId : changeIds) {
        try {
          notes.add(createCheckedUsingIndexLookup(changeId));
        } catch (NoSuchChangeException e) {
          // Ignore missing changes to match Access#get(Iterable) behavior.
        }
      }
      return notes;
    }

    public List<ChangeNotes> create(
        Repository repo,
        Project.NameKey project,
        Collection<Change.Id> changeIds,
        Predicate<ChangeNotes> predicate) {
      List<ChangeNotes> notes = new ArrayList<>();
      for (Change.Id cid : changeIds) {
        try {
          ChangeNotes cn = create(repo, project, cid);
          if (cn.getChange() != null && predicate.test(cn)) {
            notes.add(cn);
          }
        } catch (NoSuchChangeException e) {
          // Match ReviewDb behavior, returning not found; maybe the caller learned about it from
          // a dangling patch set ref or something.
          continue;
        }
      }
      return notes;
    }

    /* TODO: This is now unused in the Gerrit code-base, however it is kept in the code
    /* because it is a public method in a stable branch.
     * It can be removed in master branch where we have more flexibility to change the API
     * interface.
     */
    public List<ChangeNotes> create(
        Project.NameKey project,
        Collection<Change.Id> changeIds,
        Predicate<ChangeNotes> predicate) {
      try (Repository repo = args.repoManager.openRepository(project)) {
        return create(repo, project, changeIds, predicate);
      } catch (RepositoryNotFoundException e) {
        // The repository does not exist, hence it does not contain
        // any change.
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Unable to open project=%s when trying to retrieve changeId=%s from NoteDb",
            project, changeIds);
      }
      return Collections.emptyList();
    }

    public ListMultimap<Project.NameKey, ChangeNotes> create(Predicate<ChangeNotes> predicate)
        throws IOException {
      ImmutableListMultimap.Builder<Project.NameKey, ChangeNotes> m =
          ImmutableListMultimap.builder();
      for (Project.NameKey project : projectCache.all()) {
        try (Repository repo = args.repoManager.openRepository(project)) {
          scan(repo, project)
              .filter(r -> !r.error().isPresent())
              .map(ChangeNotesResult::notes)
              .filter(predicate)
              .forEach(n -> m.put(n.getProjectName(), n));
        }
      }
      return m.build();
    }

    public Stream<ChangeNotesResult> scan(Repository repo, Project.NameKey project)
        throws IOException {
      return scan(repo, project, null);
    }

    public Stream<ChangeNotesResult> scan(
        Repository repo, Project.NameKey project, Predicate<Change.Id> changeIdPredicate)
        throws IOException {
      return scan(scanChangeIds(repo), project, changeIdPredicate);
    }

    public Stream<ChangeNotesResult> scan(
        ImmutableMap<Change.Id, ObjectId> metaIdByChange,
        Project.NameKey project,
        Predicate<Change.Id> changeIdPredicate) {
      Stream<Map.Entry<Change.Id, ObjectId>> metaByIdStream = metaIdByChange.entrySet().stream();
      if (changeIdPredicate != null) {
        metaByIdStream = metaByIdStream.filter(e -> changeIdPredicate.test(e.getKey()));
      }
      return metaByIdStream.map(e -> scanOneChange(project, e)).filter(Objects::nonNull);
    }

    @Nullable
    private ChangeNotesResult scanOneChange(
        Project.NameKey project, Map.Entry<Change.Id, ObjectId> metaIdByChangeId) {
      Change.Id id = metaIdByChangeId.getKey();
      // TODO(dborowitz): See discussion in BatchUpdate#newChangeContext.
      try {
        Change change = ChangeNotes.Factory.newChange(project, id);
        logger.atFine().log("adding change %s found in project %s", id, project);
        return toResult(change, metaIdByChangeId.getValue());
      } catch (InvalidServerIdException ise) {
        logger.atWarning().withCause(ise).log(
            "skipping change %d in project %s because of an invalid server id", id.get(), project);
        return null;
      }
    }

    @Nullable
    private ChangeNotesResult toResult(Change rawChangeFromNoteDb, ObjectId metaId) {
      ChangeNotes n = new ChangeNotes(args, rawChangeFromNoteDb, true, null, metaId);
      try {
        n.load();
      } catch (Exception e) {
        return ChangeNotesResult.error(n.getChangeId(), e);
      }
      return ChangeNotesResult.notes(n);
    }

    /** Result of {@link #scan(Repository,Project.NameKey)}. */
    @AutoValue
    public abstract static class ChangeNotesResult {
      static ChangeNotesResult error(Change.Id id, Throwable e) {
        return new AutoValue_ChangeNotes_Factory_ChangeNotesResult(id, Optional.of(e), null);
      }

      static ChangeNotesResult notes(ChangeNotes notes) {
        return new AutoValue_ChangeNotes_Factory_ChangeNotesResult(
            notes.getChangeId(), Optional.empty(), notes);
      }

      /** Change ID that was scanned. */
      public abstract Change.Id id();

      /** Error encountered while loading this change, if any. */
      public abstract Optional<Throwable> error();

      /**
       * Notes loaded for this change.
       *
       * @return notes.
       * @throws IllegalStateException if there was an error loading the change; callers must check
       *     that {@link #error()} is absent before attempting to look up the notes.
       */
      public ChangeNotes notes() {
        checkState(maybeNotes() != null, "no ChangeNotes loaded; check error().isPresent() first");
        return maybeNotes();
      }

      @Nullable
      abstract ChangeNotes maybeNotes();
    }
  }

  private final boolean shouldExist;
  private final RefCache refs;

  private Change change;
  private ChangeNotesState state;

  // Parsed note map state, used by ChangeUpdate to make in-place editing of
  // notes easier.
  RevisionNoteMap<ChangeRevisionNote> revisionNoteMap;

  private DraftCommentNotes draftCommentNotes;
  private RobotCommentNotes robotCommentNotes;

  // Lazy defensive copies of mutable ReviewDb types, to avoid polluting the
  // ChangeNotesCache from handlers.
  private ImmutableSortedMap<String, String> customKeyedValues;
  private ImmutableSortedMap<PatchSet.Id, PatchSet> patchSets;
  private PatchSetApprovals approvals;
  private ImmutableSet<Comment.Key> commentKeys;

  public ChangeNotes(
      Args args,
      Change change,
      boolean shouldExist,
      @Nullable RefCache refs,
      @Nullable ObjectId metaSha1) {
    super(args, change.getId(), metaSha1);
    this.change = new Change(change);
    this.shouldExist = shouldExist;
    this.refs = refs;
  }

  @VisibleForTesting
  public ChangeNotes(Args args, Change change, boolean shouldExist, @Nullable RefCache refs) {
    this(args, change, shouldExist, refs, null);
  }

  public Change getChange() {
    return change;
  }

  public ObjectId getMetaId() {
    return state.metaId();
  }

  public String getServerId() {
    return state.serverId();
  }

  public ImmutableSortedMap<PatchSet.Id, PatchSet> getPatchSets() {
    if (patchSets == null) {
      ImmutableSortedMap.Builder<PatchSet.Id, PatchSet> b = ImmutableSortedMap.naturalOrder();
      b.putAll(state.patchSets());
      patchSets = b.build();
    }
    return patchSets;
  }

  /** Gets the approvals of all patch sets. */
  public PatchSetApprovals getApprovals() {
    if (approvals == null) {
      approvals = PatchSetApprovals.create(ImmutableListMultimap.copyOf(state.approvals()));
    }
    return approvals;
  }

  public ReviewerSet getReviewers() {
    return state.reviewers();
  }

  /** Returns reviewers that do not currently have a Gerrit account and were added by email. */
  public ReviewerByEmailSet getReviewersByEmail() {
    return state.reviewersByEmail();
  }

  /** Returns reviewers that were modified during this change's current WIP phase. */
  public ReviewerSet getPendingReviewers() {
    return state.pendingReviewers();
  }

  /** Returns reviewers by email that were modified during this change's current WIP phase. */
  public ReviewerByEmailSet getPendingReviewersByEmail() {
    return state.pendingReviewersByEmail();
  }

  public ImmutableList<ReviewerStatusUpdate> getReviewerUpdates() {
    return state.reviewerUpdates();
  }

  /** Returns the most recent update (i.e. status) per user. */
  public ImmutableSet<AttentionSetUpdate> getAttentionSet() {
    return state.attentionSet();
  }

  /** Returns all updates for the attention set. */
  public ImmutableList<AttentionSetUpdate> getAttentionSetUpdates() {
    return state.allAttentionSetUpdates();
  }

  /** Returns the key-value pairs that are attached to this change */
  public ImmutableSortedMap<String, String> getCustomKeyedValues() {
    if (customKeyedValues == null) {
      ImmutableSortedMap.Builder<String, String> b = ImmutableSortedMap.naturalOrder();
      b.putAll(state.customKeyedValues());
      customKeyedValues = b.build();
    }
    return customKeyedValues;
  }

  /**
   * Returns the evaluated submit requirements for the change. We only intend to store submit
   * requirements in NoteDb for closed changes. For closed changes, the results represent the state
   * of evaluating submit requirements for this change when it was merged or abandoned.
   *
   * @throws UnsupportedOperationException if submit requirements are requested for an open change.
   */
  public ImmutableList<SubmitRequirementResult> getSubmitRequirementsResult() {
    if (state.columns().status().isOpen()) {
      throw new UnsupportedOperationException(
          String.format(
              "Cannot request stored submit requirements"
                  + " for an open change: project = %s, change ID = %d",
              getProjectName(), state.changeId().get()));
    }
    return state.submitRequirementsResult();
  }

  /** Returns an ImmutableSet of all hashtags for this change sorted in alphabetical order. */
  public ImmutableSet<String> getHashtags() {
    return ImmutableSortedSet.copyOf(state.hashtags());
  }

  /** Returns a list of all users who have ever been a reviewer on this change. */
  public ImmutableList<Account.Id> getAllPastReviewers() {
    return state.allPastReviewers();
  }

  /**
   * Returns submit records stored during the most recent submit; only for changes that were
   * actually submitted.
   */
  public ImmutableList<SubmitRecord> getSubmitRecords() {
    return state.submitRecords();
  }

  /** Returns all change messages, in chronological order, oldest first. */
  public ImmutableList<ChangeMessage> getChangeMessages() {
    return state.changeMessages();
  }

  /** Returns inline comments on each revision. */
  public ImmutableListMultimap<ObjectId, HumanComment> getHumanComments() {
    return state.publishedComments();
  }

  public ImmutableSet<Comment.Key> getCommentKeys() {
    if (commentKeys == null) {
      ImmutableSet.Builder<Comment.Key> b = ImmutableSet.builder();
      for (Comment c : getHumanComments().values()) {
        b.add(new Comment.Key(c.key));
      }
      commentKeys = b.build();
    }
    return commentKeys;
  }

  public int getUpdateCount() {
    return state.updateCount();
  }

  /** Returns {@link Optional} value of time when the change was merged. */
  public Optional<Instant> getMergedOn() {
    return Optional.ofNullable(state.mergedOn());
  }

  public ImmutableListMultimap<ObjectId, HumanComment> getDraftComments(Account.Id author) {
    return getDraftComments(author, null);
  }

  public ImmutableListMultimap<ObjectId, HumanComment> getDraftComments(
      Account.Id author, @Nullable Ref ref) {
    loadDraftComments(author, ref);
    // Filter out any zombie draft comments. These are drafts that are also in
    // the published map, and arise when the update to All-Users to delete them
    // during the publish operation failed.
    return ImmutableListMultimap.copyOf(
        Multimaps.filterEntries(
            draftCommentNotes.getComments(), e -> !getCommentKeys().contains(e.getValue().key)));
  }

  public ImmutableListMultimap<ObjectId, RobotComment> getRobotComments() {
    loadRobotComments();
    return robotCommentNotes.getComments();
  }

  /**
   * If draft comments have already been loaded for this author, then they will not be reloaded.
   * However, this method will load the comments if no draft comments have been loaded or if the
   * caller would like the drafts for another author.
   */
  private void loadDraftComments(Account.Id author, @Nullable Ref ref) {
    if (draftCommentNotes == null || !author.equals(draftCommentNotes.getAuthor()) || ref != null) {
      draftCommentNotes = new DraftCommentNotes(args, getChangeId(), author, ref);
      draftCommentNotes.load();
    }
  }

  private void loadRobotComments() {
    if (robotCommentNotes == null) {
      robotCommentNotes = new RobotCommentNotes(args, change);
      robotCommentNotes.load();
    }
  }

  @VisibleForTesting
  DraftCommentNotes getDraftCommentNotes() {
    return draftCommentNotes;
  }

  public RobotCommentNotes getRobotCommentNotes() {
    loadRobotComments();
    return robotCommentNotes;
  }

  public boolean containsComment(HumanComment c) {
    if (containsCommentPublished(c)) {
      return true;
    }
    loadDraftComments(c.author.getId(), null);
    return draftCommentNotes.containsComment(c);
  }

  public boolean containsCommentPublished(Comment c) {
    for (Comment l : getHumanComments().values()) {
      if (c.key.equals(l.key)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getRefName() {
    return changeMetaRef(getChangeId());
  }

  public PatchSet getCurrentPatchSet() {
    PatchSet.Id psId = change.currentPatchSetId();
    if (psId == null || getPatchSets().get(psId) == null) {
      // In some cases, the current patch-set doesn't exist yet as it's being created during the
      // operation (e.g rebase).
      PatchSet currentPatchset =
          getPatchSets().values().stream()
              .max((p1, p2) -> p1.id().get() - p2.id().get())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "change %s can't load any patchset", getChangeId().toString())));
      return currentPatchset;
    }
    return getPatchSets().get(psId);
  }

  @Override
  protected void onLoad(LoadHandle handle) throws NoSuchChangeException, IOException {
    ObjectId rev = handle.id();
    if (rev == null) {
      if (shouldExist) {
        throw new NoSuchChangeException(getChangeId());
      }
      loadDefaults();
      return;
    }

    ChangeNotesCache.Value v =
        args.cache.get().get(getProjectName(), getChangeId(), rev, handle::walk);
    state = v.state();

    String stateServerId = state.serverId();
    /**
     * In earlier Gerrit versions serverId wasn't part of the change notes cache. That's why the
     * earlier cached entries don't have the serverId attribute. That's fine because in earlier
     * gerrit version serverId was already validated. Another approach to simplify the check would
     * be to bump the cache version, but that would invalidate all persistent cache entries, what we
     * rather try to avoid.
     */
    if (!Strings.isNullOrEmpty(stateServerId)
        && !args.serverId.equals(stateServerId)
        && !args.importedServerIds.contains(stateServerId)) {
      throw new InvalidServerIdException(args.serverId, stateServerId);
    }

    state.copyColumnsTo(change);
    revisionNoteMap = v.revisionNoteMap();
  }

  @Override
  protected void loadDefaults() {
    state = ChangeNotesState.empty(change);
  }

  @Override
  public Project.NameKey getProjectName() {
    return change.getProject();
  }

  @Nullable
  @Override
  protected ObjectId readRef(Repository repo) throws IOException {
    return refs != null ? refs.get(getRefName()).orElse(null) : super.readRef(repo);
  }
}
