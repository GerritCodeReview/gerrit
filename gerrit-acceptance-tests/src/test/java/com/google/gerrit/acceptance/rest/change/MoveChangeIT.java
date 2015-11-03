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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Branch;

import org.junit.Test;

@NoHttpd
public class MoveChangeIT extends AbstractDaemonTest {
  @Test
  public void moveChange_shortRef() throws Exception {
    // Move change to a different branch using short ref name
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch =
        new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch).get();
    gApi.changes().id(r.getChangeId()).move(newBranch.get());
    assertThat(r.getChange().change().getDest().equals(newBranch));
  }

  @Test
  public void moveChange_fullRef() throws Exception {
    // Move change to a different branch using full ref name
  }

  @Test
  public void moveChangeWithMessage() throws Exception {
    // Provide a message using --message flag (needed?)
  }

  @Test
  public void moveChangeToSameRefAsCurrent() throws Exception {
    // Move change to the branch same as change's destination
  }

  @Test
  public void moveChange_sameChangeId() throws Exception {
    // Move change to a branch with existing change with same change ID
  }

  @Test
  public void moveChangeToNonExistentRef() throws Exception {
    // Move change to a non-existing branch
  }

  @Test
  public void moveClosedChange() throws Exception {
    // Move a change which is not open
  }

  @Test
  public void moveMergeCommitChange() throws Exception {
    // Move a change which has a merge commit as the current PS
  }

  @Test
  public void moveChangeToBranch_WithoutUploadPerms() throws Exception {
    // Move change to a destination where user doesn't have upload permissions
  }

  @Test
  public void moveChangeFromBranch_WithoutAbandonPerms() throws Exception {
    // Move change for which user does not have abandon permissions
  }

  @Test
  public void moveChangeToBranchThatContainsCurrentCommit() throws Exception {
    // Move change to a branch for which current PS revision is reachable from tip
  }

  @Test
  public void moveChange_WithCurrentPatchSetLocked() throws Exception {
    // Move change that is locked
  }
}
