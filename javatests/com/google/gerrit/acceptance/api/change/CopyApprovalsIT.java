// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.approval.RecursiveApprovalCopier;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Test;

public class CopyApprovalsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RecursiveApprovalCopier recursiveApprovalCopier;

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        CopyApprovalsReferenceUpdateListener referenceUpdateListener =
            new CopyApprovalsReferenceUpdateListener();

        bind(CopyApprovalsReferenceUpdateListener.class).toInstance(referenceUpdateListener);
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
            .toInstance(referenceUpdateListener);
      }
    };
  }

  @Test
  public void multipleProjects() throws Exception {
    Project.NameKey secondProject = projectOperations.newProject().name("secondProject").create();
    TestRepository<InMemoryRepository> secondRepo = cloneProject(secondProject, admin);

    PushOneCommit.Result change1 = createChange();
    gApi.changes().id(change1.getChangeId()).current().review(ReviewInput.recommend());
    PushOneCommit.Result change2 = createChange(secondRepo);
    gApi.changes().id(change2.getChangeId()).current().review(ReviewInput.dislike());

    // these amends are reworks so votes will not be copied.
    amendChange(change1.getChangeId());
    amendChange(change1.getChangeId());
    amendChange(change1.getChangeId());

    amendChange(change2.getChangeId(), "refs/for/master", admin, secondRepo);
    amendChange(change2.getChangeId(), "refs/for/master", admin, secondRepo);
    amendChange(change2.getChangeId(), "refs/for/master", admin, secondRepo);

    // votes don't exist on the new patch-set.
    assertThat(gApi.changes().id(change1.getChangeId()).current().votes()).isEmpty();
    assertThat(gApi.changes().id(change2.getChangeId()).current().votes()).isEmpty();

    // change the project config to make the vote that was not copied to be copied once we do the
    // schema upgrade.
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(true));
      u.save();
    }

    recursiveApprovalCopier.persistStandalone();

    ApprovalInfo vote1 =
        Iterables.getOnlyElement(
            gApi.changes().id(change1.getChangeId()).current().votes().values());
    assertThat(vote1.value).isEqualTo(1);
    assertThat(vote1._accountId).isEqualTo(admin.id().get());

    ApprovalInfo vote2 =
        Iterables.getOnlyElement(
            gApi.changes().id(change2.getChangeId()).current().votes().values());
    assertThat(vote2.value).isEqualTo(-1);
    assertThat(vote2._accountId).isEqualTo(admin.id().get());
  }

  @Test
  public void corruptChangeInOneProject_OtherProjectsProcessed() throws Exception {
    Project.NameKey corruptProject = projectOperations.newProject().name("corruptProject").create();
    TestRepository<InMemoryRepository> corruptRepo = cloneProject(corruptProject, admin);

    PushOneCommit.Result change1 = createChange();
    gApi.changes().id(change1.getChangeId()).current().review(ReviewInput.recommend());
    PushOneCommit.Result change2 = createChange(corruptRepo);
    gApi.changes().id(change2.getChangeId()).current().review(ReviewInput.dislike());

    // these amends are reworks so votes will not be copied.
    amendChange(change1.getChangeId());
    amendChange(change2.getChangeId(), "refs/for/master", admin, corruptRepo);

    // votes don't exist on the new patch-set.
    assertThat(gApi.changes().id(change1.getChangeId()).current().votes()).isEmpty();
    assertThat(gApi.changes().id(change2.getChangeId()).current().votes()).isEmpty();

    // change the project config to make the vote that was not copied to be copied once we do the
    // schema upgrade.
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(true));
      u.save();
    }

    // make the meta-ref of change2 corrupt by updating it to the commit of the current patch-set
    ObjectId correctMetaRefObjectId;
    String metaRef = RefNames.changeMetaRef(change2.getChange().getId());
    try (TestRepository<InMemoryRepository> serverSideCorruptRepo =
        new TestRepository<>((InMemoryRepository) repoManager.openRepository(corruptProject))) {
      RefUpdate ru = forceUpdate(serverSideCorruptRepo, metaRef, change2.getPatchSet().commitId());
      correctMetaRefObjectId = ru.getOldObjectId();

      try {
        recursiveApprovalCopier.persistStandalone();
        fail("Expected exception when a project contains corrupt change");
      } catch (Exception e) {
        assertThat(e.getMessage()).contains("check the logs");
      } finally {
        // fix the meta-ref by setting it back to its correct objectId
        forceUpdate(serverSideCorruptRepo, metaRef, correctMetaRefObjectId);
      }
    }

    ApprovalInfo vote1 =
        Iterables.getOnlyElement(
            gApi.changes().id(change1.getChangeId()).current().votes().values());
    assertThat(vote1.value).isEqualTo(1);
    assertThat(vote1._accountId).isEqualTo(admin.id().get());
  }

  private RefUpdate forceUpdate(
      TestRepository<InMemoryRepository> repo, String ref, ObjectId newObjectId)
      throws IOException {
    RefUpdate ru = repo.getRepository().updateRef(ref);
    ru.setNewObjectId(newObjectId);
    ru.forceUpdate();
    return ru;
  }

  @Test
  public void changeWithPersistedVotesNotHarmed() throws Exception {
    // change the project config to copy all votes
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(true));
      u.save();
    }

    PushOneCommit.Result change = createChange();
    gApi.changes().id(change.getChangeId()).current().review(ReviewInput.recommend());
    amendChange(change.getChangeId());

    // vote exists on new patch-set.
    ApprovalInfo vote =
        Iterables.getOnlyElement(
            gApi.changes().id(change.getChangeId()).current().votes().values());

    ChangeNotes notes = notesFactory.createChecked(project, change.getChange().getId()).load();
    ImmutableListMultimap<PatchSet.Id, PatchSetApproval> multimap1 = notes.getApprovalsWithCopied();

    recursiveApprovalCopier.persist(change.getChange().change());

    ChangeNotes notes2 = notesFactory.createChecked(project, change.getChange().getId()).load();
    ImmutableListMultimap<PatchSet.Id, PatchSetApproval> multimap2 =
        notes2.getApprovalsWithCopied();
    assertThat(multimap1).containsExactlyEntriesIn(multimap2);

    // the vote hasn't changed.
    assertThat(
            Iterables.getOnlyElement(
                gApi.changes().id(change.getChangeId()).current().votes().values()))
        .isEqualTo(vote);
  }

  @Test
  public void multipleChanges() throws Exception {
    List<Result> changes = new ArrayList<>();

    // The test also passes with 1000, but we replaced this number to 5 to speed up the test.
    for (int i = 0; i < 5; i++) {
      PushOneCommit.Result change = createChange();
      gApi.changes().id(change.getChangeId()).current().review(ReviewInput.recommend());

      // this amend is a rework so votes will not be copied.
      amendChange(change.getChangeId());

      changes.add(change);

      // votes don't exist on the new patch-set for all changes.
      assertThat(gApi.changes().id(change.getChangeId()).current().votes()).isEmpty();
    }

    // change the project config to make the vote that was not copied to be copied once we do the
    // schema upgrade.
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(true));
      u.save();
    }

    recursiveApprovalCopier.persist(project, null);

    for (PushOneCommit.Result change : changes) {
      ApprovalInfo vote1 =
          Iterables.getOnlyElement(
              gApi.changes().id(change.getChangeId()).current().votes().values());
      assertThat(vote1.value).isEqualTo(1);
      assertThat(vote1._accountId).isEqualTo(admin.id().get());
    }
  }

  @Test
  public void refUpdateNotified() throws Exception {
    PushOneCommit.Result change = createChange();
    gApi.changes().id(change.getChangeId()).current().review(ReviewInput.recommend());

    // this amend is a rework so votes will not be copied.
    amendChange(change.getChangeId());

    // votes don't exist on the new patch-set for all changes.
    assertThat(gApi.changes().id(change.getChangeId()).current().votes()).isEmpty();

    // change the project config to make the vote that was not copied to be copied once we do the
    // schema upgrade.
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(true));
      u.save();
    }

    ObjectId metaId = change.getChange().notes().getMetaId();
    recursiveApprovalCopier.persist(project, null);

    ApprovalInfo vote1 =
        Iterables.getOnlyElement(
            gApi.changes().id(change.getChangeId()).current().votes().values());
    assertThat(vote1.value).isEqualTo(1);
    assertThat(vote1._accountId).isEqualTo(admin.id().get());

    CopyApprovalsReferenceUpdateListener testListener = testListener();
    assertThat(testListener.refUpdateFor(metaId)).isTrue();
  }

  @Test
  public void oneCorruptChange_otherChangesProcessed() throws Exception {
    PushOneCommit.Result good = createChange();
    gApi.changes().id(good.getChangeId()).current().review(ReviewInput.recommend());
    // this amend is a rework so votes will not be copied.
    amendChange(good.getChangeId());

    PushOneCommit.Result corrupt = createChange();

    // change the project config to make the vote that was not copied to be copied once we do the
    // schema upgrade.
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(true));
      u.save();
    }

    // make the meta-ref corrupt by updating it to the commit of the current patch-set
    String metaRef = RefNames.changeMetaRef(corrupt.getChange().getId());
    try (TestRepository<InMemoryRepository> serverSideTestRepo =
        new TestRepository<>((InMemoryRepository) repoManager.openRepository(project))) {
      RefUpdate ru = forceUpdate(serverSideTestRepo, metaRef, corrupt.getPatchSet().commitId());
      try {
        recursiveApprovalCopier.persist(project, null);
      } finally {
        forceUpdate(serverSideTestRepo, metaRef, ru.getOldObjectId());
      }
    }

    ApprovalInfo vote1 =
        Iterables.getOnlyElement(gApi.changes().id(good.getChangeId()).current().votes().values());
    assertThat(vote1.value).isEqualTo(1);
    assertThat(vote1._accountId).isEqualTo(admin.id().get());
  }

  private CopyApprovalsReferenceUpdateListener testListener() {
    return server.getTestInjector().getInstance(CopyApprovalsReferenceUpdateListener.class);
  }

  private static class CopyApprovalsReferenceUpdateListener implements GitReferenceUpdatedListener {
    private final AtomicLongMap<String> countsByOldObjectId = AtomicLongMap.create();

    @Override
    public void onGitReferenceUpdated(Event event) {
      String oldObjectId = event.getOldObjectId();
      countsByOldObjectId.incrementAndGet(oldObjectId);
    }

    boolean refUpdateFor(ObjectId metaRef) {
      return countsByOldObjectId.containsKey(metaRef.getName());
    }
  }
}
