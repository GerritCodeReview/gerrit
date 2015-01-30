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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gerrit.testutil.TestChanges;
import com.google.inject.util.Providers;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ConsistencyCheckerTest {
  private InMemoryDatabase schemaFactory;
  private ReviewDb db;
  private InMemoryRepositoryManager repoManager;
  private ConsistencyChecker checker;

  private TestRepository<InMemoryRepository> repo;
  private Project.NameKey project;
  private Account.Id userId;
  private RevCommit tip;

  @Before
  public void setUp() throws Exception {
    schemaFactory = InMemoryDatabase.newDatabase();
    schemaFactory.create();
    db = schemaFactory.open();
    repoManager = new InMemoryRepositoryManager();
    checker = new ConsistencyChecker(Providers.<ReviewDb> of(db), repoManager);
    project = new Project.NameKey("repo");
    repo = new TestRepository<>(repoManager.createRepository(project));
    userId = new Account.Id(1);
    db.accounts().insert(singleton(new Account(userId, TimeUtil.nowTs())));
    tip = repo.branch("master").commit().create();
  }

  @After
  public void tearDown() throws Exception {
    if (db != null) {
      db.close();
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

  @Test
  public void patchSetObjectMissing() throws Exception {
    Change c = insertChange();
    PatchSet ps = newPatchSet(c.currentPatchSetId(),
        ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"), userId);
    db.patchSets().insert(singleton(ps));

    assertProblems(c,
        "Object missing: patch set 1: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
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
    insertMissingPatchSet(c, rev);

    assertProblems(c,
        "Multiple patch sets pointing to " + rev + ": [1, 2]");
  }

  @Test
  public void missingDestRef() throws Exception {
    RefUpdate ru = repo.getRepository().updateRef("refs/heads/master");
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
    Change c = insertChange();
    RevCommit commit = repo.commit().create();
    PatchSet ps = newPatchSet(c.currentPatchSetId(), commit, userId);
    db.patchSets().insert(singleton(ps));

    assertProblems(c, "Destination ref not found (may be new branch): master");
  }

  @Test
  public void mergedChangeIsNotMerged() throws Exception {
    Change c = insertChange();
    c.setStatus(Change.Status.MERGED);
    PatchSet ps = insertPatchSet(c);
    String rev = ps.getRevision().get();

    assertProblems(c,
        "Patch set 1 (" + rev + ") is not merged into destination ref"
        + " master (" + tip.name() + "), but change status is MERGED");
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
        + " master (" + commit.name() + "), but change status is NEW");
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
        + " master (" + commit.name() + "), but change status is NEW");
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
    db.patchSets().insert(singleton(ps));
    return ps;
  }

  private PatchSet insertMissingPatchSet(Change c, String id) throws Exception {
    PatchSet ps = newPatchSet(c.currentPatchSetId(),
        ObjectId.fromString(id), userId);
    db.patchSets().insert(singleton(ps));
    return ps;
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
