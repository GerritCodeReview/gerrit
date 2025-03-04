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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.ProblemInfo.Status.FIXED;
import static com.google.gerrit.extensions.common.ProblemInfo.Status.FIX_FAILED;
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static com.google.gerrit.testing.TestChanges.newPatchSet;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ProblemInfo;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ConsistencyChecker;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestChanges;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ConsistencyCheckerIT extends AbstractDaemonTest {
  @Inject private ChangeNotes.Factory changeNotesFactory;

  @Inject private Provider<ConsistencyChecker> checkerProvider;

  @Inject private IdentifiedUser.GenericFactory userFactory;

  @Inject private ChangeInserter.Factory changeInserterFactory;

  @Inject private PatchSetInserter.Factory patchSetInserterFactory;

  @Inject private ChangeNoteUtil noteUtil;

  @Inject private Sequences sequences;

  private RevCommit tip;
  private Account.Id adminId;
  private ConsistencyChecker checker;
  private TestRepository<Repository> serverSideTestRepo;

  @Before
  public void setUp() throws Exception {
    serverSideTestRepo = new TestRepository<>(repoManager.openRepository(project));
    tip =
        serverSideTestRepo
            .getRevWalk()
            .parseCommit(serverSideTestRepo.getRepository().exactRef("HEAD").getObjectId());
    adminId = admin.id();
    checker = checkerProvider.get();
  }

  @After
  public void tearDown() {
    serverSideTestRepo.close();
  }

  @Test
  public void validNewChange() throws Exception {
    assertNoProblems(insertChange(), null);
  }

  @Test
  public void validMergedChange() throws Exception {
    ChangeNotes notes = mergeChange(incrementPatchSet(insertChange()));
    assertNoProblems(notes, null);
  }

  @Test
  public void missingOwner() throws Exception {
    TestAccount owner = accountCreator.create("missing");
    ChangeNotes notes = insertChange(owner);
    deleteUserBranch(owner.id());

    assertProblems(notes, null, problem("Missing change owner: " + owner.id()));
  }

  // No test for ref existing but object missing; InMemoryRepository won't let
  // us do such a thing.

  @Test
  public void patchSetObjectAndRefMissing() throws Exception {
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    ChangeNotes notes = insertChange();
    PatchSet ps = insertMissingPatchSet(notes, rev);
    notes = reload(notes);
    assertProblems(
        notes,
        null,
        problem("Ref missing: " + ps.id().toRefName()),
        problem("Object missing: patch set 2: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
  }

  @Test
  public void patchSetObjectAndRefMissingWithFix() throws Exception {
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    ChangeNotes notes = insertChange();
    PatchSet ps = insertMissingPatchSet(notes, rev);
    notes = reload(notes);

    String refName = ps.id().toRefName();
    assertProblems(
        notes,
        new FixInput(),
        problem("Ref missing: " + refName),
        problem("Object missing: patch set 2: " + rev));
  }

  @Test
  public void patchSetRefMissing() throws Exception {
    ChangeNotes notes = insertChange();
    serverSideTestRepo.update("refs/other/foo", psUtil.current(notes).commitId());
    String refName = notes.getChange().currentPatchSetId().toRefName();
    deleteRef(refName);

    assertProblems(notes, null, problem("Ref missing: " + refName));
  }

  @Test
  public void patchSetRefMissingWithFix() throws Exception {
    ChangeNotes notes = insertChange();
    ObjectId commitId = psUtil.current(notes).commitId();
    serverSideTestRepo.update("refs/other/foo", commitId);
    String refName = notes.getChange().currentPatchSetId().toRefName();
    deleteRef(refName);

    assertProblems(
        notes, new FixInput(), problem("Ref missing: " + refName, FIXED, "Repaired patch set ref"));
    assertThat(serverSideTestRepo.getRepository().exactRef(refName).getObjectId())
        .isEqualTo(commitId);
  }

  @Test
  public void patchSetObjectAndRefMissingWithDeletingPatchSet() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(notes);

    String rev2 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps2 = insertMissingPatchSet(notes, rev2);
    notes = reload(notes);

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        notes,
        fix,
        problem("Ref missing: " + ps2.id().toRefName()),
        problem("Object missing: patch set 2: " + rev2, FIXED, "Deleted patch set"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId().get()).isEqualTo(1);
    assertThat(psUtil.get(notes, ps1.id())).isNotNull();
    assertThat(psUtil.get(notes, ps2.id())).isNull();
  }

  @Test
  public void patchSetMultipleObjectsMissingWithDeletingPatchSets() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(notes);

    String rev2 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps2 = insertMissingPatchSet(notes, rev2);

    notes = incrementPatchSet(reload(notes));
    PatchSet ps3 = psUtil.current(notes);

    String rev4 = "c0ffeeeec0ffeeeec0ffeeeec0ffeeeec0ffeeee";
    PatchSet ps4 = insertMissingPatchSet(notes, rev4);
    notes = reload(notes);

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        notes,
        fix,
        problem("Ref missing: " + ps2.id().toRefName()),
        problem("Object missing: patch set 2: " + rev2, FIXED, "Deleted patch set"),
        problem("Ref missing: " + ps4.id().toRefName()),
        problem("Object missing: patch set 4: " + rev4, FIXED, "Deleted patch set"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId().get()).isEqualTo(3);
    assertThat(psUtil.get(notes, ps1.id())).isNotNull();
    assertThat(psUtil.get(notes, ps2.id())).isNull();
    assertThat(psUtil.get(notes, ps3.id())).isNotNull();
    assertThat(psUtil.get(notes, ps4.id())).isNull();
  }

  @Test
  public void onlyPatchSetObjectMissingWithFix() throws Exception {
    Change c = TestChanges.newChange(project, admin.id(), sequences.nextChangeId());

    PatchSet.Id psId = c.currentPatchSetId();
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps = newPatchSet(psId, rev, adminId);

    addNoteDbCommit(
        c.getId(),
        "Create change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: "
            + c.getDest().branch()
            + "\n"
            + "Change-id: "
            + c.getKey().get()
            + "\n"
            + "Subject: Bogus subject\n"
            + "Commit: "
            + rev
            + "\n"
            + "Groups: "
            + rev
            + "\n");
    ChangeNotes notes = changeNotesFactory.create(c.getProject(), c.getId());

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        notes,
        fix,
        problem("Ref missing: " + ps.id().toRefName()),
        problem(
            "Object missing: patch set 1: " + rev,
            FIX_FAILED,
            "Cannot delete patch set; no patch sets would remain"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId().get()).isEqualTo(1);
    assertThat(psUtil.current(notes)).isNotNull();
  }

  @Test
  public void duplicatePatchSetRevisions() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(notes);

    notes = incrementPatchSet(notes, serverSideTestRepo.getRevWalk().parseCommit(ps1.commitId()));

    assertProblems(
        notes,
        null,
        problem("Multiple patch sets pointing to " + ps1.commitId().name() + ": [1, 2]"));
  }

  @Test
  public void missingDestRef() throws Exception {
    ChangeNotes notes = insertChange();

    String ref = "refs/heads/master";
    // Detach head so we're allowed to delete ref.
    serverSideTestRepo.reset(serverSideTestRepo.getRepository().exactRef(ref).getObjectId());
    RefUpdate ru = serverSideTestRepo.getRepository().updateRef(ref);
    ru.setForceUpdate(true);
    testRefAction(() -> assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED));

    assertProblems(notes, null, problem("Destination ref not found (may be new branch): " + ref));
  }

  @Test
  public void mergedChangeIsNotMerged() throws Exception {
    ChangeNotes notes = insertChange();
    try (RefUpdateContext ctx = openTestRefUpdateContext()) {
      try (BatchUpdate bu = newUpdate(adminId)) {
        bu.addOp(
            notes.getChangeId(),
            new BatchUpdateOp() {
              @Override
              public boolean updateChange(ChangeContext ctx) {
                ctx.getChange().setStatus(Change.Status.MERGED);
                ctx.getUpdate(ctx.getChange().currentPatchSetId())
                    .fixStatusToMerged(new SubmissionId(ctx.getChange()));
                return true;
              }
            });
        bu.execute();
      }
    }
    notes = reload(notes);

    ObjectId tip = getDestRef(notes);
    assertProblems(
        notes,
        null,
        problem(
            "Patch set 1 ("
                + psUtil.current(notes).commitId().name()
                + ") is not merged into destination ref"
                + " refs/heads/master ("
                + tip.name()
                + "), but change status is MERGED"));
  }

  @Test
  public void newChangeIsMerged() throws Exception {
    ChangeNotes notes = insertChange();
    ObjectId commitId = psUtil.current(notes).commitId();
    serverSideTestRepo
        .branch(notes.getChange().getDest().branch())
        .update(serverSideTestRepo.getRevWalk().parseCommit(commitId));

    assertProblems(
        notes,
        null,
        problem(
            "Patch set 1 ("
                + commitId.name()
                + ") is merged into destination ref"
                + " refs/heads/master ("
                + commitId.name()
                + "), but change status is NEW"));
  }

  @Test
  public void newChangeIsMergedWithFix() throws Exception {
    ChangeNotes notes = insertChange();
    ObjectId commitId = psUtil.current(notes).commitId();
    serverSideTestRepo
        .branch(notes.getChange().getDest().branch())
        .update(serverSideTestRepo.getRevWalk().parseCommit(commitId));

    assertProblems(
        notes,
        new FixInput(),
        problem(
            "Patch set 1 ("
                + commitId.name()
                + ") is merged into destination ref"
                + " refs/heads/master ("
                + commitId.name()
                + "), but change status is NEW",
            FIXED,
            "Marked change as merged"));

    notes = reload(notes);
    assertThat(notes.getChange().isMerged()).isTrue();
    assertNoProblems(notes, null);
  }

  @Test
  public void extensionApiReturnsUpdatedValueAfterFix() throws Exception {
    ChangeNotes notes = insertChange();
    ObjectId commitId = psUtil.current(notes).commitId();
    serverSideTestRepo
        .branch(notes.getChange().getDest().branch())
        .update(serverSideTestRepo.getRevWalk().parseCommit(commitId));

    ChangeInfo info = gApi.changes().id(notes.getChangeId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    info = gApi.changes().id(notes.getChangeId().get()).check(new FixInput());
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void expectedMergedCommitIsLatestPatchSet() throws Exception {
    ChangeNotes notes = insertChange();
    ObjectId commitId = psUtil.current(notes).commitId();
    serverSideTestRepo
        .branch(notes.getChange().getDest().branch())
        .update(serverSideTestRepo.getRevWalk().parseCommit(commitId));

    FixInput fix = new FixInput();
    fix.expectMergedAs = commitId.name();
    assertProblems(
        notes,
        fix,
        problem(
            "Patch set 1 ("
                + commitId.name()
                + ") is merged into destination ref"
                + " refs/heads/master ("
                + commitId.name()
                + "), but change status is NEW",
            FIXED,
            "Marked change as merged"));

    notes = reload(notes);
    assertThat(notes.getChange().isMerged()).isTrue();
    assertNoProblems(notes, null);
  }

  @Test
  public void expectedMergedCommitNotMergedIntoDestination() throws Exception {
    ChangeNotes notes = insertChange();
    RevCommit commit =
        serverSideTestRepo.getRevWalk().parseCommit(psUtil.current(notes).commitId());
    serverSideTestRepo.branch(notes.getChange().getDest().branch()).update(commit);

    FixInput fix = new FixInput();
    RevCommit other = serverSideTestRepo.commit().message(commit.getFullMessage()).create();
    fix.expectMergedAs = other.name();
    assertProblems(
        notes,
        fix,
        problem(
            "Expected merged commit "
                + other.name()
                + " is not merged into destination ref refs/heads/master"
                + " ("
                + commit.name()
                + ")"));
  }

  @Test
  public void createNewPatchSetForExpectedMergeCommitWithNoChangeId() throws Exception {
    ChangeNotes notes = insertChange();
    String dest = notes.getChange().getDest().branch();
    RevCommit commit =
        serverSideTestRepo.getRevWalk().parseCommit(psUtil.current(notes).commitId());

    RevCommit mergedAs =
        serverSideTestRepo
            .commit()
            .parent(commit.getParent(0))
            .message(commit.getShortMessage())
            .create();
    serverSideTestRepo.getRevWalk().parseBody(mergedAs);
    assertThat(mergedAs.getFooterLines(FooterConstants.CHANGE_ID)).isEmpty();
    serverSideTestRepo.update(dest, mergedAs);

    assertNoProblems(notes, null);

    FixInput fix = new FixInput();
    fix.expectMergedAs = mergedAs.name();
    assertProblems(
        notes,
        fix,
        problem(
            "No patch set found for merged commit " + mergedAs.name(),
            FIXED,
            "Marked change as merged"),
        problem(
            "Expected merged commit " + mergedAs.name() + " has no associated patch set",
            FIXED,
            "Inserted as patch set 2"));

    notes = reload(notes);
    PatchSet.Id psId2 = PatchSet.id(notes.getChangeId(), 2);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(psUtil.get(notes, psId2).commitId()).isEqualTo(mergedAs);

    assertNoProblems(notes, null);
  }

  @Test
  public void createNewPatchSetForExpectedMergeCommitWithChangeId() throws Exception {
    ChangeNotes notes = insertChange();
    String dest = notes.getChange().getDest().branch();
    RevCommit commit =
        serverSideTestRepo.getRevWalk().parseCommit(psUtil.current(notes).commitId());

    RevCommit mergedAs =
        serverSideTestRepo
            .commit()
            .parent(commit.getParent(0))
            .message(
                commit.getShortMessage()
                    + "\n"
                    + "\n"
                    + "Change-Id: "
                    + notes.getChange().getKey().get()
                    + "\n")
            .create();
    serverSideTestRepo.getRevWalk().parseBody(mergedAs);
    assertThat(mergedAs.getFooterLines(FooterConstants.CHANGE_ID))
        .containsExactly(notes.getChange().getKey().get());
    serverSideTestRepo.update(dest, mergedAs);

    assertNoProblems(notes, null);

    FixInput fix = new FixInput();
    fix.expectMergedAs = mergedAs.name();
    assertProblems(
        notes,
        fix,
        problem(
            "No patch set found for merged commit " + mergedAs.name(),
            FIXED,
            "Marked change as merged"),
        problem(
            "Expected merged commit " + mergedAs.name() + " has no associated patch set",
            FIXED,
            "Inserted as patch set 2"));

    notes = reload(notes);
    PatchSet.Id psId2 = PatchSet.id(notes.getChangeId(), 2);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(psUtil.get(notes, psId2).commitId()).isEqualTo(mergedAs);

    assertNoProblems(notes, null);
  }

  @Test
  public void expectedMergedCommitIsOldPatchSetOfSameChange() throws Exception {
    ChangeNotes notes = insertChange();
    ObjectId commitId1 = psUtil.current(notes).commitId();
    notes = incrementPatchSet(notes);
    PatchSet ps2 = psUtil.current(notes);
    serverSideTestRepo
        .branch(notes.getChange().getDest().branch())
        .update(serverSideTestRepo.getRevWalk().parseCommit(commitId1));

    FixInput fix = new FixInput();
    fix.expectMergedAs = commitId1.name();
    assertProblems(
        notes,
        fix,
        problem(
            "No patch set found for merged commit " + commitId1.name(),
            FIXED,
            "Marked change as merged"),
        problem(
            "Expected merge commit "
                + commitId1.name()
                + " corresponds to patch set 1,"
                + " not the current patch set 2",
            FIXED,
            "Deleted patch set"),
        problem(
            "Expected merge commit "
                + commitId1.name()
                + " corresponds to patch set 1,"
                + " not the current patch set 2",
            FIXED,
            "Inserted as patch set 3"));

    notes = reload(notes);
    PatchSet.Id psId3 = PatchSet.id(notes.getChangeId(), 3);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId3);
    assertThat(notes.getChange().isMerged()).isTrue();
    assertThat(psUtil.byChangeAsMap(notes).keySet()).containsExactly(ps2.id(), psId3);
    assertThat(psUtil.get(notes, psId3).commitId()).isEqualTo(commitId1);
  }

  @Test
  public void expectedMergedCommitIsDanglingPatchSetOlderThanCurrent() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(notes);

    // Create dangling ref so next ID in the database becomes 3.
    PatchSet.Id psId2 = PatchSet.id(notes.getChangeId(), 2);
    RevCommit commit2 = patchSetCommit(psId2);
    serverSideTestRepo.branch(psId2.toRefName()).update(commit2);

    notes = incrementPatchSet(notes);
    PatchSet ps3 = psUtil.current(notes);
    assertThat(ps3.id().get()).isEqualTo(3);

    serverSideTestRepo.branch(notes.getChange().getDest().branch()).update(commit2);

    FixInput fix = new FixInput();
    fix.expectMergedAs = commit2.name();
    assertProblems(
        notes,
        fix,
        problem(
            "No patch set found for merged commit " + commit2.name(),
            FIXED,
            "Marked change as merged"),
        problem(
            "Expected merge commit "
                + commit2.name()
                + " corresponds to patch set 2,"
                + " not the current patch set 3",
            FIXED,
            "Deleted patch set"),
        problem(
            "Expected merge commit "
                + commit2.name()
                + " corresponds to patch set 2,"
                + " not the current patch set 3",
            FIXED,
            "Inserted as patch set 4"));

    notes = reload(notes);
    PatchSet.Id psId4 = PatchSet.id(notes.getChangeId(), 4);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId4);
    assertThat(notes.getChange().isMerged()).isTrue();
    assertThat(psUtil.byChangeAsMap(notes).keySet()).containsExactly(ps1.id(), ps3.id(), psId4);
    assertThat(psUtil.get(notes, psId4).commitId()).isEqualTo(commit2);
  }

  @Test
  public void expectedMergedCommitIsDanglingPatchSetNewerThanCurrent() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(notes);

    // Create dangling ref with no patch set.
    PatchSet.Id psId2 = PatchSet.id(notes.getChangeId(), 2);
    RevCommit commit2 = patchSetCommit(psId2);
    serverSideTestRepo.branch(psId2.toRefName()).update(commit2);

    serverSideTestRepo.branch(notes.getChange().getDest().branch()).update(commit2);

    FixInput fix = new FixInput();
    fix.expectMergedAs = commit2.name();
    assertProblems(
        notes,
        fix,
        problem(
            "No patch set found for merged commit " + commit2.name(),
            FIXED,
            "Marked change as merged"),
        problem(
            "Expected merge commit "
                + commit2.name()
                + " corresponds to patch set 2,"
                + " not the current patch set 1",
            FIXED,
            "Inserted as patch set 2"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(notes.getChange().isMerged()).isTrue();
    assertThat(psUtil.byChangeAsMap(notes).keySet()).containsExactly(ps1.id(), psId2);
    assertThat(psUtil.get(notes, psId2).commitId()).isEqualTo(commit2);
  }

  @Test
  public void expectedMergedCommitWithMismatchedChangeId() throws Exception {
    ChangeNotes notes = insertChange();
    String dest = notes.getChange().getDest().branch();
    RevCommit parent = serverSideTestRepo.branch(dest).commit().message("parent").create();
    RevCommit commit =
        serverSideTestRepo.getRevWalk().parseCommit(psUtil.current(notes).commitId());
    serverSideTestRepo.branch(dest).update(commit);

    String badId = "I0000000000000000000000000000000000000000";
    RevCommit mergedAs =
        serverSideTestRepo
            .commit()
            .parent(parent)
            .message(commit.getShortMessage() + "\n\nChange-Id: " + badId + "\n")
            .create();
    serverSideTestRepo.getRevWalk().parseBody(mergedAs);
    assertThat(mergedAs.getFooterLines(FooterConstants.CHANGE_ID)).containsExactly(badId);
    serverSideTestRepo.update(dest, mergedAs);

    assertNoProblems(notes, null);

    FixInput fix = new FixInput();
    fix.expectMergedAs = mergedAs.name();
    assertProblems(
        notes,
        fix,
        problem(
            "Expected merged commit "
                + mergedAs.name()
                + " has Change-Id: "
                + badId
                + ", but expected "
                + notes.getChange().getKey().get()));
  }

  @Test
  public void expectedMergedCommitMatchesMultiplePatchSets() throws Exception {
    ChangeNotes notes1 = insertChange();
    PatchSet.Id psId1 = psUtil.current(notes1).id();
    String dest = notes1.getChange().getDest().branch();
    RevCommit commit =
        serverSideTestRepo.getRevWalk().parseCommit(psUtil.current(notes1).commitId());
    serverSideTestRepo.branch(dest).update(commit);

    ChangeNotes notes2 = insertChange();
    notes2 = incrementPatchSet(notes2, commit);
    PatchSet.Id psId2 = psUtil.current(notes2).id();

    ChangeNotes notes3 = insertChange();
    notes3 = incrementPatchSet(notes3, commit);
    PatchSet.Id psId3 = psUtil.current(notes3).id();

    FixInput fix = new FixInput();
    fix.expectMergedAs = commit.name();
    assertProblems(
        notes1,
        fix,
        problem(
            "Multiple patch sets for expected merged commit "
                + commit.name()
                + ": ["
                + psId1
                + ", "
                + psId2
                + ", "
                + psId3
                + "]"));
  }

  private BatchUpdate newUpdate(Account.Id owner) {
    return batchUpdateFactory.create(project, userFactory.create(owner), TimeUtil.now());
  }

  private ChangeNotes insertChange() throws Exception {
    return insertChange(admin);
  }

  private ChangeNotes insertChange(TestAccount owner) throws Exception {
    return insertChange(owner, "refs/heads/master");
  }

  private ChangeNotes insertChange(TestAccount owner, String dest) throws Exception {
    Change.Id id = Change.id(sequences.nextChangeId());
    return testRefAction(
        () -> {
          ChangeInserter ins;
          try (BatchUpdate bu = newUpdate(owner.id())) {
            RevCommit commit = patchSetCommit(PatchSet.id(id, 1));
            bu.setNotify(NotifyResolver.Result.none());
            ins =
                changeInserterFactory
                    .create(id, commit, dest)
                    .disableValidation()
                    .setFireRevisionCreated(false)
                    .setSendMail(false);
            bu.insertChange(ins).execute();
          }
          return changeNotesFactory.create(project, ins.getChange().getId());
        });
  }

  private PatchSet.Id nextPatchSetId(ChangeNotes notes) throws Exception {
    return ChangeUtil.nextPatchSetId(
        serverSideTestRepo.getRepository(), notes.getChange().currentPatchSetId());
  }

  private ChangeNotes incrementPatchSet(ChangeNotes notes) throws Exception {
    return incrementPatchSet(notes, patchSetCommit(nextPatchSetId(notes)));
  }

  private ChangeNotes incrementPatchSet(ChangeNotes notes, RevCommit commit) throws Exception {
    return testRefAction(
        () -> {
          PatchSetInserter ins;
          try (BatchUpdate bu = newUpdate(notes.getChange().getOwner())) {
            bu.setNotify(NotifyResolver.Result.none());
            ins =
                patchSetInserterFactory
                    .create(notes, nextPatchSetId(notes), commit)
                    .disableValidation()
                    .setFireRevisionCreated(false);
            bu.addOp(notes.getChangeId(), ins).execute();
          }
          return reload(notes);
        });
  }

  private ChangeNotes reload(ChangeNotes notes) throws Exception {
    return changeNotesFactory.create(notes.getChange().getProject(), notes.getChangeId());
  }

  private RevCommit patchSetCommit(PatchSet.Id psId) throws Exception {
    RevCommit c = serverSideTestRepo.commit().parent(tip).message("Change " + psId).create();
    return serverSideTestRepo.parseBody(c);
  }

  private PatchSet insertMissingPatchSet(ChangeNotes notes, String rev) throws Exception {
    // Don't use BatchUpdate since we're manually updating the meta ref rather
    // than using ChangeUpdate.
    String subject = "Subject for missing commit";
    Change c = new Change(notes.getChange());
    PatchSet.Id psId = nextPatchSetId(notes);
    c.setCurrentPatchSet(psId, subject, c.getOriginalSubject());
    PatchSet ps = newPatchSet(psId, rev, adminId);

    addNoteDbCommit(
        c.getId(),
        "Update patch set "
            + psId.get()
            + "\n"
            + "\n"
            + "Patch-set: "
            + psId.get()
            + "\n"
            + "Commit: "
            + rev
            + "\n"
            + "Subject: "
            + subject
            + "\n");
    return ps;
  }

  private void deleteRef(String refName) throws Exception {
    RefUpdate ru = serverSideTestRepo.getRepository().updateRef(refName, true);
    ru.setForceUpdate(true);
    testRefAction(() -> assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED));
  }

  private void addNoteDbCommit(Change.Id id, String commitMessage) throws Exception {
    PersonIdent committer = serverIdent.get();
    PersonIdent author =
        noteUtil.newAccountIdIdent(
            getAccount(admin.id()).id(), committer.getWhenAsInstant(), committer);
    serverSideTestRepo
        .branch(RefNames.changeMetaRef(id))
        .commit()
        .author(author)
        .committer(committer)
        .message(commitMessage)
        .create();
  }

  private ObjectId getDestRef(ChangeNotes notes) throws Exception {
    return serverSideTestRepo
        .getRepository()
        .exactRef(notes.getChange().getDest().branch())
        .getObjectId();
  }

  private ChangeNotes mergeChange(ChangeNotes notes) throws Exception {
    return testRefAction(
        () -> {
          ObjectId oldId = getDestRef(notes);
          ObjectId newId = psUtil.current(notes).commitId();
          String dest = notes.getChange().getDest().branch();

          try (BatchUpdate bu = newUpdate(adminId)) {
            bu.addOp(
                notes.getChangeId(),
                new BatchUpdateOp() {
                  @Override
                  public void updateRepo(RepoContext ctx) throws IOException {
                    ctx.addRefUpdate(oldId, newId, dest);
                  }

                  @Override
                  public boolean updateChange(ChangeContext ctx) {
                    ctx.getChange().setStatus(Change.Status.MERGED);
                    ctx.getUpdate(ctx.getChange().currentPatchSetId())
                        .fixStatusToMerged(new SubmissionId(ctx.getChange()));
                    return true;
                  }
                });
            bu.execute();
          }
          return reload(notes);
        });
  }

  private static ProblemInfo problem(String message) {
    ProblemInfo p = new ProblemInfo();
    p.message = message;
    return p;
  }

  private static ProblemInfo problem(String message, ProblemInfo.Status status, String outcome) {
    ProblemInfo p = problem(message);
    p.status = requireNonNull(status);
    p.outcome = requireNonNull(outcome);
    return p;
  }

  private void assertProblems(
      ChangeNotes notes, @Nullable FixInput fix, ProblemInfo first, ProblemInfo... rest)
      throws Exception {
    List<ProblemInfo> expected = new ArrayList<>(1 + rest.length);
    expected.add(first);
    expected.addAll(Arrays.asList(rest));
    assertThat(checker.check(notes, fix).problems()).containsExactlyElementsIn(expected).inOrder();
  }

  private void assertNoProblems(ChangeNotes notes, @Nullable FixInput fix) throws Exception {
    assertThat(checker.check(notes, fix).problems()).isEmpty();
  }

  private void deleteUserBranch(Account.Id accountId) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String refName = RefNames.refsUsers(accountId);
      Ref ref = repo.exactRef(refName);
      if (ref == null) {
        return;
      }

      RefUpdate ru = repo.updateRef(refName);
      ru.setExpectedOldObjectId(ref.getObjectId());
      ru.setNewObjectId(ObjectId.zeroId());
      ru.setForceUpdate(true);
      Result result = testRefAction(() -> ru.delete());
      if (result != Result.FORCED) {
        throw new IOException(String.format("Failed to delete ref %s: %s", refName, result.name()));
      }
    }
  }
}
