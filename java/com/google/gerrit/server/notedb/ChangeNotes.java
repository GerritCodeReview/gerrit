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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.AssigneeStatusUpdate;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** View of a single {@link Change} based on the log of its notes branch. */
public class ChangeNotes extends AbstractChangeNotes<ChangeNotes> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final Ordering<PatchSetApproval> PSA_BY_TIME =
      Ordering.from(comparing(PatchSetApproval::granted));

  public static final Ordering<ChangeMessage> MESSAGE_BY_TIME =
      Ordering.from(comparing(ChangeMessage::getWrittenOn));

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

    public ChangeNotes createChecked(Change c) {
      return createChecked(c.getProject(), c.getId());
    }

    public ChangeNotes createChecked(Project.NameKey project, Change.Id changeId) {
      Change change = newChange(project, changeId);
      return new ChangeNotes(args, change, true, null).load();
    }

    public ChangeNotes createChecked(Change.Id changeId) {
      InternalChangeQuery query = queryProvider.get().noFields();
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

    public static Change newChange(Project.NameKey project, Change.Id changeId) {
      return new Change(
          null, changeId, null, BranchNameKey.create(project, "INVALID_NOTE_DB_ONLY"), null);
    }

    public ChangeNotes create(Project.NameKey project, Change.Id changeId) {
      checkArgument(project != null, "project is required");
      return new ChangeNotes(args, newChange(project, changeId), true, null).load();
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

    public List<ChangeNotes> create(Collection<Change.Id> changeIds) {
      List<ChangeNotes> notes = new ArrayList<>();
      for (Change.Id changeId : changeIds) {
        try {
          notes.add(createChecked(changeId));
        } catch (NoSuchChangeException e) {
          // Ignore missing changes to match Access#get(Iterable) behavior.
        }
      }
      return notes;
    }

    public List<ChangeNotes> create(
        Project.NameKey project,
        Collection<Change.Id> changeIds,
        Predicate<ChangeNotes> predicate) {
      List<ChangeNotes> notes = new ArrayList<>();
      for (Change.Id cid : changeIds) {
        try {
          ChangeNotes cn = create(project, cid);
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

    public ListMultimap<Project.NameKey, ChangeNotes> create(Predicate<ChangeNotes> predicate)
        throws IOException {
      ListMultimap<Project.NameKey, ChangeNotes> m =
          MultimapBuilder.hashKeys().arrayListValues().build();
      for (Project.NameKey project : projectCache.all()) {
        try (Repository repo = args.repoManager.openRepository(project)) {
          scan(repo, project)
              .filter(r -> !r.error().isPresent())
              .map(ChangeNotesResult::notes)
              .filter(predicate)
              .forEach(n -> m.put(n.getProjectName(), n));
        }
      }
      return ImmutableListMultimap.copyOf(m);
    }

    public Stream<ChangeNotesResult> scan(Repository repo, Project.NameKey project)
        throws IOException {
      return scan(repo, project, null);
    }

    public Stream<ChangeNotesResult> scan(
        Repository repo, Project.NameKey project, Predicate<Change.Id> changeIdPredicate)
        throws IOException {
      ScanResult sr = scanChangeIds(repo);

      Stream<Change.Id> idStream = sr.all().stream();
      if (changeIdPredicate != null) {
        idStream = idStream.filter(changeIdPredicate);
      }
      return idStream.map(id -> scanOneChange(project, sr, id)).filter(Objects::nonNull);
    }

    @Nullable
    private ChangeNotesResult scanOneChange(Project.NameKey project, ScanResult sr, Change.Id id) {
      if (!sr.fromMetaRefs().contains(id)) {
        // Stray patch set refs can happen due to normal error conditions, e.g. failed
        // push processing, so aren't worth even a warning.
        return null;
      }

      // TODO(dborowitz): See discussion in BatchUpdate#newChangeContext.
      try {
        Change change = ChangeNotes.Factory.newChange(project, id);
        logger.atFine().log("adding change %s found in project %s", id, project);
        return toResult(change);
      } catch (InvalidServerIdException ise) {
        logger.atWarning().withCause(ise).log(
            "skipping change %d in project %s because of an invalid server id", id.get(), project);
        return null;
      }
    }

    @Nullable
    private ChangeNotesResult toResult(Change rawChangeFromNoteDb) {
      ChangeNotes n = new ChangeNotes(args, rawChangeFromNoteDb, true, null);
      try {
        n.load();
      } catch (StorageException e) {
        return ChangeNotesResult.error(n.getChangeId(), e);
      }
      return ChangeNotesResult.notes(n);
    }

    /** Result of {@link #scan(Repository,Project.NameKey)}. */
    @AutoValue
    public abstract static class ChangeNotesResult {
      static ChangeNotesResult error(Change.Id id, StorageException e) {
        return new AutoValue_ChangeNotes_Factory_ChangeNotesResult(id, Optional.of(e), null);
      }

      static ChangeNotesResult notes(ChangeNotes notes) {
        return new AutoValue_ChangeNotes_Factory_ChangeNotesResult(
            notes.getChangeId(), Optional.empty(), notes);
      }

      /** Change ID that was scanned. */
      public abstract Change.Id id();

      /** Error encountered while loading this change, if any. */
      public abstract Optional<StorageException> error();

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

    @AutoValue
    abstract static class ScanResult {
      abstract ImmutableSet<Change.Id> fromPatchSetRefs();

      abstract ImmutableSet<Change.Id> fromMetaRefs();

      SetView<Change.Id> all() {
        return Sets.union(fromPatchSetRefs(), fromMetaRefs());
      }
    }

    private static ScanResult scanChangeIds(Repository repo) throws IOException {
      ImmutableSet.Builder<Change.Id> fromPs = ImmutableSet.builder();
      ImmutableSet.Builder<Change.Id> fromMeta = ImmutableSet.builder();
      for (Ref r : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
        Change.Id id = Change.Id.fromRef(r.getName());
        if (id != null) {
          (r.getName().endsWith(RefNames.META_SUFFIX) ? fromMeta : fromPs).add(id);
        }
      }
      return new AutoValue_ChangeNotes_Factory_ScanResult(fromPs.build(), fromMeta.build());
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
  private ImmutableSortedMap<PatchSet.Id, PatchSet> patchSets;
  private ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals;
  private ImmutableSet<Comment.Key> commentKeys;

  @VisibleForTesting
  public ChangeNotes(Args args, Change change, boolean shouldExist, @Nullable RefCache refs) {
    super(args, change.getId());
    this.change = new Change(change);
    this.shouldExist = shouldExist;
    this.refs = refs;
  }

  public Change getChange() {
    return change;
  }

  public ObjectId getMetaId() {
    return state.metaId();
  }

  public ImmutableSortedMap<PatchSet.Id, PatchSet> getPatchSets() {
    if (patchSets == null) {
      ImmutableSortedMap.Builder<PatchSet.Id, PatchSet> b =
          ImmutableSortedMap.orderedBy(comparing(PatchSet.Id::get));
      b.putAll(state.patchSets());
      patchSets = b.build();
    }
    return patchSets;
  }

  public ImmutableListMultimap<PatchSet.Id, PatchSetApproval> getApprovals() {
    if (approvals == null) {
      approvals = ImmutableListMultimap.copyOf(state.approvals());
    }
    return approvals;
  }

  public ReviewerSet getReviewers() {
    return state.reviewers();
  }

  /** @return reviewers that do not currently have a Gerrit account and were added by email. */
  public ReviewerByEmailSet getReviewersByEmail() {
    return state.reviewersByEmail();
  }

  /** @return reviewers that were modified during this change's current WIP phase. */
  public ReviewerSet getPendingReviewers() {
    return state.pendingReviewers();
  }

  /** @return reviewers by email that were modified during this change's current WIP phase. */
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

  /**
   * @return an ImmutableSet of Account.Ids of all users that have been assigned to this change. The
   *     order of the set is the order in which they were assigned.
   */
  public ImmutableSet<Account.Id> getPastAssignees() {
    return Lists.reverse(state.assigneeUpdates()).stream()
        .map(AssigneeStatusUpdate::currentAssignee)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  /**
   * @return an ImmutableList of AssigneeStatusUpdate of all the updates to the assignee field to
   *     this change. The order of the list is from most recent updates to least recent.
   */
  public ImmutableList<AssigneeStatusUpdate> getAssigneeUpdates() {
    return state.assigneeUpdates();
  }

  /** @return a ImmutableSet of all hashtags for this change sorted in alphabetical order. */
  public ImmutableSet<String> getHashtags() {
    return ImmutableSortedSet.copyOf(state.hashtags());
  }

  /** @return a list of all users who have ever been a reviewer on this change. */
  public ImmutableList<Account.Id> getAllPastReviewers() {
    return state.allPastReviewers();
  }

  /**
   * @return submit records stored during the most recent submit; only for changes that were
   *     actually submitted.
   */
  public ImmutableList<SubmitRecord> getSubmitRecords() {
    return state.submitRecords();
  }

  /** @return all change messages, in chronological order, oldest first. */
  public ImmutableList<ChangeMessage> getChangeMessages() {
    return state.changeMessages();
  }

  /** @return inline comments on each revision. */
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
    return requireNonNull(
        getPatchSets().get(psId), () -> String.format("missing current patch set %s", psId.get()));
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
    if (!Strings.isNullOrEmpty(stateServerId) && !args.serverId.equals(stateServerId)) {
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

  @Override
  protected ObjectId readRef(Repository repo) throws IOException {
    return refs != null ? refs.get(getRefName()).orElse(null) : super.readRef(repo);
  }
}
