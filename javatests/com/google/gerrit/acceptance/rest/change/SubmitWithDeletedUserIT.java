// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class SubmitWithDeletedUserIT extends AbstractSubmit {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_IF_NECESSARY;
  }

  @Before
  public void setupPermissions() {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();
  }

  @Test
  public void submitWithApprovalFromDeletedUserFails() throws Exception {
    // Create a new user
    TestAccount user2 = accountCreator.create("user2", "user2@example.com", "User 2", null);

    // Create a change and approve it with user2
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user2.id());
    approve(r.getChangeId());

    // "Delete" user2 by removing their account ref
    deleteAccount(user2.id());

    // Try to submit as admin.
    // It should fail because user2's approval is now ignored, so the change is not ready.
    requestScopeOperations.setApiUser(admin.id());
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).current().submit());
    assertThat(thrown).hasMessageThat().contains("is not ready");
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.CONSIDER_VOTES_OF_DELETED_ACCOUNTS)
  public void submitWithApprovalFromDeletedUserSucceedsIfExperimentEnabled() throws Throwable {
    // Create a new user
    TestAccount user2 = accountCreator.create("user2", "user2@example.com", "User 2", null);

    // Create a change and approve it with user2
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user2.id());
    approve(r.getChangeId());

    // "Delete" user2
    deleteAccount(user2.id());

    // Try to submit as admin.
    // It should succeed because the experiment IS enabled, so deleted user's votes are NOT
    // ignored.
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  private void deleteAccount(Account.Id id) throws Exception {
    try (RefUpdateContext ctx =
        RefUpdateContext.open(RefUpdateContext.RefUpdateType.ACCOUNTS_UPDATE)) {
      try (Repository repo = repoManager.openRepository(allUsers)) {
        RefUpdate ru = repo.updateRef(RefNames.refsUsers(id));
        ru.setForceUpdate(true);
        RefUpdate.Result result = ru.delete();
        assertThat(result).isEqualTo(RefUpdate.Result.FORCED);
      }
    }
  }
}
