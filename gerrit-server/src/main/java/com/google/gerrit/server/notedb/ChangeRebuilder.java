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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.PatchLineCommentsUtil.setCommentRevId;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ChangeRebuilder {
  private static final long TS_WINDOW_MS =
      TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);

  private final Provider<ReviewDb> dbProvider;
  private final ChangeControl.GenericFactory controlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final PatchListCache patchListCache;
  private final ChangeUpdate.Factory updateFactory;
  private final ChangeDraftUpdate.Factory draftUpdateFactory;

  @Inject
  ChangeRebuilder(Provider<ReviewDb> dbProvider,
      ChangeControl.GenericFactory controlFactory,
      IdentifiedUser.GenericFactory userFactory,
      PatchListCache patchListCache,
      ChangeUpdate.Factory updateFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory) {
    this.dbProvider = dbProvider;
    this.controlFactory = controlFactory;
    this.userFactory = userFactory;
    this.patchListCache = patchListCache;
    this.updateFactory = updateFactory;
    this.draftUpdateFactory = draftUpdateFactory;
  }

  public ListenableFuture<?> rebuildAsync(final Change change,
      ListeningExecutorService executor, final BatchRefUpdate bru,
      final BatchRefUpdate bruForDrafts, final Repository changeRepo,
      final Repository allUsersRepo) {
    return executor.submit(new Callable<Void>() {
        @Override
      public Void call() throws Exception {
        rebuild(change, bru, bruForDrafts, changeRepo, allUsersRepo);
        return null;
      }
    });
  }

  public void rebuild(Change change, BatchRefUpdate bru,
      BatchRefUpdate bruAllUsers, Repository changeRepo,
      Repository allUsersRepo) throws NoSuchChangeException, IOException,
      OrmException {
    deleteRef(change, changeRepo);
    ReviewDb db = dbProvider.get();
    Change.Id changeId = change.getId();

    // We will rebuild all events, except for draft comments, in buckets based
    // on author and timestamp. However, all draft comments for a given change
    // and author will be written as one commit in the notedb.
    List<Event> events = Lists.newArrayList();
    Multimap<Account.Id, PatchLineCommentEvent> draftCommentEvents =
        ArrayListMultimap.create();

    for (PatchSet ps : db.patchSets().byChange(changeId)) {
      events.add(new PatchSetEvent(ps));
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

    for (PatchSetApproval psa : db.patchSetApprovals().byChange(changeId)) {
      events.add(new ApprovalEvent(psa));
    }


    Collections.sort(events);
    BatchMetaDataUpdate batch = null;
    ChangeUpdate update = null;
    for (Event e : events) {
      if (!sameUpdate(e, update)) {
        if (update != null) {
          writeToBatch(batch, update, changeRepo);
        }
        IdentifiedUser user = userFactory.create(dbProvider, e.who);
        update = updateFactory.create(
            controlFactory.controlFor(change, user), e.when);
        update.setPatchSetId(e.psId);
        if (batch == null) {
          batch = update.openUpdateInBatch(bru);
        }
      }
      e.apply(update);
    }
    if (batch != null) {
      if (update != null) {
        writeToBatch(batch, update, changeRepo);
      }

      // Since the BatchMetaDataUpdates generated by all ChangeRebuilders on a
      // given project are backed by the same BatchRefUpdate, we need to
      // synchronize on the BatchRefUpdate. Therefore, since commit on a
      // BatchMetaDataUpdate is the only method that modifies a BatchRefUpdate,
      // we can just synchronize this call.
      synchronized (bru) {
        batch.commit();
      }
    }

    for (Account.Id author : draftCommentEvents.keys()) {
      IdentifiedUser user = userFactory.create(dbProvider, author);
      ChangeDraftUpdate draftUpdate = null;
      BatchMetaDataUpdate batchForDrafts = null;
      for (PatchLineCommentEvent e : draftCommentEvents.get(author)) {
        if (draftUpdate == null) {
          draftUpdate = draftUpdateFactory.create(
              controlFactory.controlFor(change, user), e.when);
          draftUpdate.setPatchSetId(e.psId);
          batchForDrafts = draftUpdate.openUpdateInBatch(bruAllUsers);
        }
        e.applyDraft(draftUpdate);
      }
      writeToBatch(batchForDrafts, draftUpdate, allUsersRepo);
      synchronized(bruAllUsers) {
        batchForDrafts.commit();
      }
    }

    createStarredChangesRefs(changeId, bruAllUsers, allUsersRepo);
  }

  private void createStarredChangesRefs(Change.Id changeId,
      BatchRefUpdate bruAllUsers, Repository allUsersRepo)
          throws IOException, OrmException {
    ObjectId id = StarredChangesUtil.writeLabels(allUsersRepo,
        StarredChangesUtil.DEFAULT_LABELS);
    for (StarredChange starred : dbProvider.get().starredChanges()
        .byChange(changeId)) {
      bruAllUsers.addCommand(new ReceiveCommand(ObjectId.zeroId(), id,
          RefNames.refsStarredChanges(starred.getAccountId(), changeId)));
    }
  }

  private void deleteRef(Change change, Repository changeRepo)
      throws IOException {
    String refName = ChangeNoteUtil.changeRefName(change.getId());
    RefUpdate ru = changeRepo.updateRef(refName, true);
    ru.setForceUpdate(true);
    RefUpdate.Result result = ru.delete();
    switch (result) {
      case FORCED:
      case NEW:
      case NO_CHANGE:
        break;
      default:
        throw new IOException(
            String.format("Failed to delete ref %s: %s", refName, result));
    }
  }

  private void writeToBatch(BatchMetaDataUpdate batch,
      AbstractChangeUpdate update, Repository repo) throws IOException,
      OrmException {
    try (ObjectInserter inserter = repo.newObjectInserter()) {
      update.setInserter(inserter);
      update.writeCommit(batch);
    }
  }

  private static long round(Date when) {
    return when.getTime() / TS_WINDOW_MS;
  }

  private static boolean sameUpdate(Event event, ChangeUpdate update) {
    return update != null
        && round(event.when) == round(update.getWhen())
        && event.who.equals(update.getUser().getAccountId())
        && event.psId.equals(update.getPatchSetId());
  }

  private abstract static class Event implements Comparable<Event> {
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

    abstract void apply(ChangeUpdate update) throws OrmException;

    @Override
    public int compareTo(Event other) {
      return ComparisonChain.start()
          // TODO(dborowitz): Smarter bucketing: pick a bucket start time T and
          // include all events up to T + TS_WINDOW_MS but no further.
          // Interleaving different authors complicates things.
          .compare(round(when), round(other.when))
          .compare(who.get(), other.who.get())
          .compare(psId.get(), other.psId.get())
          .result();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("psId", psId)
          .add("who", who)
          .add("when", when)
          .toString();
    }
  }

  private static class ApprovalEvent extends Event {
    private PatchSetApproval psa;

    ApprovalEvent(PatchSetApproval psa) {
      super(psa.getPatchSetId(), psa.getAccountId(), psa.getGranted());
      this.psa = psa;
    }

    @Override
    void apply(ChangeUpdate update) {
      checkUpdate(update);
      update.putApproval(psa.getLabel(), psa.getValue());
    }
  }

  private static class PatchSetEvent extends Event {
    private final PatchSet ps;

    PatchSetEvent(PatchSet ps) {
      super(ps.getId(), ps.getUploader(), ps.getCreatedOn());
      this.ps = ps;
    }

    @Override
    void apply(ChangeUpdate update) {
      checkUpdate(update);
      if (ps.getPatchSetId() == 1) {
        update.setSubject("Create change");
      } else {
        update.setSubject("Create patch set " + ps.getPatchSetId());
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
    void apply(ChangeUpdate update) throws OrmException {
      checkUpdate(update);
      if (c.getRevId() == null) {
        setCommentRevId(c, cache, change, ps);
      }
      update.insertComment(c);
    }

    void applyDraft(ChangeDraftUpdate draftUpdate) throws OrmException {
      if (c.getRevId() == null) {
        setCommentRevId(c, cache, change, ps);
      }
      draftUpdate.insertComment(c);
    }
  }
}
