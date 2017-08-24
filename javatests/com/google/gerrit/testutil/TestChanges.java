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

package com.google.gerrit.testutil;

import static com.google.common.base.MoreObjects.firstNonNull;
import static org.easymock.EasyMock.expect;

import com.google.common.collect.Ordering;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.AbstractChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Injector;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.easymock.EasyMock;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Utility functions to create and manipulate Change, ChangeUpdate, and ChangeControl objects for
 * testing.
 */
public class TestChanges {
  private static final AtomicInteger nextChangeId = new AtomicInteger(1);

  public static Change newChange(Project.NameKey project, Account.Id userId) {
    return newChange(project, userId, nextChangeId.getAndIncrement());
  }

  public static Change newChange(Project.NameKey project, Account.Id userId, int id) {
    Change.Id changeId = new Change.Id(id);
    Change c =
        new Change(
            new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
            changeId,
            userId,
            new Branch.NameKey(project, "master"),
            TimeUtil.nowTs());
    incrementPatchSet(c);
    return c;
  }

  public static PatchSet newPatchSet(PatchSet.Id id, ObjectId revision, Account.Id userId) {
    return newPatchSet(id, revision.name(), userId);
  }

  public static PatchSet newPatchSet(PatchSet.Id id, String revision, Account.Id userId) {
    PatchSet ps = new PatchSet(id);
    ps.setRevision(new RevId(revision));
    ps.setUploader(userId);
    ps.setCreatedOn(TimeUtil.nowTs());
    return ps;
  }

  public static ChangeUpdate newUpdate(Injector injector, Change c, CurrentUser user)
      throws Exception {
    injector =
        injector.createChildInjector(
            new FactoryModule() {
              @Override
              public void configure() {
                bind(CurrentUser.class).toInstance(user);
              }
            });
    ChangeUpdate update =
        injector
            .getInstance(ChangeUpdate.Factory.class)
            .create(
                stubChangeControl(injector.getInstance(AbstractChangeNotes.Args.class), c, user),
                TimeUtil.nowTs(),
                Ordering.<String>natural());

    ChangeNotes notes = update.getNotes();
    boolean hasPatchSets = notes.getPatchSets() != null && !notes.getPatchSets().isEmpty();
    NotesMigration migration = injector.getInstance(NotesMigration.class);
    if (hasPatchSets || !migration.readChanges()) {
      return update;
    }

    // Change doesn't exist yet. NoteDb requires that there be a commit for the
    // first patch set, so create one.
    GitRepositoryManager repoManager = injector.getInstance(GitRepositoryManager.class);
    try (Repository repo = repoManager.openRepository(c.getProject())) {
      TestRepository<Repository> tr = new TestRepository<>(repo);
      PersonIdent ident =
          user.asIdentifiedUser().newCommitterIdent(update.getWhen(), TimeZone.getDefault());
      TestRepository<Repository>.CommitBuilder cb =
          tr.commit()
              .author(ident)
              .committer(ident)
              .message(firstNonNull(c.getSubject(), "Test change"));
      Ref parent = repo.exactRef(c.getDest().get());
      if (parent != null) {
        cb.parent(tr.getRevWalk().parseCommit(parent.getObjectId()));
      }
      update.setBranch(c.getDest().get());
      update.setChangeId(c.getKey().get());
      update.setCommit(tr.getRevWalk(), cb.create());
      return update;
    }
  }

  private static ChangeControl stubChangeControl(
      AbstractChangeNotes.Args args, Change c, CurrentUser user) throws OrmException {
    ChangeControl ctl = EasyMock.createMock(ChangeControl.class);
    expect(ctl.getChange()).andStubReturn(c);
    expect(ctl.getProject()).andStubReturn(new Project(c.getProject()));
    expect(ctl.getUser()).andStubReturn(user);
    ChangeNotes notes = new ChangeNotes(args, c).load();
    expect(ctl.getNotes()).andStubReturn(notes);
    expect(ctl.getId()).andStubReturn(c.getId());
    EasyMock.replay(ctl);
    return ctl;
  }

  public static void incrementPatchSet(Change change) {
    PatchSet.Id curr = change.currentPatchSetId();
    PatchSetInfo ps =
        new PatchSetInfo(new PatchSet.Id(change.getId(), curr != null ? curr.get() + 1 : 1));
    ps.setSubject("Change subject");
    change.setCurrentPatchSet(ps);
  }
}
