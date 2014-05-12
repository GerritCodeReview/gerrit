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

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ChangeRebuilder {
  private static final long TS_WINDOW_MS =
      TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);

  private final PersonIdent serverIdent;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final ChangeControl.GenericFactory controlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeUpdate.Factory updateFactory;

  @Inject
  ChangeRebuilder(@GerritPersonIdent PersonIdent serverIdent,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      ChangeControl.GenericFactory controlFactory,
      IdentifiedUser.GenericFactory userFactory,
      ChangeUpdate.Factory updateFactory) {
    this.serverIdent = serverIdent;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.controlFactory = controlFactory;
    this.userFactory = userFactory;
    this.updateFactory = updateFactory;
  }

  public ListenableFuture<?> rebuildAsync(
      final Change change, ListeningExecutorService executor) {
    return executor.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        rebuild(change);
        return null;
      }
    });
  }

  private void rebuild(Change change)
      throws NoSuchChangeException, IOException, OrmException {
    deleteRef(change);
    ReviewDb db = dbProvider.get();
    Change.Id changeId = change.getId();

    List<Event> events = Lists.newArrayList();
    for (PatchSet ps : db.patchSets().byChange(changeId)) {
      events.add(new PatchSetEvent(ps));
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
          writeToBatch(batch, update);
        }
        IdentifiedUser user = userFactory.create(dbProvider, e.who);
        update = updateFactory.create(
            controlFactory.controlFor(change, user), e.when);
        update.setPatchSetId(e.psId);
        if (batch == null) {
          batch = update.openUpdate();
        }
      }
      e.apply(update);
    }
    if (batch != null) {
      if (update != null) {
        writeToBatch(batch, update);
      }
      batch.commit();
    }
  }

  private void deleteRef(Change change) throws IOException {
    Repository repo =
        repoManager.openRepository(change.getDest().getParentKey());
    try {
      String refName = ChangeNoteUtil.changeRefName(change.getId());
      RefUpdate ru = repo.updateRef(refName, true);
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      switch (result) {
        case FORCED:
        case NEW:
        case NO_CHANGE:
          break;
        default:
          throw new IOException(String.format("Failed to delete ref %s: %s",
                refName, result));
      }
    } finally {
      repo.close();
    }
  }

  private void writeToBatch(BatchMetaDataUpdate batch, ChangeUpdate update)
      throws IOException {
    CommitBuilder commit = new CommitBuilder();
    commit.setCommitter(new PersonIdent(serverIdent, update.getWhen()));
    batch.write(update, commit);
  }

  private static long round(Date when) {
    return when.getTime() / TS_WINDOW_MS;
  }

  private static boolean sameUpdate(Event event, ChangeUpdate update) {
    return update != null
        && round(event.when) == round(update.getWhen())
        && event.who.equals(update.getUser())
        && event.psId.equals(update.getPatchSetId());
  }

  private static abstract class Event implements Comparable<Event> {
    final PatchSet.Id psId;
    final Account.Id who;
    final Timestamp when;

    protected Event(PatchSet.Id psId, Account.Id who, Timestamp when) {
      this.psId = psId;
      this.who = who;
      this.when = when;
    }

    protected void checkUpdate(ChangeUpdate update) {
      checkState(Objects.equal(update.getPatchSetId(), psId),
          "cannot apply event for %s to update for %s",
          update.getPatchSetId(), psId);
      checkState(when.getTime() - update.getWhen().getTime() <= TS_WINDOW_MS,
          "event at %s outside update window starting at %s",
          when, update.getWhen());
      checkState(Objects.equal(update.getUser().getAccountId(), who),
          "cannot apply event by %s to update by %s",
          who, update.getUser().getAccountId());
    }

    abstract void apply(ChangeUpdate update);

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
      return Objects.toStringHelper(this)
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
}
