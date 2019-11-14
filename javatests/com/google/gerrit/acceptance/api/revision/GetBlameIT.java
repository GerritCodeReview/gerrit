// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.BlameInfo;
import com.google.gerrit.extensions.common.RangeInfo;
import java.util.List;
import org.junit.Test;

public class GetBlameIT extends AbstractDaemonTest {
  @Test
  public void forNonExistingFile() throws Exception {
    PushOneCommit.Result r = createChange("Test Change", "foo.txt", "FOO");
    List<BlameInfo> blameInfos =
        gApi.changes().id(r.getChangeId()).current().file("non-existing.txt").blameRequest().get();

    // File doesn't exist in commit.
    assertThat(blameInfos).isEmpty();
  }

  @Test
  public void forNonExistingFileFromBase() throws Exception {
    PushOneCommit.Result r = createChange("Test Change", "foo.txt", "FOO");
    List<BlameInfo> blameInfos =
        gApi.changes()
            .id(r.getChangeId())
            .current()
            .file("non-existing.txt")
            .blameRequest()
            .forBase(true)
            .get();

    // File doesn't exist in base commit.
    assertThat(blameInfos).isEmpty();
  }

  @Test
  public void forNewlyAddedFile() throws Exception {
    PushOneCommit.Result r = createChange("Test Change", "foo.txt", "FOO");
    List<BlameInfo> blameInfos =
        gApi.changes().id(r.getChangeId()).current().file("foo.txt").blameRequest().get();

    assertThat(blameInfos).hasSize(1);
    BlameInfo blameInfo = blameInfos.get(0);
    assertThat(blameInfo.author).isEqualTo(admin.fullName());
    assertThat(blameInfo.id).isEqualTo(r.getCommit().getId().name());
    assertThat(blameInfo.commitMsg).isEqualTo(r.getCommit().getFullMessage());
    assertThat(blameInfo.time).isEqualTo(r.getCommit().getCommitTime());

    assertThat(blameInfo.ranges).hasSize(1);
    RangeInfo rangeInfo = blameInfo.ranges.get(0);
    assertThat(rangeInfo.start).isEqualTo(1);
    assertThat(rangeInfo.end).isEqualTo(1);
  }

  @Test
  public void forNewlyAddedFileFromBase() throws Exception {
    String changeId = createChange("Test Change", "foo.txt", "FOO").getChangeId();
    List<BlameInfo> blameInfos =
        gApi.changes().id(changeId).current().file("foo.txt").blameRequest().forBase(true).get();

    // File doesn't exist in base commit.
    assertThat(blameInfos).isEmpty();
  }

  @Test
  public void forRecreatedFile() throws Exception {
    // Create change that adds 'foo.txt'.
    createChange("Change 1", "foo.txt", "FOO");

    // Create change that deletes 'foo.txt'.
    pushFactory
        .create(admin.newIdent(), testRepo, "Change 2", "foo.txt", "FOO")
        .rm("refs/for/master");

    // Create change that recreates 'foo.txt'.
    PushOneCommit.Result r = createChange("Change 3", "foo.txt", "FOO");
    List<BlameInfo> blameInfos =
        gApi.changes().id(r.getChangeId()).current().file("foo.txt").blameRequest().get();

    assertThat(blameInfos).hasSize(1);
    BlameInfo blameInfo = blameInfos.get(0);
    assertThat(blameInfo.author).isEqualTo(admin.fullName());
    assertThat(blameInfo.id).isEqualTo(r.getCommit().getId().name());
    assertThat(blameInfo.commitMsg).isEqualTo(r.getCommit().getFullMessage());
    assertThat(blameInfo.time).isEqualTo(r.getCommit().getCommitTime());

    assertThat(blameInfo.ranges).hasSize(1);
    RangeInfo rangeInfo = blameInfo.ranges.get(0);
    assertThat(rangeInfo.start).isEqualTo(1);
    assertThat(rangeInfo.end).isEqualTo(1);
  }

  @Test
  public void forRecreatedFileFromBase() throws Exception {
    // Create change that adds 'foo.txt'.
    createChange("Change 1", "foo.txt", "FOO");

    // Create change that deletes 'foo.txt'.
    pushFactory
        .create(admin.newIdent(), testRepo, "Change 2", "foo.txt", "FOO")
        .rm("refs/for/master");

    // Create change that recreates 'foo.txt'.
    String changeId3 = createChange("Change 3", "foo.txt", "FOO").getChangeId();
    List<BlameInfo> blameInfos =
        gApi.changes().id(changeId3).current().file("foo.txt").blameRequest().forBase(true).get();

    // File doesn't exist in base commit.
    assertThat(blameInfos).isEmpty();
  }
}
