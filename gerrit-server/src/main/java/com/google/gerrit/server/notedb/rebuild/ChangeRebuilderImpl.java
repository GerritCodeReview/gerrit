// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.ChangeDraftUpdate;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.OpenRepo;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.Result;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import com.google.gwtorm.client.Key;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public class ChangeRebuilderImpl extends ChangeRebuilder {
  /**
   * The maximum amount of time between the ReviewDb timestamp of the first and last events batched
   * together into a single NoteDb update.
   *
   * <p>Used to account for the fact that different records with their own timestamps (e.g. {@link
   * PatchSetApproval} and {@link ChangeMessage}) historically didn't necessarily use the same
   * timestamp, and tended to call {@code System.currentTimeMillis()} independently.
   */
  public static final long MAX_WINDOW_MS = SECONDS.toMillis(3);

  /**
   * The maximum amount of time between two consecutive events to consider them to be in the same
   * batch.
   */
  static final long MAX_DELTA_MS = SECONDS.toMillis(1);

  private final AccountCache accountCache;
  private final ChangeBundleReader bundleReader;
  private final ChangeDraftUpdate.Factory draftUpdateFactory;
  private final ChangeNoteUtil changeNoteUtil;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeUpdate.Factory updateFactory;
  private final CommentsUtil commentsUtil;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final NotesMigration migration;
  private final PatchListCache patchListCache;
  private final PersonIdent serverIdent;
  private final ProjectCache projectCache;
  private final String anonymousCowardName;
  private final String serverId;
  private final long skewMs;

  @Inject
  ChangeRebuilderImpl(
      @GerritServerConfig Config cfg,
      SchemaFactory<ReviewDb> schemaFactory,
      AccountCache accountCache,
      ChangeBundleReader bundleReader,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ChangeNoteUtil changeNoteUtil,
      ChangeNotes.Factory notesFactory,
      ChangeUpdate.Factory updateFactory,
      CommentsUtil commentsUtil,
      NoteDbUpdateManager.Factory updateManagerFactory,
      NotesMigration migration,
      PatchListCache patchListCache,
      @GerritPersonIdent PersonIdent serverIdent,
      @Nullable ProjectCache projectCache,
      @AnonymousCowardName String anonymousCowardName,
      @GerritServerId String serverId) {
    super(schemaFactory);
    this.accountCache = accountCache;
    this.bundleReader = bundleReader;
    this.draftUpdateFactory = draftUpdateFactory;
    this.changeNoteUtil = changeNoteUtil;
    this.notesFactory = notesFactory;
    this.updateFactory = updateFactory;
    this.commentsUtil = commentsUtil;
    this.updateManagerFactory = updateManagerFactory;
    this.migration = migration;
    this.patchListCache = patchListCache;
    this.serverIdent = serverIdent;
    this.projectCache = projectCache;
    this.anonymousCowardName = anonymousCowardName;
    this.serverId = serverId;
    this.skewMs = NoteDbChangeState.getReadOnlySkew(cfg);
  }

  @Override
  public Result rebuild(ReviewDb db, Change.Id changeId) throws IOException, OrmException {
    return rebuild(db, changeId, true);
  }

  @Override
  public Result rebuildEvenIfReadOnly(ReviewDb db, Change.Id changeId)
      throws IOException, OrmException {
    return rebuild(db, changeId, false);
  }

  private Result rebuild(ReviewDb db, Change.Id changeId, boolean checkReadOnly)
      throws IOException, OrmException {
    db = ReviewDbUtil.unwrapDb(db);
    // Read change just to get project; this instance is then discarded so we can read a consistent
    // ChangeBundle inside a transaction.
    Change change = db.changes().get(changeId);
    if (change == null) {
      throw new NoSuchChangeException(changeId);
    }
    try (NoteDbUpdateManager manager = updateManagerFactory.create(change.getProject())) {
      buildUpdates(manager, bundleReader.fromReviewDb(db, changeId));
      return execute(db, changeId, manager, checkReadOnly);
    }
  }

  @Override
  public Result rebuild(NoteDbUpdateManager manager, ChangeBundle bundle)
      throws NoSuchChangeException, IOException, OrmException {
    Change change = new Change(bundle.getChange());
    buildUpdates(manager, bundle);
    return manager.stageAndApplyDelta(change);
  }

  @Override
  public NoteDbUpdateManager stage(ReviewDb db, Change.Id changeId)
      throws IOException, OrmException {
    db = ReviewDbUtil.unwrapDb(db);
    Change change = checkNoteDbState(ChangeNotes.readOneReviewDbChange(db, changeId));
    if (change == null) {
      throw new NoSuchChangeException(changeId);
    }
    NoteDbUpdateManager manager = updateManagerFactory.create(change.getProject());
    buildUpdates(manager, bundleReader.fromReviewDb(db, changeId));
    manager.stage();
    return manager;
  }

  @Override
  public Result execute(ReviewDb db, Change.Id changeId, NoteDbUpdateManager manager)
      throws OrmException, IOException {
    return execute(db, changeId, manager, true);
  }

  public Result execute(
      ReviewDb db, Change.Id changeId, NoteDbUpdateManager manager, boolean checkReadOnly)
      throws OrmException, IOException {
    db = ReviewDbUtil.unwrapDb(db);
    Change change = checkNoteDbState(ChangeNotes.readOneReviewDbChange(db, changeId));
    if (change == null) {
      throw new NoSuchChangeException(changeId);
    }

    final String oldNoteDbState = change.getNoteDbState();
    Result r = manager.stageAndApplyDelta(change);
    final String newNoteDbState = change.getNoteDbState();
    try {
      db.changes()
          .atomicUpdate(
              changeId,
              new AtomicUpdate<Change>() {
                @Override
                public Change update(Change change) {
                  if (checkReadOnly) {
                    NoteDbChangeState.checkNotReadOnly(change, skewMs);
                  }
                  String currNoteDbState = change.getNoteDbState();
                  if (Objects.equals(currNoteDbState, newNoteDbState)) {
                    // Another thread completed the same rebuild we were about to.
                    throw new AbortUpdateException();
                  } else if (!Objects.equals(oldNoteDbState, currNoteDbState)) {
                    // Another thread updated the state to something else.
                    throw new ConflictingUpdateException(change, oldNoteDbState);
                  }
                  change.setNoteDbState(newNoteDbState);
                  return change;
                }
              });
    } catch (ConflictingUpdateException e) {
      // Rethrow as an OrmException so the caller knows to use staged results. Strictly speaking
      // they are not completely up to date, but result we send to the caller is the same as if this
      // rebuild had executed before the other thread.
      throw new OrmException(e.getMessage());
    } catch (AbortUpdateException e) {
      if (NoteDbChangeState.parse(changeId, newNoteDbState)
          .isUpToDate(
              manager.getChangeRepo().cmds.getRepoRefCache(),
              manager.getAllUsersRepo().cmds.getRepoRefCache())) {
        // If the state in ReviewDb matches NoteDb at this point, it means another thread
        // successfully completed this rebuild. It's ok to not execute the update in this case,
        // since the object referenced in the Result was flushed to the repo by whatever thread won
        // the race.
        return r;
      }
      // If the state doesn't match, that means another thread attempted this rebuild, but
      // failed. Fall through and try to update the ref again.
    }
    if (migration.failChangeWrites()) {
      // Don't even attempt to execute if read-only, it would fail anyway. But do throw an exception
      // to the caller so they know to use the staged results instead of reading from the repo.
      throw new OrmException(NoteDbUpdateManager.CHANGES_READ_ONLY);
    }
    manager.execute();
    return r;
  }

  private static Change checkNoteDbState(Change c) throws OrmException {
    // Can only rebuild a change if its primary storage is ReviewDb.
    NoteDbChangeState s = NoteDbChangeState.parse(c);
    if (s != null && s.getPrimaryStorage() != PrimaryStorage.REVIEW_DB) {
      throw new OrmException(
          String.format("cannot rebuild change " + c.getId() + " with state " + s));
    }
    return c;
  }

  @Override
  public void buildUpdates(NoteDbUpdateManager manager, ChangeBundle bundle)
      throws IOException, OrmException {
    manager.setCheckExpectedState(false).setRefLogMessage("Rebuilding change");
    Change change = new Change(bundle.getChange());
    if (bundle.getPatchSets().isEmpty()) {
      throw new NoPatchSetsException(change.getId());
    }

    // We will rebuild all events, except for draft comments, in buckets based on author and
    // timestamp.
    List<Event> events = new ArrayList<>();
    ListMultimap<Account.Id, DraftCommentEvent> draftCommentEvents =
        MultimapBuilder.hashKeys().arrayListValues().build();

    events.addAll(getHashtagsEvents(change, manager));

    // Delete ref only after hashtags have been read.
    deleteChangeMetaRef(change, manager.getChangeRepo().cmds);
    deleteDraftRefs(change, manager.getAllUsersRepo());

    Integer minPsNum = getMinPatchSetNum(bundle);
    TreeMap<PatchSet.Id, PatchSetEvent> patchSetEvents =
        new TreeMap<>(ReviewDbUtil.intKeyOrdering());

    for (PatchSet ps : bundle.getPatchSets()) {
      PatchSetEvent pse = new PatchSetEvent(change, ps, manager.getChangeRepo().rw);
      patchSetEvents.put(ps.getId(), pse);
      events.add(pse);
      for (Comment c : getComments(bundle, serverId, Status.PUBLISHED, ps)) {
        CommentEvent e = new CommentEvent(c, change, ps, patchListCache);
        events.add(e.addDep(pse));
      }
      for (Comment c : getComments(bundle, serverId, Status.DRAFT, ps)) {
        DraftCommentEvent e = new DraftCommentEvent(c, change, ps, patchListCache);
        draftCommentEvents.put(c.author.getId(), e);
      }
    }
    ensurePatchSetOrder(patchSetEvents);

    for (PatchSetApproval psa : bundle.getPatchSetApprovals()) {
      PatchSetEvent pse = patchSetEvents.get(psa.getPatchSetId());
      if (pse != null) {
        events.add(new ApprovalEvent(psa, change.getCreatedOn()).addDep(pse));
      }
    }

    for (Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> r :
        bundle.getReviewers().asTable().cellSet()) {
      events.add(new ReviewerEvent(r, change.getCreatedOn()));
    }

    Change noteDbChange = new Change(null, null, null, null, null);
    for (ChangeMessage msg : bundle.getChangeMessages()) {
      Event msgEvent = new ChangeMessageEvent(change, noteDbChange, msg, change.getCreatedOn());
      if (msg.getPatchSetId() != null) {
        PatchSetEvent pse = patchSetEvents.get(msg.getPatchSetId());
        if (pse == null) {
          continue; // Ignore events for missing patch sets.
        }
        msgEvent.addDep(pse);
      }
      events.add(msgEvent);
    }

    sortAndFillEvents(change, noteDbChange, bundle.getPatchSets(), events, minPsNum);

    EventList<Event> el = new EventList<>();
    for (Event e : events) {
      if (!el.canAdd(e)) {
        flushEventsToUpdate(manager, el, change);
        checkState(el.canAdd(e));
      }
      el.add(e);
    }
    flushEventsToUpdate(manager, el, change);

    EventList<DraftCommentEvent> plcel = new EventList<>();
    for (Account.Id author : draftCommentEvents.keys()) {
      for (DraftCommentEvent e : Ordering.natural().sortedCopy(draftCommentEvents.get(author))) {
        if (!plcel.canAdd(e)) {
          flushEventsToDraftUpdate(manager, plcel, change);
          checkState(plcel.canAdd(e));
        }
        plcel.add(e);
      }
      flushEventsToDraftUpdate(manager, plcel, change);
    }
  }

  private static Integer getMinPatchSetNum(ChangeBundle bundle) {
    Integer minPsNum = null;
    for (PatchSet ps : bundle.getPatchSets()) {
      int n = ps.getId().get();
      if (minPsNum == null || n < minPsNum) {
        minPsNum = n;
      }
    }
    return minPsNum;
  }

  private static void ensurePatchSetOrder(TreeMap<PatchSet.Id, PatchSetEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    Iterator<PatchSetEvent> it = events.values().iterator();
    PatchSetEvent curr = it.next();
    while (it.hasNext()) {
      PatchSetEvent next = it.next();
      next.addDep(curr);
      curr = next;
    }
  }

  private static List<Comment> getComments(
      ChangeBundle bundle, String serverId, PatchLineComment.Status status, PatchSet ps) {
    return bundle
        .getPatchLineComments()
        .stream()
        .filter(c -> c.getPatchSetId().equals(ps.getId()) && c.getStatus() == status)
        .map(plc -> plc.asComment(serverId))
        .sorted(CommentsUtil.COMMENT_ORDER)
        .collect(toList());
  }

  private void sortAndFillEvents(
      Change change,
      Change noteDbChange,
      ImmutableCollection<PatchSet> patchSets,
      List<Event> events,
      Integer minPsNum) {
    Event finalUpdates = new FinalUpdatesEvent(change, noteDbChange, patchSets);
    events.add(finalUpdates);
    setPostSubmitDeps(events);
    new EventSorter(events).sort();

    // Ensure the first event in the list creates the change, setting the author and any required
    // footers.
    Event first = events.get(0);
    if (first instanceof PatchSetEvent && change.getOwner().equals(first.user)) {
      ((PatchSetEvent) first).createChange = true;
    } else {
      events.add(0, new CreateChangeEvent(change, minPsNum));
    }

    // Final pass to correct some inconsistencies.
    //
    // First, fill in any missing patch set IDs using the latest patch set of the change at the time
    // of the event, because NoteDb can't represent actions with no associated patch set ID. This
    // workaround is as if a user added a ChangeMessage on the change by replying from the latest
    // patch set.
    //
    // Start with the first patch set that actually exists. If there are no patch sets at all,
    // minPsNum will be null, so just bail and use 1 as the patch set ID. The corresponding patch
    // set won't exist, but this change is probably corrupt anyway, as deleting the last draft patch
    // set should have deleted the whole change.
    //
    // Second, ensure timestamps are nondecreasing, by copying the previous timestamp if this
    // happens. This assumes that the only way this can happen is due to dependency constraints, and
    // it is ok to give an event the same timestamp as one of its dependencies.
    int ps = firstNonNull(minPsNum, 1);
    for (int i = 0; i < events.size(); i++) {
      Event e = events.get(i);
      if (e.psId == null) {
        e.psId = new PatchSet.Id(change.getId(), ps);
      } else {
        ps = Math.max(ps, e.psId.get());
      }

      if (i > 0) {
        Event p = events.get(i - 1);
        if (e.when.before(p.when)) {
          e.when = p.when;
        }
      }
    }
  }

  private void setPostSubmitDeps(List<Event> events) {
    Optional<Event> submitEvent =
        Lists.reverse(events).stream().filter(Event::isSubmit).findFirst();
    if (submitEvent.isPresent()) {
      events.stream().filter(Event::isPostSubmitApproval).forEach(e -> e.addDep(submitEvent.get()));
    }
  }

  private void flushEventsToUpdate(
      NoteDbUpdateManager manager, EventList<Event> events, Change change)
      throws OrmException, IOException {
    if (events.isEmpty()) {
      return;
    }
    Comparator<String> labelNameComparator;
    if (projectCache != null) {
      labelNameComparator = projectCache.get(change.getProject()).getLabelTypes().nameComparator();
    } else {
      // No project cache available, bail and use natural ordering; there's no semantic difference
      // anyway difference.
      labelNameComparator = Ordering.natural();
    }
    ChangeUpdate update =
        updateFactory.create(
            change,
            events.getAccountId(),
            events.getRealAccountId(),
            newAuthorIdent(events),
            events.getWhen(),
            labelNameComparator);
    update.setAllowWriteToNewRef(true);
    update.setPatchSetId(events.getPatchSetId());
    update.setTag(events.getTag());
    for (Event e : events) {
      e.apply(update);
    }
    manager.add(update);
    events.clear();
  }

  private void flushEventsToDraftUpdate(
      NoteDbUpdateManager manager, EventList<DraftCommentEvent> events, Change change)
      throws OrmException {
    if (events.isEmpty()) {
      return;
    }
    ChangeDraftUpdate update =
        draftUpdateFactory.create(
            change,
            events.getAccountId(),
            events.getRealAccountId(),
            newAuthorIdent(events),
            events.getWhen());
    update.setPatchSetId(events.getPatchSetId());
    for (DraftCommentEvent e : events) {
      e.applyDraft(update);
    }
    manager.add(update);
    events.clear();
  }

  private PersonIdent newAuthorIdent(EventList<?> events) {
    Account.Id id = events.getAccountId();
    if (id == null) {
      return new PersonIdent(serverIdent, events.getWhen());
    }
    return changeNoteUtil.newIdent(
        accountCache.get(id).getAccount(), events.getWhen(), serverIdent, anonymousCowardName);
  }

  private List<HashtagsEvent> getHashtagsEvents(Change change, NoteDbUpdateManager manager)
      throws IOException {
    String refName = changeMetaRef(change.getId());
    Optional<ObjectId> old = manager.getChangeRepo().getObjectId(refName);
    if (!old.isPresent()) {
      return Collections.emptyList();
    }

    RevWalk rw = manager.getChangeRepo().rw;
    List<HashtagsEvent> events = new ArrayList<>();
    rw.reset();
    rw.markStart(rw.parseCommit(old.get()));
    for (RevCommit commit : rw) {
      Account.Id authorId;
      try {
        authorId = changeNoteUtil.parseIdent(commit.getAuthorIdent(), change.getId());
      } catch (ConfigInvalidException e) {
        continue; // Corrupt data, no valid hashtags in this commit.
      }
      PatchSet.Id psId = parsePatchSetId(change, commit);
      Set<String> hashtags = parseHashtags(commit);
      if (authorId == null || psId == null || hashtags == null) {
        continue;
      }

      Timestamp commitTime = new Timestamp(commit.getCommitterIdent().getWhen().getTime());
      events.add(new HashtagsEvent(psId, authorId, commitTime, hashtags, change.getCreatedOn()));
    }
    return events;
  }

  private Set<String> parseHashtags(RevCommit commit) {
    List<String> hashtagsLines = commit.getFooterLines(FOOTER_HASHTAGS);
    if (hashtagsLines.isEmpty() || hashtagsLines.size() > 1) {
      return null;
    }

    if (hashtagsLines.get(0).isEmpty()) {
      return ImmutableSet.of();
    }
    return Sets.newHashSet(Splitter.on(',').split(hashtagsLines.get(0)));
  }

  private PatchSet.Id parsePatchSetId(Change change, RevCommit commit) {
    List<String> psIdLines = commit.getFooterLines(FOOTER_PATCH_SET);
    if (psIdLines.size() != 1) {
      return null;
    }
    Integer psId = Ints.tryParse(psIdLines.get(0));
    if (psId == null) {
      return null;
    }
    return new PatchSet.Id(change.getId(), psId);
  }

  private void deleteChangeMetaRef(Change change, ChainedReceiveCommands cmds) throws IOException {
    String refName = changeMetaRef(change.getId());
    Optional<ObjectId> old = cmds.get(refName);
    if (old.isPresent()) {
      cmds.add(new ReceiveCommand(old.get(), ObjectId.zeroId(), refName));
    }
  }

  private void deleteDraftRefs(Change change, OpenRepo allUsersRepo) throws IOException {
    for (Ref r :
        allUsersRepo
            .repo
            .getRefDatabase()
            .getRefs(RefNames.refsDraftCommentsPrefix(change.getId()))
            .values()) {
      allUsersRepo.cmds.add(new ReceiveCommand(r.getObjectId(), ObjectId.zeroId(), r.getName()));
    }
  }

  static void createChange(ChangeUpdate update, Change change) {
    update.setSubjectForCommit("Create change");
    update.setChangeId(change.getKey().get());
    update.setBranch(change.getDest().get());
    update.setSubject(change.getOriginalSubject());
  }

  @Override
  public void rebuildReviewDb(ReviewDb db, Project.NameKey project, Change.Id changeId)
      throws OrmException {
    // TODO(dborowitz): Fail fast if changes tables are disabled in ReviewDb.
    ChangeNotes notes = notesFactory.create(db, project, changeId);
    ChangeBundle bundle = ChangeBundle.fromNotes(commentsUtil, notes);

    db = ReviewDbUtil.unwrapDb(db);
    db.changes().beginTransaction(changeId);
    try {
      Change c = db.changes().get(changeId);
      PrimaryStorage ps = PrimaryStorage.of(c);
      if (ps != PrimaryStorage.NOTE_DB) {
        throw new OrmException("primary storage of " + changeId + " is " + ps);
      }
      db.changes().upsert(Collections.singleton(c));
      putExactlyEntities(
          db.changeMessages(), db.changeMessages().byChange(c.getId()), bundle.getChangeMessages());
      putExactlyEntities(db.patchSets(), db.patchSets().byChange(c.getId()), bundle.getPatchSets());
      putExactlyEntities(
          db.patchSetApprovals(),
          db.patchSetApprovals().byChange(c.getId()),
          bundle.getPatchSetApprovals());
      putExactlyEntities(
          db.patchComments(),
          db.patchComments().byChange(c.getId()),
          bundle.getPatchLineComments());
      db.commit();
    } finally {
      db.rollback();
    }
  }

  private static <T, K extends Key<?>> void putExactlyEntities(
      Access<T, K> access, Iterable<T> existing, Collection<T> ents) throws OrmException {
    Set<K> toKeep = access.toMap(ents).keySet();
    access.delete(
        FluentIterable.from(existing).filter(e -> !toKeep.contains(access.primaryKey(e))));
    access.upsert(ents);
  }
}
