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

package com.google.gerrit.acceptance.server.git.receive;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Tests for applying limits to e.g. number of files per change. */
public class ReceiveCommitsLimitsIT extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "change.maxFiles", value = "2")
  public void limitFileCount() throws Exception {
    // Create the parent.
    RevCommit parent =
        commitBuilder()
            .add("foo.txt", "same old, same old")
            .add("bar.txt", "bar")
            .message("blah")
            .create();
    testRepo.reset(parent);

    // A commit with 2 files is OK.
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "blah",
            ImmutableMap.of(
                "foo.txt", "same old, same old", "bar.txt", "changed file", "baz.txt", "new file"))
        .setParent(parent)
        .to("refs/for/master")
        .assertOkStatus();

    // A commit with 3 files is rejected.
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "blah",
            ImmutableMap.of(
                "foo.txt",
                "same old, same old",
                "bar.txt",
                "changed file",
                "baz.txt",
                "new file",
                "boom.txt",
                "boom!"))
        .setParent(parent)
        .to("refs/for/master")
        .assertErrorStatus("Exceeding maximum number of files per change (3 > 2)");
  }

  @Test
  @GerritConfig(name = "change.maxFiles", value = "1")
  public void limitFileCount_merge() throws Exception {
    // Create the parents.
    RevCommit commitFoo =
        commitBuilder().add("foo.txt", "same old, same old").message("blah").create();
    RevCommit commitBar =
        testRepo
            .branch("branch")
            .commit()
            .insertChangeId()
            .add("bar.txt", "bar")
            .message("blah")
            .create();
    testRepo.reset(commitFoo);

    // compared to AUTO_MERGE only one file is changed -> OK
    pushFactory
        .create(
            admin.newIdent(), testRepo, "blah", ImmutableMap.of("foo.txt", "same old, same old"))
        .setParents(ImmutableList.of(commitFoo, commitBar))
        .to("refs/for/master")
        .assertOkStatus();

    // compared to AUTO_MERGE two files are changed -> rejected
    pushFactory
        .create(admin.newIdent(), testRepo, "blah", ImmutableMap.of("foo.txt", "changed"))
        .setParents(ImmutableList.of(commitFoo, commitBar))
        .to("refs/for/master")
        .assertErrorStatus("Exceeding maximum number of files per change (2 > 1)");
  }
}
