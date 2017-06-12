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
import static com.google.gerrit.testutil.TestChanges.newPatchSet;
import static java.util.Collections.singleton;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
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
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gerrit.testutil.TestChanges;
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
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ConsistencyCheckerIT extends AbstractDaemonTest {
  @Inject private ChangeControl.GenericFactory changeControlFactory;

  @Inject private Provider<ConsistencyChecker> checkerProvider;

  @Inject private IdentifiedUser.GenericFactory userFactory;

  @Inject private BatchUpdate.Factory updateFactory;

  @Inject private ChangeInserter.Factory changeInserterFactory;

  @Inject private PatchSetInserter.Factory patchSetInserterFactory;

  @Inject private ChangeNoteUtil noteUtil;

  @Inject @AnonymousCowardName private String anonymousCowardName;

  @Inject private Sequences sequences;

  private RevCommit tip;
  private Account.Id adminId;
  private ConsistencyChecker checker;

  @Before
  public void setUp() throws Exception {
    // Ignore client clone of project; repurpose as server-side TestRepository.
    testRepo = new TestRepository<>((InMemoryRepository) repoManager.openRepository(project));
    tip =
        testRepo.getRevWalk().parseCommit(testRepo.getRepository().exactRef("HEAD").getObjectId());
    adminId = admin.getId();
    checker = checkerProvider.get();
  }

  @Test
  public void validNewChange() throws Exception {
    assertNoProblems(insertChange(), null);
  }

  @Test
  public void validMergedChange() throws Exception {
    ChangeControl ctl = mergeChange(incrementPatchSet(insertChange()));
    assertNoProblems(ctl, null);
  }

  @Test
  public void missingOwner() throws Exception {
    TestAccount owner = accounts.create("missing");
    ChangeControl ctl = insertChange(owner);
    db.accounts().deleteKeys(singleton(owner.getId()));

    assertProblems(ctl, null, problem("Missing change owner: " + owner.getId()));
  }

  @Test
  public void missingRepo() throws Exception {
    // NoteDb can't have a change without a repo.
    assume().that(notesMigration.enabled()).isFalse();

    ChangeControl ctl = insertChange();
    Project.NameKey name = ctl.getProject().getNameKey();
    ((InMemoryRepositoryManager) repoManager).deleteRepository(name);

    assertProblems(ctl, null, problem("Destination repository not found: " + name));
  }

  @Test
  public void invalidRevision() throws Exception {
    // NoteDb always parses the revision when inserting a patch set, so we can't
    // create an invalid patch set.
    assume().that(notesMigration.enabled()).isFalse();

    ChangeControl ctl = insertChange();
    PatchSet ps =
        newPatchSet(
            ctl.getChange().currentPatchSetId(),
            "fooooooooooooooooooooooooooooooooooooooo",
            adminId);
    db.patchSets().update(singleton(ps));

    assertProblems(
        ctl,
        null,
        problem("Invalid revision on patch set 1:" + " fooooooooooooooooooooooooooooooooooooooo"));
  }

  // No test for ref existing but object missing; InMemoryRepository won't let
  // us do such a thing.

  @Test
  public void patchSetObjectAndRefMissing() throws Exception {
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    ChangeControl ctl = insertChange();
    PatchSet ps = insertMissingPatchSet(ctl, rev);
    ctl = reload(ctl);
    assertProblems(
        ctl,
        null,
        problem("Ref missing: " + ps.getId().toRefName()),
        problem("Object missing: patch set 2:" + " deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
  }

  @Test
  public void patchSetObjectAndRefMissingWithFix() throws Exception {
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    ChangeControl ctl = insertChange();
    PatchSet ps = insertMissingPatchSet(ctl, rev);
    ctl = reload(ctl);

    String refName = ps.getId().toRefName();
    assertProblems(
        ctl,
        new FixInput(),
        problem("Ref missing: " + refName),
        problem("Object missing: patch set 2: " + rev));
  }

  @Test
  public void patchSetRefMissing() throws Exception {
    ChangeControl ctl = insertChange();
    testRepo.update(
        "refs/other/foo",
        ObjectId.fromString(psUtil.current(db, ctl.getNotes()).getRevision().get()));
    String refName = ctl.getChange().currentPatchSetId().toRefName();
    deleteRef(refName);

    assertProblems(ctl, null, problem("Ref missing: " + refName));
  }

  @Test
  public void patchSetRefMissingWithFix() throws Exception {
    ChangeControl ctl = insertChange();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    testRepo.update("refs/other/foo", ObjectId.fromString(rev));
    String refName = ctl.getChange().currentPatchSetId().toRefName();
    deleteRef(refName);

    assertProblems(
        ctl, new FixInput(), problem("Ref missing: " + refName, FIXED, "Repaired patch set ref"));
    assertThat(testRepo.getRepository().exactRef(refName).getObjectId().name()).isEqualTo(rev);
  }

  @Test
  public void patchSetObjectAndRefMissingWithDeletingPatchSet() throws Exception {
    ChangeControl ctl = insertChange();
    PatchSet ps1 = psUtil.current(db, ctl.getNotes());

    String rev2 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps2 = insertMissingPatchSet(ctl, rev2);
    ctl = reload(ctl);

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        ctl,
        fix,
        problem("Ref missing: " + ps2.getId().toRefName()),
        problem("Object missing: patch set 2: " + rev2, FIXED, "Deleted patch set"));

    ctl = reload(ctl);
    assertThat(ctl.getChange().currentPatchSetId().get()).isEqualTo(1);
    assertThat(psUtil.get(db, ctl.getNotes(), ps1.getId())).isNotNull();
    assertThat(psUtil.get(db, ctl.getNotes(), ps2.getId())).isNull();
  }

  @Test
  public void patchSetMultipleObjectsMissingWithDeletingPatchSets() throws Exception {
    ChangeControl ctl = insertChange();
    PatchSet ps1 = psUtil.current(db, ctl.getNotes());

    String rev2 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps2 = insertMissingPatchSet(ctl, rev2);

    ctl = incrementPatchSet(reload(ctl));
    PatchSet ps3 = psUtil.current(db, ctl.getNotes());

    String rev4 = "c0ffeeeec0ffeeeec0ffeeeec0ffeeeec0ffeeee";
    PatchSet ps4 = insertMissingPatchSet(ctl, rev4);
    ctl = reload(ctl);

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        ctl,
        fix,
        problem("Ref missing: " + ps2.getId().toRefName()),
        problem("Object missing: patch set 2: " + rev2, FIXED, "Deleted patch set"),
        problem("Ref missing: " + ps4.getId().toRefName()),
        problem("Object missing: patch set 4: " + rev4, FIXED, "Deleted patch set"));

    ctl = reload(ctl);
    assertThat(ctl.getChange().currentPatchSetId().get()).isEqualTo(3);
    assertThat(psUtil.get(db, ctl.getNotes(), ps1.getId())).isNotNull();
    assertThat(psUtil.get(db, ctl.getNotes(), ps2.getId())).isNull();
    assertThat(psUtil.get(db, ctl.getNotes(), ps3.getId())).isNotNull();
    assertThat(psUtil.get(db, ctl.getNotes(), ps4.getId())).isNull();
  }

  @Test
  public void onlyPatchSetObjectMissingWithFix() throws Exception {
    Change c = TestChanges.newChange(project, admin.getId(), sequences.nextChangeId());
    PatchSet.Id psId = c.currentPatchSetId();
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PatchSet ps = newPatchSet(psId, rev, adminId);

    db.changes().insert(singleton(c));
    db.patchSets().insert(singleton(ps));
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
    IdentifiedUser user = userFactory.create(admin.getId());
    ChangeControl ctl = changeControlFactory.controlFor(db, c.getProject(), c.getId(), user);

    FixInput fix = new FixInput();
    fix.deletePatchSetIfCommitMissing = true;
    assertProblems(
        ctl,
        fix,
        problem("Ref missing: " + ps.getId().toRefName()),
        problem(
            "Object missing: patch set 1: " + rev,
            FIX_FAILED,
            "Cannot delete patch set; no patch sets would remain"));

    ctl = reload(ctl);
    assertThat(ctl.getChange().currentPatchSetId().get()).isEqualTo(1);
    assertThat(psUtil.current(db, ctl.getNotes())).isNotNull();
  }

  @Test
  public void currentPatchSetMissing() throws Exception {
    // NoteDb can't create a change without a patch set.
    assume().that(notesMigration.enabled()).isFalse();

    ChangeControl ctl = insertChange();
    db.patchSets().deleteKeys(singleton(ctl.getChange().currentPatchSetId()));
    assertProblems(ctl, null, problem("Current patch set 1 not found"));
  }

  @Test
  public void duplicatePatchSetRevisions() throws Exception {
    ChangeControl ctl = insertChange();
    PatchSet ps1 = psUtil.current(db, ctl.getNotes());
    String rev = ps1.getRevision().get();

    ctl = incrementPatchSet(ctl, testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    assertProblems(ctl, null, problem("Multiple patch sets pointing to " + rev + ": [1, 2]"));
  }

  @Test
  public void missingDestRef() throws Exception {
    ChangeControl ctl = insertChange();

    String ref = "refs/heads/master";
    // Detach head so we're allowed to delete ref.
    testRepo.reset(testRepo.getRepository().exactRef(ref).getObjectId());
    RefUpdate ru = testRepo.getRepository().updateRef(ref);
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);

    assertProblems(ctl, null, problem("Destination ref not found (may be new branch): " + ref));
  }

  @Test
  public void mergedChangeIsNotMerged() throws Exception {
    ChangeControl ctl = insertChange();

    try (BatchUpdate bu = newUpdate(adminId)) {
      bu.addOp(
          ctl.getId(),
          new BatchUpdate.Op() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws OrmException {
              ctx.getChange().setStatus(Change.Status.MERGED);
              ctx.getUpdate(ctx.getChange().currentPatchSetId()).fixStatus(Change.Status.MERGED);
              return true;
            }
          });
      bu.execute();
    }
    ctl = reload(ctl);

    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    ObjectId tip = getDestRef(ctl);
    assertProblems(
        ctl,
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
    ChangeControl ctl = insertChange();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    testRepo
        .branch(ctl.getChange().getDest().get())
        .update(testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    assertProblems(
        ctl,
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
    ChangeControl ctl = insertChange();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    testRepo
        .branch(ctl.getChange().getDest().get())
        .update(testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    assertProblems(
        ctl,
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

    ctl = reload(ctl);
    assertThat(ctl.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertNoProblems(ctl, null);
  }

  @Test
  public void extensionApiReturnsUpdatedValueAfterFix() throws Exception {
    ChangeControl ctl = insertChange();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    testRepo
        .branch(ctl.getChange().getDest().get())
        .update(testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    ChangeInfo info = gApi.changes().id(ctl.getId().get()).info();
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);

    info = gApi.changes().id(ctl.getId().get()).check(new FixInput());
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void expectedMergedCommitIsLatestPatchSet() throws Exception {
    ChangeControl ctl = insertChange();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    testRepo
        .branch(ctl.getChange().getDest().get())
        .update(testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev;
    assertProblems(
        ctl,
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

    ctl = reload(ctl);
    assertThat(ctl.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertNoProblems(ctl, null);
  }

  @Test
  public void expectedMergedCommitNotMergedIntoDestination() throws Exception {
    ChangeControl ctl = insertChange();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    RevCommit commit = testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));
    testRepo.branch(ctl.getChange().getDest().get()).update(commit);

    FixInput fix = new FixInput();
    RevCommit other = testRepo.commit().message(commit.getFullMessage()).create();
    fix.expectMergedAs = other.name();
    assertProblems(
        ctl,
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
    ChangeControl ctl = insertChange();
    String dest = ctl.getChange().getDest().get();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    RevCommit commit = testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));

    RevCommit mergedAs =
        testRepo.commit().parent(commit.getParent(0)).message(commit.getShortMessage()).create();
    testRepo.getRevWalk().parseBody(mergedAs);
    assertThat(mergedAs.getFooterLines(FooterConstants.CHANGE_ID)).isEmpty();
    testRepo.update(dest, mergedAs);

    assertNoProblems(ctl, null);

    FixInput fix = new FixInput();
    fix.expectMergedAs = mergedAs.name();
    assertProblems(
        ctl,
        fix,
        problem(
            "No patch set found for merged commit " + mergedAs.name(),
            FIXED,
            "Marked change as merged"),
        problem(
            "Expected merged commit " + mergedAs.name() + " has no associated patch set",
            FIXED,
            "Inserted as patch set 2"));

    ctl = reload(ctl);
    PatchSet.Id psId2 = new PatchSet.Id(ctl.getId(), 2);
    assertThat(ctl.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(psUtil.get(db, ctl.getNotes(), psId2).getRevision().get())
        .isEqualTo(mergedAs.name());

    assertNoProblems(ctl, null);
  }

  @Test
  public void createNewPatchSetForExpectedMergeCommitWithChangeId() throws Exception {
    ChangeControl ctl = insertChange();
    String dest = ctl.getChange().getDest().get();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    RevCommit commit = testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));

    RevCommit mergedAs =
        testRepo
            .commit()
            .parent(commit.getParent(0))
            .message(
                commit.getShortMessage()
                    + "\n"
                    + "\n"
                    + "Change-Id: "
                    + ctl.getChange().getKey().get()
                    + "\n")
            .create();
    testRepo.getRevWalk().parseBody(mergedAs);
    assertThat(mergedAs.getFooterLines(FooterConstants.CHANGE_ID))
        .containsExactly(ctl.getChange().getKey().get());
    testRepo.update(dest, mergedAs);

    assertNoProblems(ctl, null);

    FixInput fix = new FixInput();
    fix.expectMergedAs = mergedAs.name();
    assertProblems(
        ctl,
        fix,
        problem(
            "No patch set found for merged commit " + mergedAs.name(),
            FIXED,
            "Marked change as merged"),
        problem(
            "Expected merged commit " + mergedAs.name() + " has no associated patch set",
            FIXED,
            "Inserted as patch set 2"));

    ctl = reload(ctl);
    PatchSet.Id psId2 = new PatchSet.Id(ctl.getId(), 2);
    assertThat(ctl.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(psUtil.get(db, ctl.getNotes(), psId2).getRevision().get())
        .isEqualTo(mergedAs.name());

    assertNoProblems(ctl, null);
  }

  @Test
  public void expectedMergedCommitIsOldPatchSetOfSameChange() throws Exception {
    ChangeControl ctl = insertChange();
    PatchSet ps1 = psUtil.current(db, ctl.getNotes());
    String rev1 = ps1.getRevision().get();
    ctl = incrementPatchSet(ctl);
    PatchSet ps2 = psUtil.current(db, ctl.getNotes());
    testRepo
        .branch(ctl.getChange().getDest().get())
        .update(testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev1)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev1;
    assertProblems(
        ctl,
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

    ctl = reload(ctl);
    PatchSet.Id psId3 = new PatchSet.Id(ctl.getId(), 3);
    assertThat(ctl.getChange().currentPatchSetId()).isEqualTo(psId3);
    assertThat(ctl.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(psUtil.byChangeAsMap(db, ctl.getNotes()).keySet())
        .containsExactly(ps2.getId(), psId3);
    assertThat(psUtil.get(db, ctl.getNotes(), psId3).getRevision().get()).isEqualTo(rev1);
  }

  @Test
  public void expectedMergedCommitIsDanglingPatchSetOlderThanCurrent() throws Exception {
    ChangeControl ctl = insertChange();
    PatchSet ps1 = psUtil.current(db, ctl.getNotes());

    // Create dangling ref so next ID in the database becomes 3.
    PatchSet.Id psId2 = new PatchSet.Id(ctl.getId(), 2);
    RevCommit commit2 = patchSetCommit(psId2);
    String rev2 = commit2.name();
    testRepo.branch(psId2.toRefName()).update(commit2);

    ctl = incrementPatchSet(ctl);
    PatchSet ps3 = psUtil.current(db, ctl.getNotes());
    assertThat(ps3.getId().get()).isEqualTo(3);

    testRepo
        .branch(ctl.getChange().getDest().get())
        .update(testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev2)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev2;
    assertProblems(
        ctl,
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

    ctl = reload(ctl);
    PatchSet.Id psId4 = new PatchSet.Id(ctl.getId(), 4);
    assertThat(ctl.getChange().currentPatchSetId()).isEqualTo(psId4);
    assertThat(ctl.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(psUtil.byChangeAsMap(db, ctl.getNotes()).keySet())
        .containsExactly(ps1.getId(), ps3.getId(), psId4);
    assertThat(psUtil.get(db, ctl.getNotes(), psId4).getRevision().get()).isEqualTo(rev2);
  }

  @Test
  public void expectedMergedCommitIsDanglingPatchSetNewerThanCurrent() throws Exception {
    ChangeControl ctl = insertChange();
    PatchSet ps1 = psUtil.current(db, ctl.getNotes());

    // Create dangling ref with no patch set.
    PatchSet.Id psId2 = new PatchSet.Id(ctl.getId(), 2);
    RevCommit commit2 = patchSetCommit(psId2);
    String rev2 = commit2.name();
    testRepo.branch(psId2.toRefName()).update(commit2);

    testRepo
        .branch(ctl.getChange().getDest().get())
        .update(testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev2)));

    FixInput fix = new FixInput();
    fix.expectMergedAs = rev2;
    assertProblems(
        ctl,
        fix,
        problem("No patch set found for merged commit " + rev2, FIXED, "Marked change as merged"),
        problem(
            "Expected merge commit "
                + rev2
                + " corresponds to patch set 2,"
                + " not the current patch set 1",
            FIXED,
            "Inserted as patch set 2"));

    ctl = reload(ctl);
    assertThat(ctl.getChange().currentPatchSetId()).isEqualTo(psId2);
    assertThat(ctl.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(psUtil.byChangeAsMap(db, ctl.getNotes()).keySet())
        .containsExactly(ps1.getId(), psId2);
    assertThat(psUtil.get(db, ctl.getNotes(), psId2).getRevision().get()).isEqualTo(rev2);
  }

  @Test
  public void expectedMergedCommitWithMismatchedChangeId() throws Exception {
    ChangeControl ctl = insertChange();
    String dest = ctl.getChange().getDest().get();
    RevCommit parent = testRepo.branch(dest).commit().message("parent").create();
    String rev = psUtil.current(db, ctl.getNotes()).getRevision().get();
    RevCommit commit = testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));
    testRepo.branch(dest).update(commit);

    String badId = "I0000000000000000000000000000000000000000";
    RevCommit mergedAs =
        testRepo
            .commit()
            .parent(parent)
            .message(commit.getShortMessage() + "\n" + "\n" + "Change-Id: " + badId + "\n")
            .create();
    testRepo.getRevWalk().parseBody(mergedAs);
    assertThat(mergedAs.getFooterLines(FooterConstants.CHANGE_ID)).containsExactly(badId);
    testRepo.update(dest, mergedAs);

    assertNoProblems(ctl, null);

    FixInput fix = new FixInput();
    fix.expectMergedAs = mergedAs.name();
    assertProblems(
        ctl,
        fix,
        problem(
            "Expected merged commit "
                + mergedAs.name()
                + " has Change-Id: "
                + badId
                + ", but expected "
                + ctl.getChange().getKey().get()));
  }

  @Test
  public void expectedMergedCommitMatchesMultiplePatchSets() throws Exception {
    ChangeControl ctl1 = insertChange();
    PatchSet.Id psId1 = psUtil.current(db, ctl1.getNotes()).getId();
    String dest = ctl1.getChange().getDest().get();
    String rev = psUtil.current(db, ctl1.getNotes()).getRevision().get();
    RevCommit commit = testRepo.getRevWalk().parseCommit(ObjectId.fromString(rev));
    testRepo.branch(dest).update(commit);

    ChangeControl ctl2 = insertChange();
    ctl2 = incrementPatchSet(ctl2, commit);
    PatchSet.Id psId2 = psUtil.current(db, ctl2.getNotes()).getId();

    ChangeControl ctl3 = insertChange();
    ctl3 = incrementPatchSet(ctl3, commit);
    PatchSet.Id psId3 = psUtil.current(db, ctl3.getNotes()).getId();

    FixInput fix = new FixInput();
    fix.expectMergedAs = commit.name();
    assertProblems(
        ctl1,
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
    return updateFactory.create(db, project, userFactory.create(owner), TimeUtil.nowTs());
  }

  private ChangeControl insertChange() throws Exception {
    return insertChange(admin);
  }

  private ChangeControl insertChange(TestAccount owner) throws Exception {
    return insertChange(owner, "refs/heads/master");
  }

  private ChangeControl insertChange(TestAccount owner, String dest) throws Exception {
    Change.Id id = new Change.Id(sequences.nextChangeId());
    ChangeInserter ins;
    try (BatchUpdate bu = newUpdate(owner.getId())) {
      RevCommit commit = patchSetCommit(new PatchSet.Id(id, 1));
      ins =
          changeInserterFactory
              .create(id, commit, dest)
              .setValidatePolicy(CommitValidators.Policy.NONE)
              .setNotify(NotifyHandling.NONE)
              .setFireRevisionCreated(false)
              .setSendMail(false);
      bu.insertChange(ins).execute();
    }
    // Return control for admin regardless of owner.
    return changeControlFactory.controlFor(db, ins.getChange(), userFactory.create(adminId));
  }

  private PatchSet.Id nextPatchSetId(ChangeControl ctl) throws Exception {
    return ChangeUtil.nextPatchSetId(testRepo.getRepository(), ctl.getChange().currentPatchSetId());
  }

  private ChangeControl incrementPatchSet(ChangeControl ctl) throws Exception {
    return incrementPatchSet(ctl, patchSetCommit(nextPatchSetId(ctl)));
  }

  private ChangeControl incrementPatchSet(ChangeControl ctl, RevCommit commit) throws Exception {
    PatchSetInserter ins;
    try (BatchUpdate bu = newUpdate(ctl.getChange().getOwner())) {
      ins =
          patchSetInserterFactory
              .create(ctl, nextPatchSetId(ctl), commit)
              .setValidatePolicy(CommitValidators.Policy.NONE)
              .setFireRevisionCreated(false)
              .setNotify(NotifyHandling.NONE);
      bu.addOp(ctl.getId(), ins).execute();
    }
    return reload(ctl);
  }

  private ChangeControl reload(ChangeControl ctl) throws Exception {
    return changeControlFactory.controlFor(
        db, ctl.getChange().getProject(), ctl.getId(), ctl.getUser());
  }

  private RevCommit patchSetCommit(PatchSet.Id psId) throws Exception {
    RevCommit c = testRepo.commit().parent(tip).message("Change " + psId).create();
    return testRepo.parseBody(c);
  }

  private PatchSet insertMissingPatchSet(ChangeControl ctl, String rev) throws Exception {
    // Don't use BatchUpdate since we're manually updating the meta ref rather
    // than using ChangeUpdate.
    String subject = "Subject for missing commit";
    Change c = new Change(ctl.getChange());
    PatchSet.Id psId = nextPatchSetId(ctl);
    c.setCurrentPatchSet(psId, subject, c.getOriginalSubject());

    PatchSet ps = newPatchSet(psId, rev, adminId);
    db.patchSets().insert(singleton(ps));
    db.changes().update(singleton(c));

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
    RefUpdate ru = testRepo.getRepository().updateRef(refName, true);
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
  }

  private void addNoteDbCommit(Change.Id id, String commitMessage) throws Exception {
    if (!notesMigration.commitChangeWrites()) {
      return;
    }
    PersonIdent committer = serverIdent.get();
    PersonIdent author =
        noteUtil.newIdent(
            accountCache.get(admin.getId()).getAccount(),
            committer.getWhen(),
            committer,
            anonymousCowardName);
    testRepo
        .branch(RefNames.changeMetaRef(id))
        .commit()
        .author(author)
        .committer(committer)
        .message(commitMessage)
        .create();
  }

  private ObjectId getDestRef(ChangeControl ctl) throws Exception {
    return testRepo.getRepository().exactRef(ctl.getChange().getDest().get()).getObjectId();
  }

  private ChangeControl mergeChange(ChangeControl ctl) throws Exception {
    final ObjectId oldId = getDestRef(ctl);
    final ObjectId newId =
        ObjectId.fromString(psUtil.current(db, ctl.getNotes()).getRevision().get());
    final String dest = ctl.getChange().getDest().get();

    try (BatchUpdate bu = newUpdate(adminId)) {
      bu.addOp(
          ctl.getId(),
          new BatchUpdate.Op() {
            @Override
            public void updateRepo(RepoContext ctx) throws IOException {
              ctx.addRefUpdate(new ReceiveCommand(oldId, newId, dest));
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
    return reload(ctl);
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
      ChangeControl ctl, @Nullable FixInput fix, ProblemInfo first, ProblemInfo... rest) {
    List<ProblemInfo> expected = new ArrayList<>(1 + rest.length);
    expected.add(first);
    expected.addAll(Arrays.asList(rest));
    assertThat(checker.check(ctl, fix).problems()).containsExactlyElementsIn(expected).inOrder();
  }

  private void assertNoProblems(ChangeControl ctl, @Nullable FixInput fix) {
    assertThat(checker.check(ctl, fix).problems()).isEmpty();
  }
}
