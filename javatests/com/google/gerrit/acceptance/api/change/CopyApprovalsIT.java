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

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.server.approval.RecursiveApprovalCopier;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class CopyApprovalsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RecursiveApprovalCopier recursiveApprovalCopier;

  @Test
  public void multipleProjects() throws Exception {
    projectOperations.newProject().name("secondProject").create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);

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
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(/* copyAnyScore= */ true));
      u.save();
    }

    recursiveApprovalCopier.persist();

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
  public void changeWithPersistedVotesNotHarmed() throws Exception {
    // change the project config to copy all votes
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(/* copyAnyScore= */ true));
      u.save();
    }

    PushOneCommit.Result change = createChange();
    gApi.changes().id(change.getChangeId()).current().review(ReviewInput.recommend());
    amendChange(change.getChangeId());

    // vote exists on new patch-set.
    ApprovalInfo vote =
        Iterables.getOnlyElement(
            gApi.changes().id(change.getChangeId()).current().votes().values());

    recursiveApprovalCopier.persist();

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
      u.getConfig()
          .updateLabelType(LabelId.CODE_REVIEW, b -> b.setCopyAnyScore(/* copyAnyScore= */ true));
      u.save();
    }

    recursiveApprovalCopier.persist();

    for (PushOneCommit.Result change : changes) {
      ApprovalInfo vote1 =
          Iterables.getOnlyElement(
              gApi.changes().id(change.getChangeId()).current().votes().values());
      assertThat(vote1.value).isEqualTo(1);
      assertThat(vote1._accountId).isEqualTo(admin.id().get());
    }
  }
}
