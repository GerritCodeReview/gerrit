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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.server.PatchLineCommentsUtil.setCommentRevId;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.FormatUtil;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.ChainedReceiveCommands;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.OpenRepo;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.Result;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChangeRebuilderImpl extends ChangeRebuilder {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeRebuilderImpl.class);

  /**
   * The maximum amount of time between the ReviewDb timestamp of the first and
   * last events batched together into a single NoteDb update.
   * <p>
   * Used to account for the fact that different records with their own
   * timestamps (e.g. {@link PatchSetApproval} and {@link ChangeMessage})
   * historically didn't necessarily use the same timestamp, and tended to call
   * {@code System.currentTimeMillis()} independently.
   */
  static final long MAX_WINDOW_MS = SECONDS.toMillis(3);

  /**
   * The maximum amount of time between two consecutive events to consider them
   * to be in the same batch.
   */
  private static final long MAX_DELTA_MS = SECONDS.toMillis(1);

  private final AccountCache accountCache;
  private final ChangeDraftUpdate.Factory draftUpdateFactory;
  private final ChangeNoteUtil changeNoteUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final NotesMigration migration;
  private final PatchListCache patchListCache;
  private final PersonIdent serverIdent;
  private final ProjectCache projectCache;
  private final String anonymousCowardName;

  @Inject
  ChangeRebuilderImpl(SchemaFactory<ReviewDb> schemaFactory,
      AccountCache accountCache,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      ChangeNoteUtil changeNoteUtil,
      ChangeUpdate.Factory updateFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      NotesMigration migration,
      PatchListCache patchListCache,
      @GerritPersonIdent PersonIdent serverIdent,
      @Nullable ProjectCache projectCache,
      @AnonymousCowardName String anonymousCowardName) {
    super(schemaFactory);
    this.accountCache = accountCache;
    this.draftUpdateFactory = draftUpdateFactory;
    this.changeNoteUtil = changeNoteUtil;
    this.updateFactory = updateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.migration = migration;
    this.patchListCache = patchListCache;
    this.serverIdent = serverIdent;
    this.projectCache = projectCache;
    this.anonymousCowardName = anonymousCowardName;
  }

  @Override
  public Result rebuild(ReviewDb db, Change.Id changeId)
      throws NoSuchChangeException, IOException, OrmException,
      ConfigInvalidException {
    db = ReviewDbUtil.unwrapDb(db);
    Change change = db.changes().get(changeId);
    if (change == null) {
      throw new NoSuchChangeException(changeId);
    }
    try (NoteDbUpdateManager manager =
        updateManagerFactory.create(change.getProject())) {
      buildUpdates(manager, ChangeBundle.fromReviewDb(db, changeId));
      return execute(db, changeId, manager);
    }
  }

  private static class AbortUpdateException extends OrmRuntimeException {
    private static final long serialVersionUID = 1L;

    AbortUpdateException() {
      super("aborted");
    }
  }

  @Override
  public Result rebuild(NoteDbUpdateManager manager,
      ChangeBundle bundle) throws NoSuchChangeException, IOException,
      OrmException, ConfigInvalidException {
    Change change = new Change(bundle.getChange());
    buildUpdates(manager, bundle);
    return manager.stageAndApplyDelta(change);
  }

  @Override
  public NoteDbUpdateManager stage(ReviewDb db, Change.Id changeId)
      throws NoSuchChangeException, IOException, OrmException {
    db = ReviewDbUtil.unwrapDb(db);
    Change change = db.changes().get(changeId);
    if (change == null) {
      throw new NoSuchChangeException(changeId);
    }
    NoteDbUpdateManager manager =
        updateManagerFactory.create(change.getProject());
    buildUpdates(manager, ChangeBundle.fromReviewDb(db, changeId));
    manager.stage();
    return manager;
  }

  @Override
  public Result execute(ReviewDb db, Change.Id changeId,
      NoteDbUpdateManager manager) throws NoSuchChangeException, OrmException,
      IOException {
    db = ReviewDbUtil.unwrapDb(db);
    Change change = db.changes().get(changeId);
    if (change == null) {
      throw new NoSuchChangeException(changeId);
    }

    final String oldNoteDbState = change.getNoteDbState();
    Result r = manager.stageAndApplyDelta(change);
    final String newNoteDbState = change.getNoteDbState();
    try {
      db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (!Objects.equals(oldNoteDbState, change.getNoteDbState())) {
            throw new AbortUpdateException();
          }
          change.setNoteDbState(newNoteDbState);
          return change;
        }
      });
      if (!migration.failChangeWrites()) {
        manager.execute();
      } else {
        // Don't even attempt to execute if read-only, it would fail anyway. But
        // do throw an exception to the caller so they know to use the staged
        // results instead of reading from the repo.
        throw new OrmException(NoteDbUpdateManager.CHANGES_READ_ONLY);
      }
    } catch (AbortUpdateException e) {
      // Drop this rebuild; another thread completed it.
    }
    return r;
  }

  @Override
  public boolean rebuildProject(ReviewDb db,
      ImmutableMultimap<Project.NameKey, Change.Id> allChanges,
      Project.NameKey project, Repository allUsersRepo)
      throws NoSuchChangeException, IOException, OrmException,
      ConfigInvalidException {
    checkArgument(allChanges.containsKey(project));
    boolean ok = true;
    ProgressMonitor pm = new TextProgressMonitor(new PrintWriter(System.out));
    pm.beginTask(
        FormatUtil.elide(project.get(), 50), allChanges.get(project).size());
    try (NoteDbUpdateManager manager = updateManagerFactory.create(project);
        ObjectInserter allUsersInserter = allUsersRepo.newObjectInserter();
        RevWalk allUsersRw = new RevWalk(allUsersInserter.newReader())) {
      manager.setAllUsersRepo(allUsersRepo, allUsersRw, allUsersInserter,
          new ChainedReceiveCommands(allUsersRepo));
      for (Change.Id changeId : allChanges.get(project)) {
        try {
          buildUpdates(manager, ChangeBundle.fromReviewDb(db, changeId));
        } catch (Throwable t) {
          log.error("Failed to rebuild change " + changeId, t);
          ok = false;
        }
        pm.update(1);
      }
      manager.execute();
    } finally {
      pm.endTask();
    }
    return ok;
  }

  private void buildUpdates(NoteDbUpdateManager manager, ChangeBundle bundle)
      throws IOException, OrmException {
    manager.setCheckExpectedState(false);
    Change change = new Change(bundle.getChange());
    PatchSet.Id currPsId = change.currentPatchSetId();
    // We will rebuild all events, except for draft comments, in buckets based
    // on author and timestamp.
    List<Event> events = new ArrayList<>();
    Multimap<Account.Id, PatchLineCommentEvent> draftCommentEvents =
        ArrayListMultimap.create();

    events.addAll(getHashtagsEvents(change, manager));

    // Delete ref only after hashtags have been read
    deleteChangeMetaRef(change, manager.getChangeRepo().cmds);
    deleteDraftRefs(change, manager.getAllUsersRepo());

    Integer minPsNum = getMinPatchSetNum(bundle);
    Set<PatchSet.Id> psIds =
        Sets.newHashSetWithExpectedSize(bundle.getPatchSets().size());

    for (PatchSet ps : bundle.getPatchSets()) {
      if (ps.getId().get() > currPsId.get()) {
        log.info(
            "Skipping patch set {}, which is higher than current patch set {}",
            ps.getId(), currPsId);
        continue;
      }
      psIds.add(ps.getId());
      events.add(new PatchSetEvent(
          change, ps, manager.getChangeRepo().rw));
      for (PatchLineComment c : getPatchLineComments(bundle, ps)) {
        PatchLineCommentEvent e =
            new PatchLineCommentEvent(c, change, ps, patchListCache);
        if (c.getStatus() == Status.PUBLISHED) {
          events.add(e);
        } else {
          draftCommentEvents.put(c.getAuthor(), e);
        }
      }
    }

    for (PatchSetApproval psa : bundle.getPatchSetApprovals()) {
      if (psIds.contains(psa.getPatchSetId())) {
        events.add(new ApprovalEvent(psa, change.getCreatedOn()));
      }
    }

    for (Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> r :
        bundle.getReviewers().asTable().cellSet()) {
      events.add(new ReviewerEvent(r, change.getCreatedOn()));
    }

    Change noteDbChange = new Change(null, null, null, null, null);
    for (ChangeMessage msg : bundle.getChangeMessages()) {
      if (msg.getPatchSetId() == null || psIds.contains(msg.getPatchSetId())) {
        events.add(
            new ChangeMessageEvent(msg, noteDbChange, change.getCreatedOn()));
      }
    }

    sortAndFillEvents(change, noteDbChange, events, minPsNum);

    EventList<Event> el = new EventList<>();
    for (Event e : events) {
      if (!el.canAdd(e)) {
        flushEventsToUpdate(manager, el, change);
        checkState(el.canAdd(e));
      }
      el.add(e);
    }
    flushEventsToUpdate(manager, el, change);

    EventList<PatchLineCommentEvent> plcel = new EventList<>();
    for (Account.Id author : draftCommentEvents.keys()) {
      for (PatchLineCommentEvent e :
          EVENT_ORDER.sortedCopy(draftCommentEvents.get(author))) {
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

  private static List<PatchLineComment> getPatchLineComments(ChangeBundle bundle,
      final PatchSet ps) {
    return FluentIterable.from(bundle.getPatchLineComments())
        .filter(new Predicate<PatchLineComment>() {
          @Override
          public boolean apply(PatchLineComment in) {
            return in.getPatchSetId().equals(ps.getId());
          }
        }).toSortedList(PatchLineCommentsUtil.PLC_ORDER);
  }

  private void sortAndFillEvents(Change change, Change noteDbChange,
      List<Event> events, Integer minPsNum) {
    Collections.sort(events, EVENT_ORDER);
    events.add(new FinalUpdatesEvent(change, noteDbChange));

    // Ensure the first event in the list creates the change, setting the author
    // and any required footers.
    Event first = events.get(0);
    if (first instanceof PatchSetEvent && change.getOwner().equals(first.who)) {
      ((PatchSetEvent) first).createChange = true;
    } else {
      events.add(0, new CreateChangeEvent(change, minPsNum));
    }

    // Fill in any missing patch set IDs using the latest patch set of the
    // change at the time of the event, because NoteDb can't represent actions
    // with no associated patch set ID. This workaround is as if a user added a
    // ChangeMessage on the change by replying from the latest patch set.
    //
    // Start with the first patch set that actually exists. If there are no
    // patch sets at all, minPsNum will be null, so just bail and use 1 as the
    // patch set ID. The corresponding patch set won't exist, but this change is
    // probably corrupt anyway, as deleting the last draft patch set should have
    // deleted the whole change.
    int ps = firstNonNull(minPsNum, 1);
    for (Event e : events) {
      if (e.psId == null) {
        e.psId = new PatchSet.Id(change.getId(), ps);
      } else {
        ps = Math.max(ps, e.psId.get());
      }
    }
  }

  private void flushEventsToUpdate(NoteDbUpdateManager manager,
      EventList<Event> events, Change change) throws OrmException, IOException {
    if (events.isEmpty()) {
      return;
    }
    Comparator<String> labelNameComparator;
    if (projectCache != null) {
      labelNameComparator = projectCache.get(change.getProject())
          .getLabelTypes().nameComparator();
    } else {
      // No project cache available, bail and use natural ordering; there's no
      // semantic difference anyway difference.
      labelNameComparator = Ordering.natural();
    }
    ChangeUpdate update = updateFactory.create(
        change,
        events.getAccountId(),
        events.newAuthorIdent(),
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

  private void flushEventsToDraftUpdate(NoteDbUpdateManager manager,
      EventList<PatchLineCommentEvent> events, Change change)
      throws OrmException {
    if (events.isEmpty()) {
      return;
    }
    ChangeDraftUpdate update = draftUpdateFactory.create(
        change,
        events.getAccountId(),
        events.newAuthorIdent(),
        events.getWhen());
    update.setPatchSetId(events.getPatchSetId());
    for (PatchLineCommentEvent e : events) {
      e.applyDraft(update);
    }
    manager.add(update);
    events.clear();
  }

  private List<HashtagsEvent> getHashtagsEvents(Change change,
      NoteDbUpdateManager manager) throws IOException {
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
        authorId =
            changeNoteUtil.parseIdent(commit.getAuthorIdent(), change.getId());
      } catch (ConfigInvalidException e) {
        continue; // Corrupt data, no valid hashtags in this commit.
      }
      PatchSet.Id psId = parsePatchSetId(change, commit);
      Set<String> hashtags = parseHashtags(commit);
      if (authorId == null || psId == null || hashtags == null) {
        continue;
      }

      Timestamp commitTime =
          new Timestamp(commit.getCommitterIdent().getWhen().getTime());
      events.add(new HashtagsEvent(psId, authorId, commitTime, hashtags,
            change.getCreatedOn()));
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

  private void deleteChangeMetaRef(Change change, ChainedReceiveCommands cmds)
      throws IOException {
    String refName = changeMetaRef(change.getId());
    Optional<ObjectId> old = cmds.get(refName);
    if (old.isPresent()) {
      cmds.add(new ReceiveCommand(old.get(), ObjectId.zeroId(), refName));
    }
  }

  private void deleteDraftRefs(Change change, OpenRepo allUsersRepo)
      throws IOException {
    for (Ref r : allUsersRepo.repo.getRefDatabase()
        .getRefs(RefNames.refsDraftCommentsPrefix(change.getId())).values()) {
      allUsersRepo.cmds.add(
          new ReceiveCommand(r.getObjectId(), ObjectId.zeroId(), r.getName()));
    }
  }

  private static final Ordering<Event> EVENT_ORDER = new Ordering<Event>() {
    @Override
    public int compare(Event a, Event b) {
      return ComparisonChain.start()
          .compare(a.when, b.when)
          .compareTrueFirst(isPatchSet(a), isPatchSet(b))
          .compareTrueFirst(a.predatesChange, b.predatesChange)
          .compare(a.who, b.who, ReviewDbUtil.intKeyOrdering())
          .compare(a.psId, b.psId, ReviewDbUtil.intKeyOrdering().nullsLast())
          .result();
    }

    private boolean isPatchSet(Event e) {
      return e instanceof PatchSetEvent;
    }
  };

  private abstract static class Event {
    // NOTE: EventList only supports direct subclasses, not an arbitrary
    // hierarchy.

    final Account.Id who;
    final Timestamp when;
    final String tag;
    final boolean predatesChange;
    PatchSet.Id psId;

    protected Event(PatchSet.Id psId, Account.Id who, Timestamp when,
        Timestamp changeCreatedOn, String tag) {
      this.psId = psId;
      this.who = who;
      this.tag = tag;
      // Truncate timestamps at the change's createdOn timestamp.
      predatesChange = when.before(changeCreatedOn);
      this.when = predatesChange ? changeCreatedOn : when;
    }

    protected void checkUpdate(AbstractChangeUpdate update) {
      checkState(Objects.equals(update.getPatchSetId(), psId),
          "cannot apply event for %s to update for %s",
          update.getPatchSetId(), psId);
      checkState(when.getTime() - update.getWhen().getTime() <= MAX_WINDOW_MS,
          "event at %s outside update window starting at %s",
          when, update.getWhen());
      checkState(Objects.equals(update.getNullableAccountId(), who),
          "cannot apply event by %s to update by %s",
          who, update.getNullableAccountId());
    }

    /**
     * @return whether this event type must be unique per {@link ChangeUpdate},
     *     i.e. there may be at most one of this type.
     */
    abstract boolean uniquePerUpdate();

    abstract void apply(ChangeUpdate update) throws OrmException, IOException;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("psId", psId)
          .add("who", who)
          .add("when", when)
          .toString();
    }
  }

  private class EventList<E extends Event> extends ArrayList<E> {
    private static final long serialVersionUID = 1L;

    private E getLast() {
      return get(size() - 1);
    }

    private long getLastTime() {
      return getLast().when.getTime();
    }

    private long getFirstTime() {
      return get(0).when.getTime();
    }

    boolean canAdd(E e) {
      if (isEmpty()) {
        return true;
      }
      if (e instanceof FinalUpdatesEvent) {
        return false; // FinalUpdatesEvent always gets its own update.
      }

      Event last = getLast();
      if (!Objects.equals(e.who, last.who)
          || !e.psId.equals(last.psId)
          || !Objects.equals(e.tag, last.tag)) {
        return false; // Different patch set, author, or tag.
      }

      long t = e.when.getTime();
      long tFirst = getFirstTime();
      long tLast = getLastTime();
      checkArgument(t >= tLast,
          "event %s is before previous event in list %s", e, last);
      if (t - tLast > MAX_DELTA_MS || t - tFirst > MAX_WINDOW_MS) {
        return false; // Too much time elapsed.
      }

      if (!e.uniquePerUpdate()) {
        return true;
      }
      for (Event o : this) {
        if (e.getClass() == o.getClass()) {
          return false; // Only one event of this type allowed per update.
        }
      }

      // TODO(dborowitz): Additional heuristics, like keeping events separate if
      // they affect overlapping fields within a single entity.

      return true;
    }

    Timestamp getWhen() {
      return get(0).when;
    }

    PatchSet.Id getPatchSetId() {
      PatchSet.Id id = checkNotNull(get(0).psId);
      for (int i = 1; i < size(); i++) {
        checkState(get(i).psId.equals(id),
            "mismatched patch sets in EventList: %s != %s", id, get(i).psId);
      }
      return id;
    }

    Account.Id getAccountId() {
      Account.Id id = get(0).who;
      for (int i = 1; i < size(); i++) {
        checkState(Objects.equals(id, get(i).who),
            "mismatched users in EventList: %s != %s", id, get(i).who);
      }
      return id;
    }

    PersonIdent newAuthorIdent() {
      Account.Id id = getAccountId();
      if (id == null) {
        return new PersonIdent(serverIdent, getWhen());
      }
      return changeNoteUtil.newIdent(
          accountCache.get(id).getAccount(), getWhen(), serverIdent,
          anonymousCowardName);
    }

    String getTag() {
      return getLast().tag;
    }
  }

  private static void createChange(ChangeUpdate update, Change change) {
    update.setSubjectForCommit("Create change");
    update.setChangeId(change.getKey().get());
    update.setBranch(change.getDest().get());
    update.setSubject(change.getOriginalSubject());
  }

  private static class CreateChangeEvent extends Event {
    private final Change change;

    private static PatchSet.Id psId(Change change, Integer minPsNum) {
      int n;
      if (minPsNum == null) {
        // There were no patch sets for the change at all, so something is very
        // wrong. Bail and use 1 as the patch set.
        n = 1;
      } else {
        n = minPsNum;
      }
      return new PatchSet.Id(change.getId(), n);
    }

    CreateChangeEvent(Change change, Integer minPsNum) {
      super(psId(change, minPsNum), change.getOwner(), change.getCreatedOn(),
          change.getCreatedOn(), null);
      this.change = change;
    }

    @Override
    boolean uniquePerUpdate() {
      return true;
    }

    @Override
    void apply(ChangeUpdate update) throws IOException, OrmException {
      checkUpdate(update);
      createChange(update, change);
    }
  }

  private static class ApprovalEvent extends Event {
    private PatchSetApproval psa;

    ApprovalEvent(PatchSetApproval psa, Timestamp changeCreatedOn) {
      super(psa.getPatchSetId(), psa.getAccountId(), psa.getGranted(),
          changeCreatedOn, psa.getTag());
      this.psa = psa;
    }

    @Override
    boolean uniquePerUpdate() {
      return false;
    }

    @Override
    void apply(ChangeUpdate update) {
      checkUpdate(update);
      update.putApproval(psa.getLabel(), psa.getValue());
    }
  }

  private static class ReviewerEvent extends Event {
    private Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> reviewer;

    ReviewerEvent(
        Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> reviewer,
        Timestamp changeCreatedOn) {
      super(
          // Reviewers aren't generally associated with a particular patch set
          // (although as an implementation detail they were in ReviewDb). Just
          // use the latest patch set at the time of the event.
          null,
          reviewer.getColumnKey(), reviewer.getValue(), changeCreatedOn, null);
      this.reviewer = reviewer;
    }

    @Override
    boolean uniquePerUpdate() {
      return false;
    }

    @Override
    void apply(ChangeUpdate update) throws IOException, OrmException {
      checkUpdate(update);
      update.putReviewer(reviewer.getColumnKey(), reviewer.getRowKey());
    }
  }

  private static class PatchSetEvent extends Event {
    private final Change change;
    private final PatchSet ps;
    private final RevWalk rw;
    private boolean createChange;

    PatchSetEvent(Change change, PatchSet ps, RevWalk rw) {
      super(ps.getId(), ps.getUploader(), ps.getCreatedOn(),
          change.getCreatedOn(), null);
      this.change = change;
      this.ps = ps;
      this.rw = rw;
    }

    @Override
    boolean uniquePerUpdate() {
      return true;
    }

    @Override
    void apply(ChangeUpdate update) throws IOException, OrmException {
      checkUpdate(update);
      if (createChange) {
        createChange(update, change);
      } else {
        update.setSubject(change.getSubject());
        update.setSubjectForCommit("Create patch set " + ps.getPatchSetId());
      }
      setRevision(update, ps);
      List<String> groups = ps.getGroups();
      if (!groups.isEmpty()) {
        update.setGroups(ps.getGroups());
      }
      if (ps.isDraft()) {
        update.setPatchSetState(PatchSetState.DRAFT);
      }
    }

    private void setRevision(ChangeUpdate update, PatchSet ps)
        throws IOException {
      String rev = ps.getRevision().get();
      String cert = ps.getPushCertificate();
      ObjectId id;
      try {
        id = ObjectId.fromString(rev);
      } catch (InvalidObjectIdException e) {
        update.setRevisionForMissingCommit(rev, cert);
        return;
      }
      try {
        update.setCommit(rw, id, cert);
      } catch (MissingObjectException e) {
        update.setRevisionForMissingCommit(rev, cert);
        return;
      }
    }
  }

  private static class PatchLineCommentEvent extends Event {
    public final PatchLineComment c;
    private final Change change;
    private final PatchSet ps;
    private final PatchListCache cache;

    PatchLineCommentEvent(PatchLineComment c, Change change, PatchSet ps,
        PatchListCache cache) {
      super(PatchLineCommentsUtil.getCommentPsId(c), c.getAuthor(),
          c.getWrittenOn(), change.getCreatedOn(), c.getTag());
      this.c = c;
      this.change = change;
      this.ps = ps;
      this.cache = cache;
    }

    @Override
    boolean uniquePerUpdate() {
      return false;
    }

    @Override
    void apply(ChangeUpdate update) throws OrmException {
      checkUpdate(update);
      if (c.getRevId() == null) {
        setCommentRevId(c, cache, change, ps);
      }
      update.putComment(c);
    }

    void applyDraft(ChangeDraftUpdate draftUpdate) throws OrmException {
      if (c.getRevId() == null) {
        setCommentRevId(c, cache, change, ps);
      }
      draftUpdate.putComment(c);
    }
  }

  private static class HashtagsEvent extends Event {
    private final Set<String> hashtags;

    HashtagsEvent(PatchSet.Id psId, Account.Id who, Timestamp when,
        Set<String> hashtags, Timestamp changeCreatdOn) {
      super(psId, who, when, changeCreatdOn,
          // Somewhat confusingly, hashtags do not use the setTag method on
          // AbstractChangeUpdate, so pass null as the tag.
          null);
      this.hashtags = hashtags;
    }

    @Override
    boolean uniquePerUpdate() {
      // Since these are produced from existing commits in the old NoteDb graph,
      // we know that there must be one per commit in the rebuilt graph.
      return true;
    }

    @Override
    void apply(ChangeUpdate update) throws OrmException {
      update.setHashtags(hashtags);
    }
  }

  private static class ChangeMessageEvent extends Event {
    private static final Pattern TOPIC_SET_REGEXP =
        Pattern.compile("^Topic set to (.+)$");
    private static final Pattern TOPIC_CHANGED_REGEXP =
        Pattern.compile("^Topic changed from (.+) to (.+)$");
    private static final Pattern TOPIC_REMOVED_REGEXP =
        Pattern.compile("^Topic (.+) removed$");

    private static final Pattern STATUS_ABANDONED_REGEXP =
        Pattern.compile("^Abandoned(\n.*)*$");
    private static final Pattern STATUS_RESTORED_REGEXP =
        Pattern.compile("^Restored(\n.*)*$");

    private final ChangeMessage message;
    private final Change noteDbChange;

    ChangeMessageEvent(ChangeMessage message, Change noteDbChange,
        Timestamp changeCreatedOn) {
      super(message.getPatchSetId(), message.getAuthor(),
          message.getWrittenOn(), changeCreatedOn, message.getTag());
      this.message = message;
      this.noteDbChange = noteDbChange;
    }

    @Override
    boolean uniquePerUpdate() {
      return true;
    }

    @Override
    void apply(ChangeUpdate update) throws OrmException {
      checkUpdate(update);
      update.setChangeMessage(message.getMessage());
      setTopic(update);
      setStatus(update);
    }

    private void setTopic(ChangeUpdate update) {
      String msg = message.getMessage();
      if (msg == null) {
        return;
      }
      Matcher m = TOPIC_SET_REGEXP.matcher(msg);
      if (m.matches()) {
        String topic = m.group(1);
        update.setTopic(topic);
        noteDbChange.setTopic(topic);
        return;
      }

      m = TOPIC_CHANGED_REGEXP.matcher(msg);
      if (m.matches()) {
        String topic = m.group(2);
        update.setTopic(topic);
        noteDbChange.setTopic(topic);
        return;
      }

      if (TOPIC_REMOVED_REGEXP.matcher(msg).matches()) {
        update.setTopic(null);
        noteDbChange.setTopic(null);
      }
    }

    private void setStatus(ChangeUpdate update) {
      String msg = message.getMessage();
      if (msg == null) {
        return;
      }
      if (STATUS_ABANDONED_REGEXP.matcher(msg).matches()) {
        update.setStatus(Change.Status.ABANDONED);
        noteDbChange.setStatus(Change.Status.ABANDONED);
        return;
      }

      if (STATUS_RESTORED_REGEXP.matcher(msg).matches()) {
        update.setStatus(Change.Status.NEW);
        noteDbChange.setStatus(Change.Status.NEW);
      }
    }
  }

  private static class FinalUpdatesEvent extends Event {
    private final Change change;
    private final Change noteDbChange;

    FinalUpdatesEvent(Change change, Change noteDbChange) {
      super(change.currentPatchSetId(), change.getOwner(),
          change.getLastUpdatedOn(), change.getCreatedOn(), null);
      this.change = change;
      this.noteDbChange = noteDbChange;
    }

    @Override
    boolean uniquePerUpdate() {
      return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    void apply(ChangeUpdate update) throws OrmException {
      if (!Objects.equals(change.getTopic(), noteDbChange.getTopic())) {
        update.setTopic(change.getTopic());
      }
      if (!Objects.equals(change.getStatus(), noteDbChange.getStatus())) {
        // TODO(dborowitz): Stamp approximate approvals at this time.
        update.fixStatus(change.getStatus());
      }
      if (change.getSubmissionId() != null) {
        update.setSubmissionId(change.getSubmissionId());
      }
      if (!update.isEmpty()) {
        update.setSubjectForCommit("Final NoteDb migration updates");
      }
    }
  }
}
