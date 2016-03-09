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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.PatchLineCommentsUtil.setCommentRevId;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.git.ChainedReceiveCommands;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChangeRebuilder {
  private static final long TS_WINDOW_MS =
      TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final GitRepositoryManager repoManager;
  private final ChangeControl.GenericFactory controlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final InternalUser.Factory internalUserFactory;
  private final PatchListCache patchListCache;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeDraftUpdate.Factory draftUpdateFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeNoteUtil changeNoteUtil;

  @Inject
  ChangeRebuilder(SchemaFactory<ReviewDb> schemaFactory,
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory controlFactory,
      IdentifiedUser.GenericFactory userFactory,
      InternalUser.Factory internalUserFactory,
      PatchListCache patchListCache,
      ChangeUpdate.Factory updateFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeNoteUtil changeNoteUtil) {
    this.schemaFactory = schemaFactory;
    this.repoManager = repoManager;
    this.controlFactory = controlFactory;
    this.userFactory = userFactory;
    this.internalUserFactory = internalUserFactory;
    this.patchListCache = patchListCache;
    this.updateFactory = updateFactory;
    this.draftUpdateFactory = draftUpdateFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.changeNoteUtil = changeNoteUtil;
  }

  public ListenableFuture<?> rebuildAsync(final Change.Id id,
      ListeningExecutorService executor) {
    return executor.submit(new Callable<Void>() {
        @Override
      public Void call() throws Exception {
        try (ReviewDb db = schemaFactory.open()) {
          rebuild(db, id);
        }
        return null;
      }
    });
  }

  public void rebuild(ReviewDb db, Change.Id changeId)
      throws NoSuchChangeException, IOException, OrmException,
      ConfigInvalidException {
    Change change = db.changes().get(changeId);
    if (change == null) {
      return;
    }
    NoteDbUpdateManager manager =
        updateManagerFactory.create(change.getProject());

    // We will rebuild all events, except for draft comments, in buckets based
    // on author and timestamp.
    List<Event> events = Lists.newArrayList();
    Multimap<Account.Id, PatchLineCommentEvent> draftCommentEvents =
        ArrayListMultimap.create();

    Repository changeMetaRepo = manager.getChangeRepo();
    events.addAll(getHashtagsEvents(change, manager));

    // Delete ref only after hashtags have been read
    deleteRef(change, changeMetaRepo, manager.getChangeCommands());

    try (Repository codeRepo = repoManager.openRepository(change.getProject());
        RevWalk codeRw = new RevWalk(codeRepo)) {
      for (PatchSet ps : db.patchSets().byChange(changeId)) {
        events.add(new PatchSetEvent(change, ps, codeRw));
        for (PatchLineComment c : db.patchComments().byPatchSet(ps.getId())) {
          PatchLineCommentEvent e =
              new PatchLineCommentEvent(c, change, ps, patchListCache);
          if (c.getStatus() == Status.PUBLISHED) {
            events.add(e);
          } else {
            draftCommentEvents.put(c.getAuthor(), e);
          }
        }
      }
    }

    for (PatchSetApproval psa : db.patchSetApprovals().byChange(changeId)) {
      events.add(new ApprovalEvent(psa));
    }

    Change notedbChange = new Change(null, null, null, null, null);
    for (ChangeMessage msg : db.changeMessages().byChange(changeId)) {
      events.add(new ChangeMessageEvent(msg, notedbChange));
    }

    Collections.sort(events, EVENT_ORDER);

    events.add(new FinalUpdatesEvent(change, notedbChange));

    EventList<Event> el = new EventList<>();
    for (Event e : events) {
      if (!el.canAdd(e)) {
        flushEventsToUpdate(db, manager, el, change);
        checkState(el.canAdd(e));
      }
      el.add(e);
    }
    flushEventsToUpdate(db, manager, el, change);

    EventList<PatchLineCommentEvent> plcel = new EventList<>();
    for (Account.Id author : draftCommentEvents.keys()) {
      for (PatchLineCommentEvent e : draftCommentEvents.get(author)) {
        if (!plcel.canAdd(e)) {
          flushEventsToDraftUpdate(db, manager, plcel, change);
          checkState(plcel.canAdd(e));
        }
        plcel.add(e);
      }
      flushEventsToDraftUpdate(db, manager, plcel, change);
    }

    createStarredChangesRefs(db, changeId, manager.getAllUsersCommands(),
        manager.getAllUsersRepo());
    manager.execute();
  }

  private void flushEventsToUpdate(ReviewDb db, NoteDbUpdateManager manager,
      EventList<Event> events, Change change)
      throws NoSuchChangeException, OrmException, IOException {
    if (events.isEmpty()) {
      return;
    }
    ChangeUpdate update = updateFactory.create(
        controlFactory.controlFor(db, change, events.getUser(db)),
        events.getWhen());
    update.setPatchSetId(events.getPatchSetId());
    for (Event e : events) {
      e.apply(update);
    }
    manager.add(update);
    events.clear();
  }

  private void flushEventsToDraftUpdate(ReviewDb db, NoteDbUpdateManager manager,
      EventList<PatchLineCommentEvent> events, Change change)
      throws NoSuchChangeException, OrmException {
    if (events.isEmpty()) {
      return;
    }
    ChangeDraftUpdate update = draftUpdateFactory.create(
        controlFactory.controlFor(db, change, events.getUser(db)),
        events.getWhen());
    update.setPatchSetId(events.getPatchSetId());
    for (PatchLineCommentEvent e : events) {
      e.applyDraft(update);
    }
    manager.add(update);
    events.clear();
  }

  private void createStarredChangesRefs(ReviewDb db, Change.Id changeId,
      ChainedReceiveCommands allUsersCmds, Repository allUsersRepo)
          throws IOException, OrmException {
    ObjectId emptyTree = emptyTree(allUsersRepo);
    for (StarredChange starred : db.starredChanges().byChange(changeId)) {
      allUsersCmds.add(new ReceiveCommand(ObjectId.zeroId(), emptyTree,
          RefNames.refsStarredChanges(starred.getAccountId(), changeId)));
    }
  }

  private static ObjectId emptyTree(Repository repo) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
      oi.flush();
      return id;
    }
  }

  private List<HashtagsEvent> getHashtagsEvents(Change change,
      NoteDbUpdateManager manager) throws IOException, ConfigInvalidException {
    String refName = ChangeNoteUtil.changeRefName(change.getId());
    ObjectId old = manager.getChangeCommands()
        .getObjectId(manager.getChangeRepo(), refName);
    if (old == null) {
      return Collections.emptyList();
    }

    RevWalk rw = manager.getChangeRevWalk();
    List<HashtagsEvent> events = new ArrayList<>();
    rw.reset();
    rw.markStart(rw.parseCommit(old));
    for (RevCommit commit : rw) {
      Account.Id authorId =
          changeNoteUtil.parseIdent(commit.getAuthorIdent(), change.getId());
      PatchSet.Id psId = parsePatchSetId(change, commit);
      Set<String> hashtags = parseHashtags(commit);
      if (authorId == null || psId == null || hashtags == null) {
        continue;
      }

      Timestamp commitTime =
          new Timestamp(commit.getCommitterIdent().getWhen().getTime());
      events.add(new HashtagsEvent(psId, authorId, commitTime, hashtags));
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
    } else {
      return Sets.newHashSet(Splitter.on(',').split(hashtagsLines.get(0)));
    }
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

  private void deleteRef(Change change, Repository repo,
      ChainedReceiveCommands cmds) throws IOException {
    String refName = ChangeNoteUtil.changeRefName(change.getId());
    ObjectId old = cmds.getObjectId(repo, refName);
    if (old != null) {
      cmds.add(new ReceiveCommand(old, ObjectId.zeroId(), refName));
    }
  }

  private static final Ordering<Event> EVENT_ORDER = new Ordering<Event>() {
    @Override
    public int compare(Event a, Event b) {
      return ComparisonChain.start()
          .compare(a.when, b.when)
          .compare(a.who.get(), b.who.get())
          .compare(a.psId.get(), b.psId.get())
          .result();
    }
  };

  private abstract static class Event {
    // NOTE: EventList only supports direct subclasses, not an arbitrary
    // hierarchy.

    final PatchSet.Id psId;
    final Account.Id who;
    final Timestamp when;

    protected Event(PatchSet.Id psId, Account.Id who, Timestamp when) {
      this.psId = psId;
      this.who = who;
      this.when = when;
    }

    protected void checkUpdate(AbstractChangeUpdate update) {
      checkState(Objects.equals(update.getPatchSetId(), psId),
          "cannot apply event for %s to update for %s",
          update.getPatchSetId(), psId);
      checkState(when.getTime() - update.getWhen().getTime() <= TS_WINDOW_MS,
          "event at %s outside update window starting at %s",
          when, update.getWhen());
      checkState(Objects.equals(update.getUser().getAccountId(), who),
          "cannot apply event by %s to update by %s",
          who, update.getUser().getAccountId());
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

    private static final long MAX_DELTA_MS = 1000;
    private static final long MAX_WINDOW_MS = 5000;

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
          || !Objects.equals(e.psId, last.psId)) {
        return false; // Different patch set or author.
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
      PatchSet.Id id = get(0).psId;
      for (int i = 1; i < size(); i++) {
        checkState(Objects.equals(id, get(i).psId),
            "mismatched patch sets in EventList: %s != %s", id, get(i).psId);
      }
      return id;
    }

    CurrentUser getUser(ReviewDb db) {
      Account.Id id = get(0).who;
      for (int i = 1; i < size(); i++) {
        checkState(Objects.equals(id, get(i).who),
            "mismatched users in EventList: %s != %s", id, get(i).who);
      }

      return id != null
          ? userFactory.create(Providers.of(db), id)
          : internalUserFactory.create();
    }
  }

  private static class ApprovalEvent extends Event {
    private PatchSetApproval psa;

    ApprovalEvent(PatchSetApproval psa) {
      super(psa.getPatchSetId(), psa.getAccountId(), psa.getGranted());
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

  private static class PatchSetEvent extends Event {
    private final Change change;
    private final PatchSet ps;
    private final RevWalk rw;

    PatchSetEvent(Change change, PatchSet ps, RevWalk rw) {
      super(ps.getId(), ps.getUploader(), ps.getCreatedOn());
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
      update.setSubject(change.getSubject());
      if (ps.getPatchSetId() == 1) {
        update.setSubjectForCommit("Create change");
        update.setChangeId(change.getKey().get());
        update.setBranch(change.getDest().get());
      } else {
        update.setSubjectForCommit("Create patch set " + ps.getPatchSetId());
      }
      update.setCommit(rw, ObjectId.fromString(ps.getRevision().get()),
          ps.getPushCertificate());
      update.setGroups(ps.getGroups());
      if (ps.isDraft()) {
        update.setPatchSetState(PatchSetState.DRAFT);
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
      super(PatchLineCommentsUtil.getCommentPsId(c), c.getAuthor(), c.getWrittenOn());
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
        Set<String> hashtags) {
      super(psId, who, when);
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
    private final Change notedbChange;

    ChangeMessageEvent(ChangeMessage message, Change notedbChange) {
      super(message.getPatchSetId(), message.getAuthor(),
          message.getWrittenOn());
      this.message = message;
      this.notedbChange = notedbChange;
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
      Matcher m = TOPIC_SET_REGEXP.matcher(msg);
      if (m.matches()) {
        String topic = m.group(1);
        update.setTopic(topic);
        notedbChange.setTopic(topic);
        return;
      }

      m = TOPIC_CHANGED_REGEXP.matcher(msg);
      if (m.matches()) {
        String topic = m.group(2);
        update.setTopic(topic);
        notedbChange.setTopic(topic);
        return;
      }

      if (TOPIC_REMOVED_REGEXP.matcher(msg).matches()) {
        update.setTopic(null);
        notedbChange.setTopic(null);
      }
    }

    private void setStatus(ChangeUpdate update) {
      String msg = message.getMessage();
      if (STATUS_ABANDONED_REGEXP.matcher(msg).matches()) {
        update.setStatus(Change.Status.ABANDONED);
        notedbChange.setStatus(Change.Status.ABANDONED);
        return;
      }

      if (STATUS_RESTORED_REGEXP.matcher(msg).matches()) {
        update.setStatus(Change.Status.NEW);
        notedbChange.setStatus(Change.Status.NEW);
      }
    }
  }

  private static class FinalUpdatesEvent extends Event {
    private final Change change;
    private final Change notedbChange;

    FinalUpdatesEvent(Change change, Change notedbChange) {
      super(change.currentPatchSetId(), change.getOwner(),
          // TODO(dborowitz): This should maybe be a synthetic timestamp just
          // after the actual last update in the history. On the one hand using
          // the commit updated time is reasonable, but on the other it might be
          // non-monotonic, and who knows what would break then.
          change.getLastUpdatedOn());
      this.change = change;
      this.notedbChange = notedbChange;
    }

    @Override
    boolean uniquePerUpdate() {
      return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    void apply(ChangeUpdate update) throws OrmException {
      if (!Objects.equals(change.getTopic(), notedbChange.getTopic())) {
        update.setTopic(change.getTopic());
      }
      if (!Objects.equals(change.getStatus(), notedbChange.getStatus())) {
        // TODO(dborowitz): Stamp approximate approvals at this time.
        update.fixStatus(change.getStatus());
      }
      if (change.getSubmissionId() != null) {
        update.setSubmissionId(change.getSubmissionId());
      }
      if (!update.isEmpty()) {
        update.setSubjectForCommit("Final notedb migration updates");
      }
    }
  }
}
