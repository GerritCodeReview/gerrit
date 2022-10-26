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

package com.google.gerrit.acceptance.server.git.receive;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Tests for checking the validation of Change-Id during receive-commits. */
public class ReceiveCommitsChangeIdValidationIT extends AbstractDaemonTest {

  @Test
  public void disallowTruncatingChangeIdAcrossPatchSets() throws Exception {
    // Create the parent.
    RevCommit parent =
        commitBuilder().add("foo.txt", "foo content").message("base commit").create();
    testRepo.reset(parent);

    String changeId = "I0000000000000000000000000000000000000012";
    String truncatedChangeId = "I000000000000000000000000000000000000001";

    // The initial Change PS1 is accepted
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "blah",
            ImmutableMap.of("foo.txt", "first patch-set"),
            changeId)
        .setParent(parent)
        .to("refs/for/master")
        .assertOkStatus();

    // The Change PS2 is rejected because the Change-Id is truncated
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "blah\n\nChange-Id: " + truncatedChangeId,
            ImmutableMap.of("foo.txt", "second patch-set"))
        .setParent(parent)
        .to("refs/for/master")
        .assertErrorStatus("invalid Change-Id");
  }
}
