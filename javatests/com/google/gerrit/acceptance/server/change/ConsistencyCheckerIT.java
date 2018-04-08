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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.extensions.common.ProblemInfo.Status.FIXED;
import static com.google.gerrit.extensions.common.ProblemInfo.Status.FIX_FAILED;
import static com.google.gerrit.testing.TestChanges.newPatchSet;
import static java.util.Collections.singleton;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.config.AnonymousCowardName;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ProblemInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ConsistencyChecker;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gerrit.testing.TestChanges;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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

  @Inject @AnonymousCowardName private String anonymousCowardName;

  @Inject private Sequences sequences;

  private RevCommit tip;
  private Account.Id adminId;
  private ConsistencyChecker checker;
  private TestRepository<InMemoryRepository> serverSideTestRepo;

  private void assumeNoteDbDisabled() {
    assume().that(notesMigration.readChanges()).isFalse();
    assume().that(NoteDbMode.get()).isNotEqualTo(NoteDbMode.CHECK);
  }

  @Before
  public void setUp() throws Exception {
    serverSideTestRepo =
        new TestRepository<>((InMemoryRepository) repoManager.openRepository(project));
    tip =
        serverSideTestRepo
            .getRevWalk()
            .parseCommit(serverSideTestRepo.getRepository().exactRef("HEAD").getObjectId());
    adminId = admin.getId();
    checker = checkerProvider.get();
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
    deleteUserBranch(owner.getId());

    assertProblems(notes, null, problem("Missing change owner: " + owner.getId()));
  }

  @Test
  public void missingRepo() throws Exception {
    // NoteDb can't have a change without a repo.
    assumeNoteDbDisabled();

    ChangeNotes notes = insertChange();
    Project.NameKey name = notes.getProjectName();
    ((InMemoryRepositoryManager) repoManager).deleteRepository(name);
    assertThat(checker.check(notes, null).problems())
        .containsExactly(problem("Destination repository not found: " + name));
  }

  @Test
  public void invalidRevision() throws Exception {
    // NoteDb always parses the revision when inserting a patch set, so we can't
    // create an invalid patch set.
    assumeNoteDbDisabled();

    ChangeNotes notes = insertChange();
    PatchSet ps =
        newPatchSet(
            notes.getChange().currentPatchSetId(),
            "fooooooooooooooooooooooooooooooooooooooo",
            adminId);
    db.patchSets().update(singleton(ps));

    assertProblems(
        notes,
        null,
        problem("Invalid revision on patch set 1: fooooooooooooooooooooooooooooooooooooooo"));
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
        problem("Ref missing: " + ps.getId().toRefName()),
        problem("Object missing: patch set 2: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
  }

  @Test
  public void patchSetObjectAndRefMissingWithFix() throws Exception {
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    ChangeNotes notes = insertChange();
    PatchSet ps = insertMissingPatchSet(notes, rev);
    notes = reload(notes);

    String refName = ps.getId().toRefName();
    assertProblems(
        notes,
        new FixInput(),
        problem("Ref missing: " + refName),
        problem("Object missing: patch set 2: " + rev));
  }

  @Test
  public void patchSetRefMissing() throws Exception {
    ChangeNotes notes = insertChange();
    serverSideTestRepo.update(
        "refs/other/foo", ObjectId.fromString(psUtil.current(db, notes).getRevision().get()));
    String refName = notes.getChange().currentPatchSetId().toRefName();
    deleteRef(refName);

    assertProblems(notes, null, problem("Ref missing: " + refName));
  }

  @Test
  public void patchSetRefMissingWithFix() throws Exception {
    ChangeNotes notes = insertChange();
    String rev = psUtil.current(db, notes).getRevision().get();
    serverSideTestRepo.update("refs/other/foo", ObjectId.fromString(rev));
    String refName = notes.getChange().currentPatchSetId().toRefName();
    deleteRef(refName);

    assertProblems(
        notes, new FixInput(), problem("Ref missing: " + refName, FIXED, "Repaired patch set ref"));
    assertThat(serverSideTestRepo.getRepository().exactRef(refName).getObjectId().name())
        .isEqualTo(rev);
  }

  @Test
  public void patchSetObjectAndRefMissingWithDeletingPatchSet() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(db, notes);

    String rev2 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps2 = insertMissingPatchSet(notes, rev2);
    notes = reload(notes);

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        notes,
        fix,
        problem("Ref missing: " + ps2.getId().toRefName()),
        problem("Object missing: patch set 2: " + rev2, FIXED, "Deleted patch set"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId().get()).isEqualTo(1);
    assertThat(psUtil.get(db, notes, ps1.getId())).isNotNull();
    assertThat(psUtil.get(db, notes, ps2.getId())).isNull();
  }

  @Test
  public void patchSetMultipleObjectsMissingWithDeletingPatchSets() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(db, notes);

    String rev2 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps2 = insertMissingPatchSet(notes, rev2);

    notes = incrementPatchSet(reload(notes));
    PatchSet ps3 = psUtil.current(db, notes);

    String rev4 = "c0ffeeeec0ffeeeec0ffeeeec0ffeeeec0ffeeee";
    PatchSet ps4 = insertMissingPatchSet(notes, rev4);
    notes = reload(notes);

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        notes,
        fix,
        problem("Ref missing: " + ps2.getId().toRefName()),
        problem("Object missing: patch set 2: " + rev2, FIXED, "Deleted patch set"),
        problem("Ref missing: " + ps4.getId().toRefName()),
        problem("Object missing: patch set 4: " + rev4, FIXED, "Deleted patch set"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId().get()).isEqualTo(3);
    assertThat(psUtil.get(db, notes, ps1.getId())).isNotNull();
    assertThat(psUtil.get(db, notes, ps2.getId())).isNull();
    assertThat(psUtil.get(db, notes, ps3.getId())).isNotNull();
    assertThat(psUtil.get(db, notes, ps4.getId())).isNull();
  }

  @Test
  public void onlyPatchSetObjectMissingWithFix() throws Exception {
    Change c = TestChanges.newChange(project, admin.getId(), sequences.nextChangeId());

    // Set review started, mimicking Schema_153, so tests pass with NoteDbMode.CHECK.
    c.setReviewStarted(true);

    PatchSet.Id psId = c.currentPatchSetId();
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps = newPatchSet(psId, rev, adminId);

    if (notesMigration.changePrimaryStorage() == PrimaryStorage.REVIEW_DB) {
      db.changes().insert(singleton(c));
      db.patchSets().insert(singleton(ps));
    }
    addNoteDbCommit(
        c.getId(),
        "Create change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: "
            + c.getDest().get()
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
    indexer.index(db, c.getProject(), c.getId());
    ChangeNotes notes = changeNotesFactory.create(db, c.getProject(), c.getId());

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        notes,
        fix,
        problem("Ref missing: " + ps.getId().toRefName()),
        problem(
            "Object missing: patch set 1: " + rev,
            FIX_FAILED,
            "Cannot delete patch set; no patch sets would remain"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId().get()).isEqualTo(1);
    assertThat(psUtil.current(db, notes)).isNotNull();
  }

  @Test
  public void currentPatchSetMissing() throws Exception {
    // NoteDb can't create a change without a patch set.
    assumeNoteDbDisabled();

    ChangeNotes notes = insertChange();
    db.patchSets().deleteKeys(singleton(notes.getChange().currentPatchSetId()));
    assertProblems(notes, null, problem("Current patch set 1 not found"));
  }

  @Test
  public void duplicatePatchSetRevisions() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(db, notes);
    String rev = ps1.getRevision().get();

    notes =
        incrementPatchSet(
            notes, serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    assertProblems(notes, null, problem("Multiple patch sets pointing to " + rev + ": [1, 2]"));
  }

  @Test
  public void missingDestRef() throws Exception {
    ChangeNotes notes = insertChange();

    String ref = "refs/heads/master";
    // Detach head so we're allowed to delete ref.
    serverSideTestRepo.reset(serverSideTestRepo.getRepository().exactRef(ref).getObjectId());
    RefUpdate ru = serverSideTestRepo.getRepository().updateRef(ref);
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);

    assertProblems(notes, null, problem("Destination ref not found (may be new branch): " + ref));
  }

  @Test
  public void mergedChangeIsNotMerged() throws Exception {
    ChangeNotes notes = insertChange();

    try (BatchUpdate bu = newUpdate(adminId)) {
      bu.addOp(
          notes.getChangeId(),
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws OrmException {
              ctx.getChange().setStatus(Change.Status.MERGED);
              ctx.getUpdate(ctx.getChange().currentPatchSetId()).fixStatus(Change.Status.MERGED);
              return true;
            }
          });
      bu.execute();
    }
    notes = reload(notes);

    String rev = psUtil.current(db, notes).getRevision().get();
    ObjectId tip = getDestRef(notes);
    assertProblems(
        notes,
        null,
        problem(
            "Patch set 1 ("
                + rev
                + ") is not merged into destination ref"
                + " refs/heads/master ("
                + tip.name()
                + "), but change status is MERGED"));
  }

  @Test
  public void newChangeIsMerged() throws Exception {
    ChangeNotes notes = insertChange();
    String rev = psUtil.current(db, notes).getRevision().get();
    serverSideTestRepo
        .branch(notes.getChange().getDest().get())
        .update(serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    assertProblems(
        notes,
        null,
        problem(
            "Patch set 1 ("
                + rev
                + ") is merged into destination ref"
                + " refs/heads/master ("
                + rev
                + "), but change status is NEW"));
  }

  @Test
  public void newChangeIsMergedWithFix() throws Exception {
    ChangeNotes notes = insertChange();
    String rev = psUtil.current(db, notes).getRevision().get();
    serverSideTestRepo
        .branch(notes.getChange().getDest().get())
        .update(serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    assertProblems(
        notes,
        new FixInput(),
        problem(
            "Patch set 1 ("
                + rev
                + ") is merged into destination ref"
                + " refs/heads/master ("
                + rev
                + "), but change status is NEW",
            FIXED,
            "Marked change as merged"));

    notes = reload(notes);
    assertThat(notes.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertNoProblems(notes, null);
  }

  @Test
  public void extensionApiReturnsUpdatedValueAfterFix() throws Exception {
    ChangeNotes notes = insertChange();
    String rev = psUtil.current(db, notes).getRevision().get();
    serverSideTestRepo
        .branch(notes.getChange().getDest().get())
        .update(serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    ChangeInfo info = gApi.changes().id(notes.getChangeId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    info = gApi.changes().id(notes.getChangeId().get()).check(new FixInput());
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void expectedMergedCommitIsLatestPatchSet() throws Exception {
    ChangeNotes notes = insertChange();
    String rev = psUtil.current(db, notes).getRevision().get();
    serverSideTestRepo
        .branch(notes.getChange().getDest().get())
        .update(serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev;
    assertProblems(
        notes,
        fix,
        problem(
            "Patch set 1 ("
                + rev
                + ") is merged into destination ref"
                + " refs/heads/master ("
                + rev
                + "), but change status is NEW",
            FIXED,
            "Marked change as merged"));

    notes = reload(notes);
    assertThat(notes.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertNoProblems(notes, null);
  }

  @Test
  public void expectedMergedCommitNotMergedIntoDestination() throws Exception {
    ChangeNotes notes = insertChange();
    String rev = psUtil.current(db, notes).getRevision().get();
    RevCommit commit = serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));
    serverSideTestRepo.branch(notes.getChange().getDest().get()).update(commit);

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
    String dest = notes.getChange().getDest().get();
    String rev = psUtil.current(db, notes).getRevision().get();
    RevCommit commit = serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));

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
    PatchSet.Id psId2 = new PatchSet.Id(notes.getChangeId(), 2);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(psUtil.get(db, notes, psId2).getRevision().get()).isEqualTo(mergedAs.name());

    assertNoProblems(notes, null);
  }

  @Test
  public void createNewPatchSetForExpectedMergeCommitWithChangeId() throws Exception {
    ChangeNotes notes = insertChange();
    String dest = notes.getChange().getDest().get();
    String rev = psUtil.current(db, notes).getRevision().get();
    RevCommit commit = serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));

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
    PatchSet.Id psId2 = new PatchSet.Id(notes.getChangeId(), 2);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(psUtil.get(db, notes, psId2).getRevision().get()).isEqualTo(mergedAs.name());

    assertNoProblems(notes, null);
  }

  @Test
  public void expectedMergedCommitIsOldPatchSetOfSameChange() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(db, notes);
    String rev1 = ps1.getRevision().get();
    notes = incrementPatchSet(notes);
    PatchSet ps2 = psUtil.current(db, notes);
    serverSideTestRepo
        .branch(notes.getChange().getDest().get())
        .update(serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev1)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev1;
    assertProblems(
        notes,
        fix,
        problem("No patch set found for merged commit " + rev1, FIXED, "Marked change as merged"),
        problem(
            "Expected merge commit "
                + rev1
                + " corresponds to patch set 1,"
                + " not the current patch set 2",
            FIXED,
            "Deleted patch set"),
        problem(
            "Expected merge commit "
                + rev1
                + " corresponds to patch set 1,"
                + " not the current patch set 2",
            FIXED,
            "Inserted as patch set 3"));

    notes = reload(notes);
    PatchSet.Id psId3 = new PatchSet.Id(notes.getChangeId(), 3);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId3);
    assertThat(notes.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(psUtil.byChangeAsMap(db, notes).keySet()).containsExactly(ps2.getId(), psId3);
    assertThat(psUtil.get(db, notes, psId3).getRevision().get()).isEqualTo(rev1);
  }

  @Test
  public void expectedMergedCommitIsDanglingPatchSetOlderThanCurrent() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(db, notes);

    // Create dangling ref so next ID in the database becomes 3.
    PatchSet.Id psId2 = new PatchSet.Id(notes.getChangeId(), 2);
    RevCommit commit2 = patchSetCommit(psId2);
    String rev2 = commit2.name();
    serverSideTestRepo.branch(psId2.toRefName()).update(commit2);

    notes = incrementPatchSet(notes);
    PatchSet ps3 = psUtil.current(db, notes);
    assertThat(ps3.getId().get()).isEqualTo(3);

    serverSideTestRepo
        .branch(notes.getChange().getDest().get())
        .update(serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev2)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev2;
    assertProblems(
        notes,
        fix,
        problem("No patch set found for merged commit " + rev2, FIXED, "Marked change as merged"),
        problem(
            "Expected merge commit "
                + rev2
                + " corresponds to patch set 2,"
                + " not the current patch set 3",
            FIXED,
            "Deleted patch set"),
        problem(
            "Expected merge commit "
                + rev2
                + " corresponds to patch set 2,"
                + " not the current patch set 3",
            FIXED,
            "Inserted as patch set 4"));

    notes = reload(notes);
    PatchSet.Id psId4 = new PatchSet.Id(notes.getChangeId(), 4);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId4);
    assertThat(notes.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(psUtil.byChangeAsMap(db, notes).keySet())
        .containsExactly(ps1.getId(), ps3.getId(), psId4);
    assertThat(psUtil.get(db, notes, psId4).getRevision().get()).isEqualTo(rev2);
  }

  @Test
  public void expectedMergedCommitIsDanglingPatchSetNewerThanCurrent() throws Exception {
    ChangeNotes notes = insertChange();
    PatchSet ps1 = psUtil.current(db, notes);

    // Create dangling ref with no patch set.
    PatchSet.Id psId2 = new PatchSet.Id(notes.getChangeId(), 2);
    RevCommit commit2 = patchSetCommit(psId2);
    String rev2 = commit2.name();
    serverSideTestRepo.branch(psId2.toRefName()).update(commit2);

    serverSideTestRepo
        .branch(notes.getChange().getDest().get())
        .update(serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev2)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev2;
    assertProblems(
        notes,
        fix,
        problem("No patch set found for merged commit " + rev2, FIXED, "Marked change as merged"),
        problem(
            "Expected merge commit "
                + rev2
                + " corresponds to patch set 2,"
                + " not the current patch set 1",
            FIXED,
            "Inserted as patch set 2"));

    notes = reload(notes);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(notes.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(psUtil.byChangeAsMap(db, notes).keySet()).containsExactly(ps1.getId(), psId2);
    assertThat(psUtil.get(db, notes, psId2).getRevision().get()).isEqualTo(rev2);
  }

  @Test
  public void expectedMergedCommitWithMismatchedChangeId() throws Exception {
    ChangeNotes notes = insertChange();
    String dest = notes.getChange().getDest().get();
    RevCommit parent = serverSideTestRepo.branch(dest).commit().message("parent").create();
    String rev = psUtil.current(db, notes).getRevision().get();
    RevCommit commit = serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));
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
    PatchSet.Id psId1 = psUtil.current(db, notes1).getId();
    String dest = notes1.getChange().getDest().get();
    String rev = psUtil.current(db, notes1).getRevision().get();
    RevCommit commit = serverSideTestRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));
    serverSideTestRepo.branch(dest).update(commit);

    ChangeNotes notes2 = insertChange();
    notes2 = incrementPatchSet(notes2, commit);
    PatchSet.Id psId2 = psUtil.current(db, notes2).getId();

    ChangeNotes notes3 = insertChange();
    notes3 = incrementPatchSet(notes3, commit);
    PatchSet.Id psId3 = psUtil.current(db, notes3).getId();

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
    return batchUpdateFactory.create(db, project, userFactory.create(owner), TimeUtil.nowTs());
  }

  private ChangeNotes insertChange() throws Exception {
    return insertChange(admin);
  }

  private ChangeNotes insertChange(TestAccount owner) throws Exception {
    return insertChange(owner, "refs/heads/master");
  }

  private ChangeNotes insertChange(TestAccount owner, String dest) throws Exception {
    Change.Id id = new Change.Id(sequences.nextChangeId());
    ChangeInserter ins;
    try (BatchUpdate bu = newUpdate(owner.getId())) {
      RevCommit commit = patchSetCommit(new PatchSet.Id(id, 1));
      ins =
          changeInserterFactory
              .create(id, commit, dest)
              .setValidate(false)
              .setNotify(NotifyHandling.NONE)
              .setFireRevisionCreated(false)
              .setSendMail(false);
      bu.insertChange(ins).execute();
    }
    return changeNotesFactory.create(db, project, ins.getChange().getId());
  }

  private PatchSet.Id nextPatchSetId(ChangeNotes notes) throws Exception {
    return ChangeUtil.nextPatchSetId(
        serverSideTestRepo.getRepository(), notes.getChange().currentPatchSetId());
  }

  private ChangeNotes incrementPatchSet(ChangeNotes notes) throws Exception {
    return incrementPatchSet(notes, patchSetCommit(nextPatchSetId(notes)));
  }

  private ChangeNotes incrementPatchSet(ChangeNotes notes, RevCommit commit) throws Exception {
    PatchSetInserter ins;
    try (BatchUpdate bu = newUpdate(notes.getChange().getOwner())) {
      ins =
          patchSetInserterFactory
              .create(notes, nextPatchSetId(notes), commit)
              .setValidate(false)
              .setFireRevisionCreated(false)
              .setNotify(NotifyHandling.NONE);
      bu.addOp(notes.getChangeId(), ins).execute();
    }
    return reload(notes);
  }

  private ChangeNotes reload(ChangeNotes notes) throws Exception {
    return changeNotesFactory.create(db, notes.getChange().getProject(), notes.getChangeId());
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

    if (PrimaryStorage.of(c) == PrimaryStorage.REVIEW_DB) {
      db.patchSets().insert(singleton(ps));
      db.changes().update(singleton(c));
    }

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
    indexer.index(db, c.getProject(), c.getId());

    return ps;
  }

  private void deleteRef(String refName) throws Exception {
    RefUpdate ru = serverSideTestRepo.getRepository().updateRef(refName, true);
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
  }

  private void addNoteDbCommit(Change.Id id, String commitMessage) throws Exception {
    if (!notesMigration.commitChangeWrites()) {
      return;
    }
    PersonIdent committer = serverIdent.get();
    PersonIdent author =
        noteUtil.newIdent(getAccount(admin.getId()), committer.getWhen(), committer);
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
        .exactRef(notes.getChange().getDest().get())
        .getObjectId();
  }

  private ChangeNotes mergeChange(ChangeNotes notes) throws Exception {
    final ObjectId oldId = getDestRef(notes);
    final ObjectId newId = ObjectId.fromString(psUtil.current(db, notes).getRevision().get());
    final String dest = notes.getChange().getDest().get();

    try (BatchUpdate bu = newUpdate(adminId)) {
      bu.addOp(
          notes.getChangeId(),
          new BatchUpdateOp() {
            @Override
            public void updateRepo(RepoContext ctx) throws IOException {
              ctx.addRefUpdate(oldId, newId, dest);
            }

            @Override
            public boolean updateChange(ChangeContext ctx) throws OrmException {
              ctx.getChange().setStatus(Change.Status.MERGED);
              ctx.getUpdate(ctx.getChange().currentPatchSetId()).fixStatus(Change.Status.MERGED);
              return true;
            }
          });
      bu.execute();
    }
    return reload(notes);
  }

  private static ProblemInfo problem(String message) {
    ProblemInfo p = new ProblemInfo();
    p.message = message;
    return p;
  }

  private static ProblemInfo problem(String message, ProblemInfo.Status status, String outcome) {
    ProblemInfo p = problem(message);
    p.status = checkNotNull(status);
    p.outcome = checkNotNull(outcome);
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
      Result result = ru.delete();
      if (result != Result.FORCED) {
        throw new IOException(String.format("Failed to delete ref %s: %s", refName, result.name()));
      }
    }
  }
}
