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

package com.google.gerrit.server.git.receive;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

public class BranchCommitValidatorTest {
  @Test
  public void directBranchUpdateUsesDestinationMergePermission() {
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/heads/master"))).isTrue();
  }

  @Test
  public void magicBranchUpdateUsesReviewMergePermission() {
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/for/master"))).isFalse();
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/for/refs/heads/master")))
        .isFalse();
  }

  @Test
  public void patchSetRefUpdateUsesReviewMergePermission() {
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/changes/34/1234/2")))
        .isFalse();
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/changes/1234"))).isFalse();
  }

  @Test
  public void refsMetaConfigUpdateUsesDestinationMergePermission() {
    // refs/meta/config is neither a magic branch nor a patch-set ref, so a direct merge push
    // there is scoped to the destination ref's Push Merge Commit permission (with the legacy
    // refs/for/ fallback), like any other direct branch update.
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/meta/config"))).isTrue();
  }

  @Test
  public void tagUpdateUsesDestinationMergePermission() {
    // A lightweight tag pointing at a merge commit is a direct ref update.
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/tags/v1.0"))).isTrue();
  }

  @Test
  public void magicBranchWithOptionsUsesReviewMergePermission() {
    assertThat(BranchCommitValidator.isDirectRefUpdate(command("refs/for/master%topic=t")))
        .isFalse();
  }

  private static ReceiveCommand command(String refName) {
    return new ReceiveCommand(
        ObjectId.zeroId(),
        ObjectId.fromString("0000000000000000000000000000000000000001"),
        refName);
  }
}
