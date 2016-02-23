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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/** View of a single {@link Change} based on the log of its notes branch. */
public class ChangeNotes extends AbstractChangeNotes<ChangeNotes> {
  static final Ordering<PatchSetApproval> PSA_BY_TIME =
      Ordering.natural().onResultOf(
        new Function<PatchSetApproval, Timestamp>() {
          @Override
          public Timestamp apply(PatchSetApproval input) {
            return input.getGranted();
          }
        });

  public static final Ordering<ChangeMessage> MESSAGE_BY_TIME =
      Ordering.natural().onResultOf(
        new Function<ChangeMessage, Timestamp>() {
          @Override
          public Timestamp apply(ChangeMessage input) {
            return input.getWrittenOn();
          }
        });

  public static ConfigInvalidException parseException(Change.Id changeId,
      String fmt, Object... args) {
    return new ConfigInvalidException("Change " + changeId + ": "
        + String.format(fmt, args));
  }

  public static Account.Id parseIdent(PersonIdent ident, Change.Id changeId)
      throws ConfigInvalidException {
    String email = ident.getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      String host = email.substring(at + 1, email.length());
      Integer id = Ints.tryParse(email.substring(0, at));
      if (id != null && host.equals(GERRIT_PLACEHOLDER_HOST)) {
        return new Account.Id(id);
      }
    }
    throw parseException(changeId, "invalid identity, expected <id>@%s: %s",
      GERRIT_PLACEHOLDER_HOST, email);
  }

  @Singleton
  public static class Factory {
    private static final Logger log = LoggerFactory.getLogger(Factory.class);

    private final GitRepositoryManager repoManager;
    private final NotesMigration migration;
    private final AllUsersName allUsers;
    private final Provider<InternalChangeQuery> queryProvider;
    private final ProjectCache projectCache;

    @VisibleForTesting
    @Inject
    public Factory(GitRepositoryManager repoManager,
        NotesMigration migration,
        AllUsersName allUsers,
        Provider<InternalChangeQuery> queryProvider,
        ProjectCache projectCache) {
      this.repoManager = repoManager;
      this.migration = migration;
      this.allUsers = allUsers;
      this.queryProvider = queryProvider;
      this.projectCache = projectCache;
    }

    public ChangeNotes createChecked(ReviewDb db, Change c)
        throws OrmException, NoSuchChangeException {
      ChangeNotes notes = create(db, c.getProject(), c.getId());
      if (notes.getChange() == null) {
        throw new NoSuchChangeException(c.getId());
      }
      return notes;
    }

    public ChangeNotes createChecked(ReviewDb db, Project.NameKey project,
        Change.Id changeId) throws OrmException, NoSuchChangeException {
      ChangeNotes notes = create(db, project, changeId);
      if (notes.getChange() == null) {
        throw new NoSuchChangeException(changeId);
      }
      return notes;
    }

    public ChangeNotes createChecked(Change.Id changeId)
        throws OrmException, NoSuchChangeException {
      InternalChangeQuery query = queryProvider.get().noFields();
      List<ChangeData> changes = query.byLegacyChangeId(changeId);
      if (changes.isEmpty()) {
        throw new NoSuchChangeException(changeId);
      }
      if (changes.size() != 1) {
        log.error(
            String.format("Multiple changes found for %d", changeId.get()));
        throw new NoSuchChangeException(changeId);
      }
      return changes.get(0).notes();
    }

    public ChangeNotes create(ReviewDb db, Project.NameKey project,
        Change.Id changeId) throws OrmException {
      Change change = db.changes().get(changeId);
      checkArgument(change.getProject().equals(project),
          "passed project %s when creating ChangeNotes for %s, but actual"
          + " project is %s",
          project, changeId, change.getProject());
      // TODO: Throw NoSuchChangeException when the change is not found in the
      // database
      return new ChangeNotes(repoManager, migration, allUsers, project,
          change).load();
    }

    /**
     * Create change notes for a change that was loaded from index. This method
     * should only be used when database access is harmful and potentially stale
     * data from the index is acceptable.
     *
     * @param change change loaded from secondary index
     * @return change notes
     */
    public ChangeNotes createFromIndexedChange(Change change) {
      return new ChangeNotes(repoManager, migration, allUsers,
          change.getProject(), change);
    }

    public ChangeNotes createForNew(Change change) throws OrmException {
      return new ChangeNotes(repoManager, migration, allUsers,
          change.getProject(), change).load();
    }

    // TODO(dborowitz): Remove when deleting index schemas <27.
    public ChangeNotes createFromIdOnlyWhenNotedbDisabled(
        ReviewDb db, Change.Id changeId) throws OrmException {
    checkState(!migration.readChanges(), "do not call"
        + " createFromIdOnlyWhenNotedbDisabled when notedb is enabled");
      Change change = db.changes().get(changeId);
      return new ChangeNotes(repoManager, migration, allUsers,
          change.getProject(), change).load();
    }

    // TODO(ekempin): Remove when database backend is deleted
    /**
     * Instantiate ChangeNotes for a change that has been loaded by a batch read
     * from the database.
     */
    private ChangeNotes createFromChangeOnlyWhenNotedbDisabled(Change change)
        throws OrmException {
      checkState(!migration.readChanges(), "do not call"
          + " createFromChangeWhenNotedbDisabled when notedb is enabled");
      return new ChangeNotes(repoManager, migration, allUsers,
          change.getProject(), change).load();
    }

    public CheckedFuture<ChangeNotes, OrmException> createAsync(
        final ListeningExecutorService executorService, final ReviewDb db,
        final Project.NameKey project, final Change.Id changeId) {
      return Futures.makeChecked(
          Futures.transformAsync(db.changes().getAsync(changeId),
              new AsyncFunction<Change, ChangeNotes>() {
                @Override
                public ListenableFuture<ChangeNotes> apply(
                    final Change change) {
                  return executorService.submit(new Callable<ChangeNotes>() {
                    @Override
                    public ChangeNotes call() throws Exception {
                      checkArgument(change.getProject().equals(project),
                          "passed project %s when creating ChangeNotes for %s,"
                              + " but actual project is %s",
                          project, changeId, change.getProject());
                      return new ChangeNotes(repoManager, migration,
                          allUsers, project, change).load();
                    }
                  });
                }
              }), new Function<Exception, OrmException>() {
                @Override
                public OrmException apply(Exception e) {
                  if (e instanceof OrmException) {
                    return (OrmException) e;
                  }
                  return new OrmException(e);
                }
              });
    }

    public List<ChangeNotes> create(ReviewDb db,
        Collection<Change.Id> changeIds) throws OrmException {
      List<ChangeNotes> notes = new ArrayList<>();
      if (migration.enabled()) {
        for (Change.Id changeId : changeIds) {
          try {
            notes.add(createChecked(changeId));
          } catch (NoSuchChangeException e) {
            // Ignore missing changes to match Access#get(Iterable) behavior.
          }
        }
        return notes;
      }

      for (Change c : db.changes().get(changeIds)) {
        notes.add(createFromChangeOnlyWhenNotedbDisabled(c));
      }
      return notes;
    }

    public List<ChangeNotes> create(ReviewDb db, Project.NameKey project,
        Collection<Change.Id> changeIds, Predicate<ChangeNotes> predicate)
            throws OrmException {
      List<ChangeNotes> notes = new ArrayList<>();
      if (migration.enabled()) {
        for (Change.Id cid : changeIds) {
          ChangeNotes cn = create(db, project, cid);
          if (cn.getChange() != null && predicate.apply(cn)) {
            notes.add(cn);
          }
        }
        return notes;
      }

      for (Change c : db.changes().get(changeIds)) {
        if (c != null && project.equals(c.getDest().getParentKey())) {
          ChangeNotes cn = createFromChangeOnlyWhenNotedbDisabled(c);
          if (predicate.apply(cn)) {
            notes.add(cn);
          }
        }
      }
      return notes;
    }

    public ListMultimap<Project.NameKey, ChangeNotes> create(ReviewDb db,
        Predicate<ChangeNotes> predicate) throws IOException, OrmException {
      ListMultimap<Project.NameKey, ChangeNotes> m = ArrayListMultimap.create();
      if (migration.readChanges()) {
        for (Project.NameKey project : projectCache.all()) {
          try (Repository repo = repoManager.openRepository(project)) {
            List<ChangeNotes> changes = scanNotedb(repo, db, project);
            for (ChangeNotes cn : changes) {
              if (predicate.apply(cn)) {
                m.put(project, cn);
              }
            }
          }
        }
      } else {
        for (Change change : db.changes().all()) {
          ChangeNotes notes = createFromChangeOnlyWhenNotedbDisabled(change);
          if (predicate.apply(notes)) {
            m.put(change.getProject(), notes);
          }
        }
      }
      return ImmutableListMultimap.copyOf(m);
    }

    public List<ChangeNotes> scan(Repository repo, ReviewDb db,
        Project.NameKey project) throws OrmException, IOException {
      if (!migration.readChanges()) {
        return scanDb(repo, db);
      }

      return scanNotedb(repo, db, project);
    }

    private List<ChangeNotes> scanDb(Repository repo, ReviewDb db)
        throws OrmException, IOException {
      Set<Change.Id> ids = scan(repo);
      List<ChangeNotes> notes = new ArrayList<>(ids.size());
      // A batch size of N may overload get(Iterable), so use something smaller,
      // but still >1.
      for (List<Change.Id> batch : Iterables.partition(ids, 30)) {
        for (Change change : db.changes().get(batch)) {
          notes.add(createFromChangeOnlyWhenNotedbDisabled(change));
        }
      }
      return notes;
    }

    private List<ChangeNotes> scanNotedb(Repository repo, ReviewDb db,
        Project.NameKey project) throws OrmException, IOException {
      Set<Change.Id> ids = scan(repo);
      List<ChangeNotes> changeNotes = new ArrayList<>(ids.size());
      for (Change.Id id : ids) {
        changeNotes.add(create(db, project, id));
      }
      return changeNotes;
    }

    public static Set<Change.Id> scan(Repository repo) throws IOException {
      Map<String, Ref> refs =
          repo.getRefDatabase().getRefs(RefNames.REFS_CHANGES);
      Set<Change.Id> ids = new HashSet<>(refs.size());
      for (Ref r : refs.values()) {
        Change.Id id = Change.Id.fromRef(r.getName());
        if (id != null) {
          ids.add(id);
        }
      }
      return ids;
    }
  }

  private final Project.NameKey project;
  private final Change change;
  private ImmutableSortedMap<PatchSet.Id, PatchSet> patchSets;
  private ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals;
  private ImmutableSetMultimap<ReviewerStateInternal, Account.Id> reviewers;
  private ImmutableList<Account.Id> allPastReviewers;
  private ImmutableList<SubmitRecord> submitRecords;
  private ImmutableList<ChangeMessage> allChangeMessages;
  private ImmutableListMultimap<PatchSet.Id, ChangeMessage> changeMessagesByPatchSet;
  private ImmutableListMultimap<RevId, PatchLineComment> comments;
  private ImmutableSet<String> hashtags;

  // Parsed note map state, used by ChangeUpdate to make in-place editing of
  // notes easier.
  RevisionNoteMap revisionNoteMap;

  private final AllUsersName allUsers;
  private DraftCommentNotes draftCommentNotes;

  @VisibleForTesting
  public ChangeNotes(GitRepositoryManager repoManager, NotesMigration migration,
      AllUsersName allUsers, Project.NameKey project,
      Change change) {
    super(repoManager, migration, change != null ? change.getId() : null);
    this.allUsers = allUsers;
    this.project = project;
    this.change = change != null ? new Change(change) : null;
  }

  public Change getChange() {
    return change;
  }

  public ImmutableMap<PatchSet.Id, PatchSet> getPatchSets() {
    return patchSets;
  }

  public ImmutableListMultimap<PatchSet.Id, PatchSetApproval> getApprovals() {
    return approvals;
  }

  public ImmutableSetMultimap<ReviewerStateInternal, Account.Id> getReviewers() {
    return reviewers;
  }

  /**
   *
   * @return a ImmutableSet of all hashtags for this change sorted in alphabetical order.
   */
  public ImmutableSet<String> getHashtags() {
    return ImmutableSortedSet.copyOf(hashtags);
  }

  /**
   * @return a list of all users who have ever been a reviewer on this change.
   */
  public ImmutableList<Account.Id> getAllPastReviewers() {
    return allPastReviewers;
  }

  /**
   * @return submit records stored during the most recent submit; only for
   *     changes that were actually submitted.
   */
  public ImmutableList<SubmitRecord> getSubmitRecords() {
    return submitRecords;
  }

  /** @return all change messages, in chronological order, oldest first. */
  public ImmutableList<ChangeMessage> getChangeMessages() {
    return allChangeMessages;
  }

  /**
   * @return change messages by patch set, in chronological order, oldest
   *     first.
   */
  public ImmutableListMultimap<PatchSet.Id, ChangeMessage>
      getChangeMessagesByPatchSet() {
    return changeMessagesByPatchSet;
  }

  /** @return inline comments on each revision. */
  public ImmutableListMultimap<RevId, PatchLineComment> getComments() {
    return comments;
  }

  public ImmutableListMultimap<RevId, PatchLineComment> getDraftComments(
      Account.Id author) throws OrmException {
    loadDraftComments(author);
    final Multimap<RevId, PatchLineComment> published = comments;
    // Filter out any draft comments that also exist in the published map, in
    // case the update to All-Users to delete them during the publish operation
    // failed.
    Multimap<RevId, PatchLineComment> filtered = Multimaps.filterEntries(
        draftCommentNotes.getComments(),
        new Predicate<Map.Entry<RevId, PatchLineComment>>() {
          @Override
          public boolean apply(Map.Entry<RevId, PatchLineComment> in) {
            for (PatchLineComment c : published.get(in.getKey())) {
              if (c.getKey().equals(in.getValue().getKey())) {
                return false;
              }
            }
            return true;
          }
        });
    return ImmutableListMultimap.copyOf(
        filtered);
  }

  /**
   * If draft comments have already been loaded for this author, then they will
   * not be reloaded. However, this method will load the comments if no draft
   * comments have been loaded or if the caller would like the drafts for
   * another author.
   */
  private void loadDraftComments(Account.Id author)
      throws OrmException {
    if (draftCommentNotes == null ||
        !author.equals(draftCommentNotes.getAuthor())) {
      draftCommentNotes = new DraftCommentNotes(repoManager, migration,
          allUsers, getChangeId(), author);
      draftCommentNotes.load();
    }
  }

  @VisibleForTesting
  DraftCommentNotes getDraftCommentNotes() {
    return draftCommentNotes;
  }

  public boolean containsComment(PatchLineComment c) throws OrmException {
    if (containsCommentPublished(c)) {
      return true;
    }
    loadDraftComments(c.getAuthor());
    return draftCommentNotes.containsComment(c);
  }

  public boolean containsCommentPublished(PatchLineComment c) {
    for (PatchLineComment l : getComments().values()) {
      if (c.getKey().equals(l.getKey())) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(getChangeId());
  }

  public PatchSet getCurrentPatchSet() {
    PatchSet.Id psId = change.currentPatchSetId();
    return checkNotNull(patchSets.get(psId),
        "missing current patch set %s", psId.get());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    ObjectId rev = getRevision();
    if (rev == null) {
      loadDefaults();
      return;
    }
    try (RevWalk walk = new RevWalk(reader);
        ChangeNotesParser parser = new ChangeNotesParser(project,
            change.getId(), rev, walk, repoManager)) {
      parser.parseAll();

      if (parser.status != null) {
        change.setStatus(parser.status);
      }
      approvals = parser.buildApprovals();
      changeMessagesByPatchSet = parser.buildMessagesByPatchSet();
      allChangeMessages = parser.buildAllMessages();
      comments = ImmutableListMultimap.copyOf(parser.comments);
      revisionNoteMap = parser.revisionNoteMap;
      change.setKey(new Change.Key(parser.changeId));
      change.setDest(new Branch.NameKey(project, parser.branch));
      change.setTopic(Strings.emptyToNull(parser.topic));
      change.setCreatedOn(parser.createdOn);
      change.setLastUpdatedOn(parser.lastUpdatedOn);
      change.setOwner(parser.ownerId);
      change.setSubmissionId(parser.submissionId);
      patchSets = ImmutableSortedMap.copyOf(
          parser.patchSets, ReviewDbUtil.intKeyOrdering());

      if (!patchSets.isEmpty()) {
        change.setCurrentPatchSet(
            parser.currentPatchSetId, parser.subject, parser.originalSubject);
      } else {
        // TODO(dborowitz): This should be an error, but for now it's required
        // for some tests to pass.
        change.clearCurrentPatchSet();
      }

      if (parser.hashtags != null) {
        hashtags = ImmutableSet.copyOf(parser.hashtags);
      } else {
        hashtags = ImmutableSet.of();
      }
      ImmutableSetMultimap.Builder<ReviewerStateInternal, Account.Id> reviewers =
          ImmutableSetMultimap.builder();
      for (Map.Entry<Account.Id, ReviewerStateInternal> e
          : parser.reviewers.entrySet()) {
        reviewers.put(e.getValue(), e.getKey());
      }
      this.reviewers = reviewers.build();
      this.allPastReviewers = ImmutableList.copyOf(parser.allPastReviewers);

      submitRecords = ImmutableList.copyOf(parser.submitRecords);
    }
  }

  @Override
  protected void loadDefaults() {
    approvals = ImmutableListMultimap.of();
    reviewers = ImmutableSetMultimap.of();
    submitRecords = ImmutableList.of();
    allChangeMessages = ImmutableList.of();
    changeMessagesByPatchSet = ImmutableListMultimap.of();
    comments = ImmutableListMultimap.of();
    hashtags = ImmutableSet.of();
  }

  @Override
  protected boolean onSave(CommitBuilder commit) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " is read-only");
  }

  @Override
  public Project.NameKey getProjectName() {
    return project;
  }
}
