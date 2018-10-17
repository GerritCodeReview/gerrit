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
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.git.RefCache;
import com.google.gerrit.server.git.RepoRefCache;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
      Ordering.from(comparing(PatchSetApproval::getGranted));

  public static final Ordering<ChangeMessage> MESSAGE_BY_TIME =
      Ordering.from(comparing(ChangeMessage::getWrittenOn));

  public static ConfigInvalidException parseException(
      Change.Id changeId, String fmt, Object... args) {
    return new ConfigInvalidException("Change " + changeId + ": " + String.format(fmt, args));
  }

  @Nullable
  public static Change readOneReviewDbChange(ReviewDb db, Change.Id id) throws OrmException {
    return ReviewDbUtil.unwrapDb(db).changes().get(id);
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

    public ChangeNotes createChecked(ReviewDb db, Change c) throws OrmException {
      return createChecked(db, c.getProject(), c.getId());
    }

    public ChangeNotes createChecked(ReviewDb db, Project.NameKey project, Change.Id changeId)
        throws OrmException {
      Change change = readOneReviewDbChange(db, changeId);
      if (change == null) {
        if (!args.migration.readChanges()) {
          throw new NoSuchChangeException(changeId);
        }
        // Change isn't in ReviewDb, but its primary storage might be in NoteDb.
        // Prepopulate the change exists with proper noteDbState field.
        change = newNoteDbOnlyChange(project, changeId);
      } else if (!change.getProject().equals(project)) {
        throw new NoSuchChangeException(changeId);
      }
      return new ChangeNotes(args, change).load();
    }

    public ChangeNotes createChecked(Change.Id changeId) throws OrmException {
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

    public static Change newNoteDbOnlyChange(Project.NameKey project, Change.Id changeId) {
      Change change =
          new Change(
              null, changeId, null, new Branch.NameKey(project, "INVALID_NOTE_DB_ONLY"), null);
      change.setNoteDbState(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);
      return change;
    }

    private Change loadChangeFromDb(ReviewDb db, Project.NameKey project, Change.Id changeId)
        throws OrmException {
      checkArgument(project != null, "project is required");
      Change change = readOneReviewDbChange(db, changeId);

      if (change == null) {
        if (args.migration.readChanges()) {
          return newNoteDbOnlyChange(project, changeId);
        }
        throw new NoSuchChangeException(changeId);
      }
      checkArgument(
          change.getProject().equals(project),
          "passed project %s when creating ChangeNotes for %s, but actual project is %s",
          project,
          changeId,
          change.getProject());
      return change;
    }

    public ChangeNotes create(ReviewDb db, Project.NameKey project, Change.Id changeId)
        throws OrmException {
      return new ChangeNotes(args, loadChangeFromDb(db, project, changeId)).load();
    }

    public ChangeNotes createWithAutoRebuildingDisabled(
        ReviewDb db, Project.NameKey project, Change.Id changeId) throws OrmException {
      return new ChangeNotes(args, loadChangeFromDb(db, project, changeId), true, false, null)
          .load();
    }

    /**
     * Create change notes for a change that was loaded from index. This method should only be used
     * when database access is harmful and potentially stale data from the index is acceptable.
     *
     * @param change change loaded from secondary index
     * @return change notes
     */
    public ChangeNotes createFromIndexedChange(Change change) {
      return new ChangeNotes(args, change);
    }

    public ChangeNotes createForBatchUpdate(Change change, boolean shouldExist)
        throws OrmException {
      return new ChangeNotes(args, change, shouldExist, false, null).load();
    }

    public ChangeNotes createWithAutoRebuildingDisabled(Change change, RefCache refs)
        throws OrmException {
      return new ChangeNotes(args, change, true, false, refs).load();
    }

    // TODO(ekempin): Remove when database backend is deleted
    /**
     * Instantiate ChangeNotes for a change that has been loaded by a batch read from the database.
     */
    private ChangeNotes createFromChangeOnlyWhenNoteDbDisabled(Change change) throws OrmException {
      checkState(
          !args.migration.readChanges(),
          "do not call createFromChangeWhenNoteDbDisabled when NoteDb is enabled");
      return new ChangeNotes(args, change).load();
    }

    public List<ChangeNotes> create(ReviewDb db, Collection<Change.Id> changeIds)
        throws OrmException {
      List<ChangeNotes> notes = new ArrayList<>();
      if (args.migration.readChanges()) {
        for (Change.Id changeId : changeIds) {
          try {
            notes.add(createChecked(changeId));
          } catch (NoSuchChangeException e) {
            // Ignore missing changes to match Access#get(Iterable) behavior.
          }
        }
        return notes;
      }

      for (Change c : ReviewDbUtil.unwrapDb(db).changes().get(changeIds)) {
        notes.add(createFromChangeOnlyWhenNoteDbDisabled(c));
      }
      return notes;
    }

    public List<ChangeNotes> create(
        ReviewDb db,
        Project.NameKey project,
        Collection<Change.Id> changeIds,
        Predicate<ChangeNotes> predicate)
        throws OrmException {
      List<ChangeNotes> notes = new ArrayList<>();
      if (args.migration.readChanges()) {
        for (Change.Id cid : changeIds) {
          try {
            ChangeNotes cn = create(db, project, cid);
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

      for (Change c : ReviewDbUtil.unwrapDb(db).changes().get(changeIds)) {
        if (c != null && project.equals(c.getDest().getParentKey())) {
          ChangeNotes cn = createFromChangeOnlyWhenNoteDbDisabled(c);
          if (predicate.test(cn)) {
            notes.add(cn);
          }
        }
      }
      return notes;
    }

    public ListMultimap<Project.NameKey, ChangeNotes> create(
        ReviewDb db, Predicate<ChangeNotes> predicate) throws IOException, OrmException {
      ListMultimap<Project.NameKey, ChangeNotes> m =
          MultimapBuilder.hashKeys().arrayListValues().build();
      if (args.migration.readChanges()) {
        for (Project.NameKey project : projectCache.all()) {
          try (Repository repo = args.repoManager.openRepository(project)) {
            scanNoteDb(repo, db, project)
                .filter(r -> !r.error().isPresent())
                .map(ChangeNotesResult::notes)
                .filter(predicate)
                .forEach(n -> m.put(n.getProjectName(), n));
          }
        }
      } else {
        for (Change change : ReviewDbUtil.unwrapDb(db).changes().all()) {
          ChangeNotes notes = createFromChangeOnlyWhenNoteDbDisabled(change);
          if (predicate.test(notes)) {
            m.put(change.getProject(), notes);
          }
        }
      }
      return ImmutableListMultimap.copyOf(m);
    }

    public Stream<ChangeNotesResult> scan(Repository repo, ReviewDb db, Project.NameKey project)
        throws IOException {
      return args.migration.readChanges() ? scanNoteDb(repo, db, project) : scanReviewDb(repo, db);
    }

    private Stream<ChangeNotesResult> scanReviewDb(Repository repo, ReviewDb db)
        throws IOException {
      // Scan IDs that might exist in ReviewDb, assuming that each change has at least one patch set
      // ref. Not all changes might exist: some patch set refs might have been written where the
      // corresponding ReviewDb write failed. These will be silently filtered out by the batch get
      // call below, which is intended.
      Set<Change.Id> ids = scanChangeIds(repo).fromPatchSetRefs();

      // A batch size of N may overload get(Iterable), so use something smaller, but still >1.
      return Streams.stream(Iterators.partition(ids.iterator(), 30))
          .flatMap(
              batch -> {
                try {
                  return Streams.stream(ReviewDbUtil.unwrapDb(db).changes().get(batch))
                      .map(this::toResult)
                      .filter(Objects::nonNull);
                } catch (OrmException e) {
                  // Return this error for each Id in the input batch.
                  return batch.stream().map(id -> ChangeNotesResult.error(id, e));
                }
              });
    }

    private Stream<ChangeNotesResult> scanNoteDb(
        Repository repo, ReviewDb db, Project.NameKey project) throws IOException {
      ScanResult sr = scanChangeIds(repo);
      PrimaryStorage defaultStorage = args.migration.changePrimaryStorage();

      return sr.all()
          .stream()
          .map(id -> scanOneNoteDbChange(db, project, sr, defaultStorage, id))
          .filter(Objects::nonNull);
    }

    private ChangeNotesResult scanOneNoteDbChange(
        ReviewDb db,
        Project.NameKey project,
        ScanResult sr,
        PrimaryStorage defaultStorage,
        Change.Id id) {
      Change change;
      try {
        change = readOneReviewDbChange(db, id);
      } catch (OrmException e) {
        return ChangeNotesResult.error(id, e);
      }

      if (change == null) {
        if (!sr.fromMetaRefs().contains(id)) {
          // Stray patch set refs can happen due to normal error conditions, e.g. failed
          // push processing, so aren't worth even a warning.
          return null;
        }
        if (defaultStorage == PrimaryStorage.REVIEW_DB) {
          // If changes should exist in ReviewDb, it's worth warning about a meta ref with
          // no corresponding ReviewDb data.
          logger.atWarning().log(
              "skipping change %s found in project %s but not in ReviewDb", id, project);
          return null;
        }
        // TODO(dborowitz): See discussion in NoteDbBatchUpdate#newChangeContext.
        change = ChangeNotes.Factory.newNoteDbOnlyChange(project, id);
      } else if (!change.getProject().equals(project)) {
        logger.atSevere().log(
            "skipping change %s found in project %s because ReviewDb change has project %s",
            id, project, change.getProject());
        return null;
      }
      logger.atFine().log("adding change %s found in project %s", id, project);
      return toResult(change);
    }

    @Nullable
    private ChangeNotesResult toResult(Change rawChangeFromReviewDbOrNoteDb) {
      ChangeNotes n = new ChangeNotes(args, rawChangeFromReviewDbOrNoteDb);
      try {
        n.load();
      } catch (OrmException e) {
        return ChangeNotesResult.error(n.getChangeId(), e);
      }
      return ChangeNotesResult.notes(n);
    }

    /** Result of {@link #scan(Repository, ReviewDb, Project.NameKey)}. */
    @AutoValue
    public abstract static class ChangeNotesResult {
      static ChangeNotesResult error(Change.Id id, OrmException e) {
        return new AutoValue_ChangeNotes_Factory_ChangeNotesResult(id, Optional.of(e), null);
      }

      static ChangeNotesResult notes(ChangeNotes notes) {
        return new AutoValue_ChangeNotes_Factory_ChangeNotesResult(
            notes.getChangeId(), Optional.empty(), notes);
      }

      /** Change ID that was scanned. */
      public abstract Change.Id id();

      /** Error encountered while loading this change, if any. */
      public abstract Optional<OrmException> error();

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

  private NoteDbUpdateManager.Result rebuildResult;
  private DraftCommentNotes draftCommentNotes;
  private RobotCommentNotes robotCommentNotes;

  // Lazy defensive copies of mutable ReviewDb types, to avoid polluting the
  // ChangeNotesCache from handlers.
  private ImmutableSortedMap<PatchSet.Id, PatchSet> patchSets;
  private ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals;
  private ImmutableSet<Comment.Key> commentKeys;

  @VisibleForTesting
  public ChangeNotes(Args args, Change change) {
    this(args, change, true, true, null);
  }

  private ChangeNotes(
      Args args, Change change, boolean shouldExist, boolean autoRebuild, @Nullable RefCache refs) {
    super(args, change.getId(), PrimaryStorage.of(change), autoRebuild);
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
      for (Map.Entry<PatchSet.Id, PatchSet> e : state.patchSets()) {
        b.put(e.getKey(), new PatchSet(e.getValue()));
      }
      patchSets = b.build();
    }
    return patchSets;
  }

  public ImmutableListMultimap<PatchSet.Id, PatchSetApproval> getApprovals() {
    if (approvals == null) {
      ImmutableListMultimap.Builder<PatchSet.Id, PatchSetApproval> b =
          ImmutableListMultimap.builder();
      for (Map.Entry<PatchSet.Id, PatchSetApproval> e : state.approvals()) {
        b.put(e.getKey(), new PatchSetApproval(e.getValue()));
      }
      approvals = b.build();
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

  /** @return an ImmutableSet of Account.Ids of all users that have been assigned to this change. */
  public ImmutableSet<Account.Id> getPastAssignees() {
    return state.pastAssignees();
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
  public ImmutableListMultimap<RevId, Comment> getComments() {
    return state.publishedComments();
  }

  public ImmutableSet<Comment.Key> getCommentKeys() {
    if (commentKeys == null) {
      ImmutableSet.Builder<Comment.Key> b = ImmutableSet.builder();
      for (Comment c : getComments().values()) {
        b.add(new Comment.Key(c.key));
      }
      commentKeys = b.build();
    }
    return commentKeys;
  }

  public ImmutableListMultimap<RevId, Comment> getDraftComments(Account.Id author)
      throws OrmException {
    return getDraftComments(author, null);
  }

  public ImmutableListMultimap<RevId, Comment> getDraftComments(
      Account.Id author, @Nullable Ref ref) throws OrmException {
    loadDraftComments(author, ref);
    // Filter out any zombie draft comments. These are drafts that are also in
    // the published map, and arise when the update to All-Users to delete them
    // during the publish operation failed.
    return ImmutableListMultimap.copyOf(
        Multimaps.filterEntries(
            draftCommentNotes.getComments(), e -> !getCommentKeys().contains(e.getValue().key)));
  }

  public ImmutableListMultimap<RevId, RobotComment> getRobotComments() throws OrmException {
    loadRobotComments();
    return robotCommentNotes.getComments();
  }

  /**
   * If draft comments have already been loaded for this author, then they will not be reloaded.
   * However, this method will load the comments if no draft comments have been loaded or if the
   * caller would like the drafts for another author.
   */
  private void loadDraftComments(Account.Id author, @Nullable Ref ref) throws OrmException {
    if (draftCommentNotes == null || !author.equals(draftCommentNotes.getAuthor()) || ref != null) {
      draftCommentNotes =
          new DraftCommentNotes(args, change, author, autoRebuild, rebuildResult, ref);
      draftCommentNotes.load();
    }
  }

  private void loadRobotComments() throws OrmException {
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

  public boolean containsComment(Comment c) throws OrmException {
    if (containsCommentPublished(c)) {
      return true;
    }
    loadDraftComments(c.author.getId(), null);
    return draftCommentNotes.containsComment(c);
  }

  public boolean containsCommentPublished(Comment c) {
    for (Comment l : getComments().values()) {
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

  @VisibleForTesting
  public Timestamp getReadOnlyUntil() {
    return state.readOnlyUntil();
  }

  @Override
  protected void onLoad(LoadHandle handle)
      throws NoSuchChangeException, IOException, ConfigInvalidException {
    ObjectId rev = handle.id();
    if (rev == null) {
      if (args.migration.readChanges()
          && PrimaryStorage.of(change) == PrimaryStorage.NOTE_DB
          && shouldExist) {
        throw new NoSuchChangeException(getChangeId());
      }
      loadDefaults();
      return;
    }

    ChangeNotesCache.Value v =
        args.cache.get().get(getProjectName(), getChangeId(), rev, handle.walk());
    state = v.state();
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

  @Override
  protected LoadHandle openHandle(Repository repo) throws NoSuchChangeException, IOException {
    if (autoRebuild) {
      NoteDbChangeState state = NoteDbChangeState.parse(change);
      if (args.migration.disableChangeReviewDb()) {
        checkState(
            state != null,
            "shouldn't have null NoteDbChangeState when ReviewDb disabled: %s",
            change);
      }
      ObjectId id = readRef(repo);
      if (id == null) {
        // Meta ref doesn't exist in NoteDb.

        if (state == null) {
          // Either ReviewDb change is being newly created, or it exists in ReviewDb but has not yet
          // been rebuilt for the first time, e.g. because we just turned on write-only mode. In
          // both cases, we don't want to auto-rebuild, just proceed with an empty ChangeNotes.
          return super.openHandle(repo, id);
        } else if (shouldExist && state.getPrimaryStorage() == PrimaryStorage.NOTE_DB) {
          throw new NoSuchChangeException(getChangeId());
        }

        // ReviewDb claims NoteDb state exists, but meta ref isn't present: fall through and
        // auto-rebuild if necessary.
      }
      RefCache refs = this.refs != null ? this.refs : new RepoRefCache(repo);
      if (!NoteDbChangeState.isChangeUpToDate(state, refs, getChangeId())) {
        return rebuildAndOpen(repo, id);
      }
    }
    return super.openHandle(repo);
  }

  private LoadHandle rebuildAndOpen(Repository repo, ObjectId oldId) throws IOException {
    Timer1.Context timer = args.metrics.autoRebuildLatency.start(CHANGES);
    try {
      Change.Id cid = getChangeId();
      ReviewDb db = args.db.get();
      ChangeRebuilder rebuilder = args.rebuilder.get();
      NoteDbUpdateManager.Result r;
      try (NoteDbUpdateManager manager = rebuilder.stage(db, cid)) {
        if (manager == null) {
          return super.openHandle(repo, oldId); // May be null in tests.
        }
        manager.setRefLogMessage("Auto-rebuilding change");
        r = manager.stageAndApplyDelta(change);
        try {
          rebuilder.execute(db, cid, manager);
          repo.scanForRepoChanges();
        } catch (OrmException | IOException e) {
          // Rebuilding failed. Most likely cause is contention on one or more
          // change refs; there are other types of errors that can happen during
          // rebuilding, but generally speaking they should happen during stage(),
          // not execute(). Assume that some other worker is going to successfully
          // store the rebuilt state, which is deterministic given an input
          // ChangeBundle.
          //
          // Parse notes from the staged result so we can return something useful
          // to the caller instead of throwing.
          logger.atFine().log("Rebuilding change %s failed: %s", getChangeId(), e.getMessage());
          args.metrics.autoRebuildFailureCount.increment(CHANGES);
          rebuildResult = requireNonNull(r);
          requireNonNull(r.newState());
          requireNonNull(r.staged());
          requireNonNull(r.staged().changeObjects());
          return LoadHandle.create(
              ChangeNotesCommit.newStagedRevWalk(repo, r.staged().changeObjects()),
              r.newState().getChangeMetaId());
        }
      }
      return LoadHandle.create(ChangeNotesCommit.newRevWalk(repo), r.newState().getChangeMetaId());
    } catch (NoSuchChangeException e) {
      return super.openHandle(repo, oldId);
    } catch (OrmException e) {
      throw new IOException(e);
    } finally {
      logger.atFine().log(
          "Rebuilt change %s in project %s in %s ms",
          getChangeId(),
          getProjectName(),
          TimeUnit.MILLISECONDS.convert(timer.stop(), TimeUnit.NANOSECONDS));
    }
  }
}
