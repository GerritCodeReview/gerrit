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
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeDraftUpdate;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Injector;

import org.easymock.EasyMock;
import org.eclipse.jgit.lib.ObjectId;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility functions to create and manipulate Change, ChangeUpdate, and
 * ChangeControl objects for testing.
 */
public class TestChanges {
  private static final AtomicInteger nextChangeId = new AtomicInteger(1);

  public static Change newChange(Project.NameKey project, Account.Id userId) {
    return newChange(project, userId, nextChangeId.getAndIncrement());
  }

  public static Change newChange(Project.NameKey project, Account.Id userId,
      int id) {
    Change.Id changeId = new Change.Id(id);
    Change c = new Change(
        new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
        changeId,
        userId,
        new Branch.NameKey(project, "master"),
        TimeUtil.nowTs());
    incrementPatchSet(c);
    return c;
  }

  public static PatchSet newPatchSet(PatchSet.Id id, ObjectId revision,
      Account.Id userId) {
    return newPatchSet(id, revision.name(), userId);
  }

  public static PatchSet newPatchSet(PatchSet.Id id, String revision,
      Account.Id userId) {
    PatchSet ps = new PatchSet(id);
    ps.setRevision(new RevId(revision));
    ps.setUploader(userId);
    ps.setCreatedOn(TimeUtil.nowTs());
    return ps;
  }

  public static ChangeUpdate newUpdate(Injector injector,
      GitRepositoryManager repoManager, NotesMigration migration, Change c,
      final AllUsersNameProvider allUsers, final IdentifiedUser user)
      throws OrmException {
    return injector.createChildInjector(new FactoryModule() {
      @Override
      public void configure() {
        factory(ChangeUpdate.Factory.class);
        factory(ChangeDraftUpdate.Factory.class);
        bind(IdentifiedUser.class).toInstance(user);
        bind(AllUsersName.class).toProvider(allUsers);
      }
    }).getInstance(ChangeUpdate.Factory.class).create(
        stubChangeControl(repoManager, migration, c, allUsers, user),
        TimeUtil.nowTs(), Ordering.<String> natural());
  }

  public static ChangeControl stubChangeControl(
      GitRepositoryManager repoManager, NotesMigration migration,
      Change c, AllUsersNameProvider allUsers,
      IdentifiedUser user) throws OrmException {
    ChangeControl ctl = EasyMock.createMock(ChangeControl.class);
    expect(ctl.getChange()).andStubReturn(c);
    expect(ctl.getUser()).andStubReturn(user);
    ChangeNotes notes = new ChangeNotes(repoManager, migration, allUsers, c)
        .load();
    expect(ctl.getNotes()).andStubReturn(notes);
    EasyMock.replay(ctl);
    return ctl;
  }

  public static void incrementPatchSet(Change change) {
    PatchSet.Id curr = change.currentPatchSetId();
    PatchSetInfo ps = new PatchSetInfo(new PatchSet.Id(
        change.getId(), curr != null ? curr.get() + 1 : 1));
    ps.setSubject("Change subject");
    change.setCurrentPatchSet(ps);
  }
}
