// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.RefNames;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

/** Tests that pushing to {@code refs/users/self} works with and without code review. */
public class PushToUsersSelfIT extends AbstractDaemonTest {

  @Test
  public void pushForReview() throws Exception {
    TestRepository<?> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_USERS_SELF + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit.Result mr =
        pushFactory
            .create(admin.newIdent(), allUsersRepo)
            .to("refs/for/" + RefNames.REFS_USERS_SELF);
    mr.assertOkStatus();
    assertThat(mr.getChange().change().getDest().branch())
        .isEqualTo(RefNames.refsUsers(admin.id()));
  }

  @Test
  public void pushToBranch() throws Exception {
    TestRepository<?> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_USERS_SELF + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit.Result mr =
        pushFactory.create(admin.newIdent(), allUsersRepo).to(RefNames.REFS_USERS_SELF);
    mr.assertOkStatus();
  }
}
