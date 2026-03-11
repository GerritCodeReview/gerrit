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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractSubmit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class SubmitWithDeletedUserIT extends AbstractSubmit {

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      value = ExperimentFeaturesConstants.IGNORE_VOTES_OF_DELETED_ACCOUNTS)
  public void submitWithApprovalFromDeletedUserFails() throws Exception {
    // Create a new user
    TestAccount user2 = accountCreator.create("user2", "user2@example.com", "User 2", null);
    
    // Create a change and approve it with user2
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user2.id());
    approve(r.getChangeId());
    
    // "Delete" user2 by removing their account ref and evicting from cache
    deleteAccount(user2.id());
    
    // Try to submit as admin. 
    // It should fail because user2's approval is now ignored, so the change is not ready.
    requestScopeOperations.setApiUser(admin.id());
    ResourceConflictException thrown = assertThrows(
        ResourceConflictException.class,
        () -> submit(r.getChangeId()));
    assertThat(thrown).hasMessageThat().contains("is not ready");
  }

  @Test
  public void submitWithApprovalFromDeletedUserSucceedsIfExperimentDisabled() throws Exception {
    // Create a new user
    TestAccount user2 = accountCreator.create("user2", "user2@example.com", "User 2", null);
    
    // Create a change and approve it with user2
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user2.id());
    approve(r.getChangeId());
    
    // "Delete" user2
    deleteAccount(user2.id());
    
    // Try to submit as admin. 
    // It should succeed because the experiment is NOT enabled, so deleted user's votes are NOT ignored.
    requestScopeOperations.setApiUser(admin.id());
    submit(r.getChangeId());
    r.assertChangeMerged();
  }

  private void deleteAccount(Account.Id id) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(RefNames.refsUsers(id));
      ru.setForceUpdate(true);
      RefUpdate.Result result = ru.delete();
      assertThat(result).isEqualTo(RefUpdate.Result.FORCED);
    }
    accountCache.evict(id);
  }
}
