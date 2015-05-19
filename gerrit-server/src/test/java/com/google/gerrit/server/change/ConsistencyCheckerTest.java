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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testutil.TestChanges.newChange;
import static com.google.gerrit.testutil.TestChanges.newPatchSet;
import static java.util.Collections.singleton;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.common.ProblemInfo;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.testutil.FakeAccountByEmailCache;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gerrit.testutil.InMemoryRepositoryManager.Repo;
import com.google.gerrit.testutil.TestChanges;
import com.google.inject.util.Providers;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ConsistencyCheckerTest {
  private LifecycleManager lifecycle;
  private InMemoryDatabase schemaFactory;
  private ReviewDb db;
  private InMemoryRepositoryManager repoManager;
  private ConsistencyChecker checker;

  private TestRepository<Repo> repo;
  private Project.NameKey project;
  private Account.Id userId;
  private RevCommit tip;

  @Before
  public void setUp() throws Exception {
    FakeAccountByEmailCache accountCache = new FakeAccountByEmailCache();
    lifecycle = new LifecycleManager();
    schemaFactory = InMemoryDatabase.newDatabase(lifecycle);
    lifecycle.start();
    schemaFactory.create();
    db = schemaFactory.open();
    repoManager = new InMemoryRepositoryManager();
    checker = new ConsistencyChecker(
        Providers.<ReviewDb> of(db),
        repoManager,
        Providers.<CurrentUser> of(new InternalUser(null)),
        Providers.of(new PersonIdent("server", "noreply@example.com")),
        new PatchSetInfoFactory(repoManager, accountCache));
    project = new Project.NameKey("repo");
    repo = new TestRepository<>(repoManager.createRepository(project));
    userId = new Account.Id(1);
    accountCache.putAny(userId);
    db.accounts().insert(singleton(new Account(userId, TimeUtil.nowTs())));
    tip = repo.branch("master").commit().create();
  }

  @After
  public void tearDown() throws Exception {
    if (db != null) {
      db.close();
    }
    if (lifecycle != null) {
      lifecycle.stop();
    }
    if (schemaFactory != null) {
      InMemoryDatabase.drop(schemaFactory);
    }
  }

  @Test
  public void validNewChange() throws Exception {
    Change c = insertChange();
    insertPatchSet(c);
    incrementPatchSet(c);
    insertPatchSet(c);
    assertProblems(c);
  }

  @Test
  public void validMergedChange() throws Exception {
    Change c = insertChange();
    c.setStatus(Change.Status.MERGED);
    insertPatchSet(c);
    incrementPatchSet(c);

    incrementPatchSet(c);
    RevCommit commit2 = repo.branch(c.currentPatchSetId().toRefName()).commit()
        .parent(tip).create();
    PatchSet ps2 = newPatchSet(c.currentPatchSetId(), commit2, userId);
    db.patchSets().insert(singleton(ps2));

    repo.branch(c.getDest().get()).update(commit2);
    assertProblems(c);
  }

  @Test
  public void missingOwner() throws Exception {
    Change c = newChange(project, new Account.Id(2));
    db.changes().insert(singleton(c));
    RevCommit commit = repo.branch(c.currentPatchSetId().toRefName()).commit()
        .parent(tip).create();
    PatchSet ps = newPatchSet(c.currentPatchSetId(), commit, userId);
    db.patchSets().insert(singleton(ps));

    assertProblems(c, "Missing change owner: 2");
  }

  @Test
  public void missingRepo() throws Exception {
    Change c = newChange(new Project.NameKey("otherproject"), userId);
    db.changes().insert(singleton(c));
    insertMissingPatchSet(c, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    assertProblems(c, "Destination repository not found: otherproject");
  }

  @Test
  public void invalidRevision() throws Exception {
    Change c = insertChange();

    db.patchSets().insert(singleton(newPatchSet(c.currentPatchSetId(),
            "fooooooooooooooooooooooooooooooooooooooo", userId)));
    incrementPatchSet(c);
    insertPatchSet(c);

    assertProblems(c,
        "Invalid revision on patch set 1:"
        + " fooooooooooooooooooooooooooooooooooooooo");
  }

  // No test for ref existing but object missing; InMemoryRepository won't let
  // us do such a thing.

  @Test
  public void patchSetObjectAndRefMissing() throws Exception {
    Change c = insertChange();
    PatchSet ps = newPatchSet(c.currentPatchSetId(),
        ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"), userId);
    db.patchSets().insert(singleton(ps));

    assertProblems(c,
        "Ref missing: " + ps.getId().toRefName(),
        "Object missing: patch set 1: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
  }

  @Test
  public void patchSetObjectAndRefMissingWithFix() throws Exception {
    Change c = insertChange();
    PatchSet ps = newPatchSet(c.currentPatchSetId(),
        ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"), userId);
    db.patchSets().insert(singleton(ps));

    String refName = ps.getId().toRefName();
    List<ProblemInfo> problems = checker.check(c, new FixInput()).problems();
    ProblemInfo p = problems.get(0);
    assertThat(p.message).isEqualTo("Ref missing: " + refName);
    assertThat(p.status).isNull();
  }

  @Test
  public void patchSetRefMissing() throws Exception {
    Change c = insertChange();
    PatchSet ps = insertPatchSet(c);
    String refName = ps.getId().toRefName();
    repo.update("refs/other/foo", ObjectId.fromString(ps.getRevision().get()));
    deleteRef(refName);

    assertProblems(c, "Ref missing: " + refName);
  }

  @Test
  public void patchSetRefMissingWithFix() throws Exception {
    Change c = insertChange();
    PatchSet ps = insertPatchSet(c);
    String refName = ps.getId().toRefName();
    repo.update("refs/other/foo", ObjectId.fromString(ps.getRevision().get()));
    deleteRef(refName);

    List<ProblemInfo> problems = checker.check(c, new FixInput()).problems();
    ProblemInfo p = problems.get(0);
    assertThat(p.message).isEqualTo("Ref missing: " + refName);
    assertThat(p.status).isEqualTo(ProblemInfo.Status.FIXED);
    assertThat(p.outcome).isEqualTo("Repaired patch set ref");

    assertThat(repo.getRepository().getRef(refName).getObjectId().name())
        .isEqualTo(ps.getRevision().get());
  }

  @Test
  public void patchSetObjectAndRefMissingWithDeletingPatchSet()
      throws Exception {
    Change c = insertChange();
    PatchSet ps1 = insertPatchSet(c);
    incrementPatchSet(c);
    PatchSet ps2 = insertMissingPatchSet(c,
        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    List<ProblemInfo> problems = checker.check(c, fix).problems();
    assertThat(problems).hasSize(2);
    ProblemInfo p = problems.get(0);
    assertThat(p.message).isEqualTo("Ref missing: " + ps2.getId().toRefName());
    assertThat(p.status).isNull();
    p = problems.get(1);
    assertThat(p.message).isEqualTo(
        "Object missing: patch set 2: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    assertThat(p.status).isEqualTo(ProblemInfo.Status.FIXED);
    assertThat(p.outcome).isEqualTo("Deleted patch set");

    c = db.changes().get(c.getId());
    assertThat(c.currentPatchSetId().get()).isEqualTo(1);
    assertThat(db.patchSets().get(ps1.getId())).isNotNull();
    assertThat(db.patchSets().get(ps2.getId())).isNull();
  }

  @Test
  public void patchSetMultipleObjectsMissingWithDeletingPatchSets()
      throws Exception {
    Change c = insertChange();
    PatchSet ps1 = insertPatchSet(c);

    incrementPatchSet(c);
    PatchSet ps2 = insertMissingPatchSet(c,
        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

    incrementPatchSet(c);
    PatchSet ps3 = insertPatchSet(c);

    incrementPatchSet(c);
    PatchSet ps4 = insertMissingPatchSet(c,
        "c0ffeeeec0ffeeeec0ffeeeec0ffeeeec0ffeeee");

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    List<ProblemInfo> problems = checker.check(c, fix).problems();
    assertThat(problems).hasSize(4);

    ProblemInfo p = problems.get(0);
    assertThat(p.message).isEqualTo("Ref missing: " + ps4.getId().toRefName());
    assertThat(p.status).isNull();

    p = problems.get(1);
    assertThat(p.message).isEqualTo(
        "Object missing: patch set 4: c0ffeeeec0ffeeeec0ffeeeec0ffeeeec0ffeeee");
    assertThat(p.status).isEqualTo(ProblemInfo.Status.FIXED);
    assertThat(p.outcome).isEqualTo("Deleted patch set");

    p = problems.get(2);
    assertThat(p.message).isEqualTo("Ref missing: " + ps2.getId().toRefName());
    assertThat(p.status).isNull();

    p = problems.get(3);
    assertThat(p.message).isEqualTo(
        "Object missing: patch set 2: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    assertThat(p.status).isEqualTo(ProblemInfo.Status.FIXED);
    assertThat(p.outcome).isEqualTo("Deleted patch set");

    c = db.changes().get(c.getId());
    assertThat(c.currentPatchSetId().get()).isEqualTo(3);
    assertThat(db.patchSets().get(ps1.getId())).isNotNull();
    assertThat(db.patchSets().get(ps2.getId())).isNull();
    assertThat(db.patchSets().get(ps3.getId())).isNotNull();
    assertThat(db.patchSets().get(ps4.getId())).isNull();
  }

  @Test
  public void onlyPatchSetObjectMissingWithFix() throws Exception {
    Change c = insertChange();
    PatchSet ps1 = insertMissingPatchSet(c,
        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    List<ProblemInfo> problems = checker.check(c, fix).problems();
    assertThat(problems).hasSize(2);
    ProblemInfo p = problems.get(0);
    assertThat(p.message).isEqualTo("Ref missing: " + ps1.getId().toRefName());
    assertThat(p.status).isNull();
    p = problems.get(1);
    assertThat(p.message).isEqualTo(
        "Object missing: patch set 1: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    assertThat(p.status).isEqualTo(ProblemInfo.Status.FIX_FAILED);
    assertThat(p.outcome)
        .isEqualTo("Cannot delete patch set; no patch sets would remain");

    c = db.changes().get(c.getId());
    assertThat(c.currentPatchSetId().get()).isEqualTo(1);
    assertThat(db.patchSets().get(ps1.getId())).isNotNull();
  }

  @Test
  public void currentPatchSetMissing() throws Exception {
    Change c = insertChange();
    assertProblems(c, "Current patch set 1 not found");
  }

  @Test
  public void duplicatePatchSetRevisions() throws Exception {
    Change c = insertChange();
    PatchSet ps1 = insertPatchSet(c);
    String rev = ps1.getRevision().get();
    incrementPatchSet(c);
    PatchSet ps2 = insertMissingPatchSet(c, rev);
    updatePatchSetRef(ps2);

    assertProblems(c,
        "Multiple patch sets pointing to " + rev + ": [1, 2]");
  }

  @Test
  public void missingDestRef() throws Exception {
    String ref = "refs/heads/master";
    RefUpdate ru = repo.getRepository().updateRef(ref);
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
    Change c = insertChange();
    RevCommit commit = repo.commit().create();
    PatchSet ps = newPatchSet(c.currentPatchSetId(), commit, userId);
    updatePatchSetRef(ps);
    db.patchSets().insert(singleton(ps));

    assertProblems(c, "Destination ref not found (may be new branch): " + ref);
  }

  @Test
  public void mergedChangeIsNotMerged() throws Exception {
    Change c = insertChange();
    c.setStatus(Change.Status.MERGED);
    PatchSet ps = insertPatchSet(c);
    String rev = ps.getRevision().get();

    assertProblems(c,
        "Patch set 1 (" + rev + ") is not merged into destination ref"
        + " refs/heads/master (" + tip.name()
        + "), but change status is MERGED");
  }

  @Test
  public void newChangeIsMerged() throws Exception {
    Change c = insertChange();
    RevCommit commit = repo.branch(c.currentPatchSetId().toRefName()).commit()
        .parent(tip).create();
    PatchSet ps = newPatchSet(c.currentPatchSetId(), commit, userId);
    db.patchSets().insert(singleton(ps));
    repo.branch(c.getDest().get()).update(commit);

    assertProblems(c,
        "Patch set 1 (" + commit.name() + ") is merged into destination ref"
        + " refs/heads/master (" + commit.name()
        + "), but change status is NEW");
  }

  @Test
  public void newChangeIsMergedWithFix() throws Exception {
    Change c = insertChange();
    RevCommit commit = repo.branch(c.currentPatchSetId().toRefName()).commit()
        .parent(tip).create();
    PatchSet ps = newPatchSet(c.currentPatchSetId(), commit, userId);
    db.patchSets().insert(singleton(ps));
    repo.branch(c.getDest().get()).update(commit);

    List<ProblemInfo> problems = checker.check(c, new FixInput()).problems();
    assertThat(problems).hasSize(1);
    ProblemInfo p = problems.get(0);
    assertThat(p.message).isEqualTo(
        "Patch set 1 (" + commit.name() + ") is merged into destination ref"
        + " refs/heads/master (" + commit.name()
        + "), but change status is NEW");
    assertThat(p.status).isEqualTo(ProblemInfo.Status.FIXED);
    assertThat(p.outcome).isEqualTo("Marked change as merged");

    c = db.changes().get(c.getId());
    assertThat(c.getStatus()).isEqualTo(Change.Status.MERGED);
    assertProblems(c);
  }

  private Change insertChange() throws Exception {
    Change c = newChange(project, userId);
    db.changes().insert(singleton(c));
    return c;
  }

  private void incrementPatchSet(Change c) throws Exception {
    TestChanges.incrementPatchSet(c);
    db.changes().upsert(singleton(c));
  }

  private PatchSet insertPatchSet(Change c) throws Exception {
    db.changes().upsert(singleton(c));
    RevCommit commit = repo.branch(c.currentPatchSetId().toRefName()).commit()
        .parent(tip).create();
    PatchSet ps = newPatchSet(c.currentPatchSetId(), commit, userId);
    updatePatchSetRef(ps);
    db.patchSets().insert(singleton(ps));
    return ps;
  }

  private PatchSet insertMissingPatchSet(Change c, String id) throws Exception {
    PatchSet ps = newPatchSet(c.currentPatchSetId(),
        ObjectId.fromString(id), userId);
    db.patchSets().insert(singleton(ps));
    return ps;
  }

  private void updatePatchSetRef(PatchSet ps) throws Exception {
    repo.update(ps.getId().toRefName(),
        ObjectId.fromString(ps.getRevision().get()));
  }

  private void deleteRef(String refName) throws Exception {
    RefUpdate ru = repo.getRepository().updateRef(refName, true);
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
  }

  private void assertProblems(Change c, String... expected) {
    assertThat(Lists.transform(checker.check(c).problems(),
          new Function<ProblemInfo, String>() {
            @Override
            public String apply(ProblemInfo in) {
              checkArgument(in.status == null,
                  "Status is not null: " + in.message);
              return in.message;
            }
          })).containsExactly((Object[]) expected);
  }
}
