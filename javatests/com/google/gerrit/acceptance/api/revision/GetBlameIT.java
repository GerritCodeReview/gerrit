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
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
  public void forPatchsetLevelFile() throws Exception {
    PushOneCommit.Result r = createChange("Test Change", "foo.txt", "FOO");
    List<BlameInfo> blameInfos =
        gApi.changes().id(r.getChangeId()).current().file(PATCHSET_LEVEL).blameRequest().get();

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
  public void forPatchsetLevelFileFromBase() throws Exception {
    PushOneCommit.Result r = createChange("Test Change", "foo.txt", "FOO");
    List<BlameInfo> blameInfos =
        gApi.changes()
            .id(r.getChangeId())
            .current()
            .file(PATCHSET_LEVEL)
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

  @Test
  public void withIgnoreRevsFile() throws Exception {
    PushOneCommit.Result r1 = createChange("Change 1", "foo.txt", "line1\nline2\n");
    PushOneCommit.Result r2 = createChange("Change 2", "foo.txt", "line1\nline2 formatted\n");
    PushOneCommit.Result r3 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Change 3",
                ImmutableMap.of(
                    "foo.txt",
                    "line1\nline2 formatted\nline3\n",
                    ".git-blame-ignore-revs",
                    "# Formatting commit\n" + r2.getCommit().name() + "\n"))
            .to("refs/for/master");

    List<BlameInfo> blameInfos =
        gApi.changes().id(r3.getChangeId()).current().file("foo.txt").blameRequest().get();

    assertThat(blameInfos).hasSize(2);
    BlameInfo first = getBlameInfoForCommit(blameInfos, r1.getCommit().name());
    assertThat(first.ranges).hasSize(1);
    assertThat(first.ranges.get(0).start).isEqualTo(1);
    assertThat(first.ranges.get(0).end).isEqualTo(2);

    BlameInfo second = getBlameInfoForCommit(blameInfos, r3.getCommit().name());
    assertThat(second.ranges).hasSize(1);
    assertThat(second.ranges.get(0).start).isEqualTo(3);
    assertThat(second.ranges.get(0).end).isEqualTo(3);
  }

  @Test
  public void withIgnoreRevsFileFromBase() throws Exception {
    PushOneCommit.Result r1 = createChange("Change 1", "foo.txt", "line1\nline2\n");
    PushOneCommit.Result r2 = createChange("Change 2", "foo.txt", "line1\nline2 formatted\n");
    PushOneCommit.Result r3 =
        createChange("Change 3", ".git-blame-ignore-revs", r2.getCommit().name() + "\n");
    PushOneCommit.Result r4 =
        createChange("Change 4", "foo.txt", "line1\nline2 formatted\nline3\n");

    // Base of r4 is r3, which has .git-blame-ignore-revs ignoring r2.
    List<BlameInfo> blameAtR3 =
        gApi.changes()
            .id(r4.getChangeId())
            .current()
            .file("foo.txt")
            .blameRequest()
            .forBase(true)
            .get();
    assertThat(blameAtR3).hasSize(1);
    assertThat(blameAtR3.get(0).id).isEqualTo(r1.getCommit().name());
    assertThat(blameAtR3.get(0).ranges).hasSize(1);
    assertThat(blameAtR3.get(0).ranges.get(0).start).isEqualTo(1);
    assertThat(blameAtR3.get(0).ranges.get(0).end).isEqualTo(2);

    // Base of r3 is r2, which does not have .git-blame-ignore-revs yet.
    List<BlameInfo> blameAtR2 =
        gApi.changes()
            .id(r3.getChangeId())
            .current()
            .file("foo.txt")
            .blameRequest()
            .forBase(true)
            .get();
    assertThat(blameAtR2).hasSize(2);
    BlameInfo r1Blame = getBlameInfoForCommit(blameAtR2, r1.getCommit().name());
    assertThat(r1Blame.ranges).hasSize(1);
    assertThat(r1Blame.ranges.get(0).start).isEqualTo(1);
    assertThat(r1Blame.ranges.get(0).end).isEqualTo(1);
    BlameInfo r2Blame = getBlameInfoForCommit(blameAtR2, r2.getCommit().name());
    assertThat(r2Blame.ranges).hasSize(1);
    assertThat(r2Blame.ranges.get(0).start).isEqualTo(2);
    assertThat(r2Blame.ranges.get(0).end).isEqualTo(2);
  }

  @Test
  public void withIgnoreRevsFileAddedInSubsequentPatchSet() throws Exception {
    PushOneCommit.Result r1 = createChange("Change 1", "foo.txt", "line1\nline2\n");
    PushOneCommit.Result r2 = createChange("Change 2", "foo.txt", "line1\nline2 formatted\n");
    PushOneCommit.Result ps1 =
        createChange("Change 3", "foo.txt", "line1\nline2 formatted\nline3\n");

    // Patch set 1 does not have .git-blame-ignore-revs, so line 2 is blamed on r2.
    List<BlameInfo> ps1Blame =
        gApi.changes().id(ps1.getChangeId()).revision(1).file("foo.txt").blameRequest().get();
    assertThat(ps1Blame).hasSize(3);
    assertThat(getBlameInfoForCommit(ps1Blame, r2.getCommit().name()).ranges.get(0).start)
        .isEqualTo(2);

    // Patch set 2 introduces .git-blame-ignore-revs ignoring r2.
    PushOneCommit.Result ps2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "Change 3",
                ImmutableMap.of(
                    "foo.txt",
                    "line1\nline2 formatted\nline3\n",
                    ".git-blame-ignore-revs",
                    r2.getCommit().name() + "\n"),
                ps1.getChangeId())
            .to("refs/for/master");
    ps2.assertOkStatus();

    List<BlameInfo> ps2Blame =
        gApi.changes().id(ps2.getChangeId()).current().file("foo.txt").blameRequest().get();
    assertThat(ps2Blame).hasSize(2);
    BlameInfo first = getBlameInfoForCommit(ps2Blame, r1.getCommit().name());
    assertThat(first.ranges).hasSize(1);
    assertThat(first.ranges.get(0).start).isEqualTo(1);
    assertThat(first.ranges.get(0).end).isEqualTo(2);

    BlameInfo second = getBlameInfoForCommit(ps2Blame, ps2.getCommit().name());
    assertThat(second.ranges).hasSize(1);
    assertThat(second.ranges.get(0).start).isEqualTo(3);
    assertThat(second.ranges.get(0).end).isEqualTo(3);
  }

  @Test
  public void withIgnoreRevsFileInMergeCommit() throws Exception {
    PushOneCommit.Result r1 = createChange("Change 1", "foo.txt", "line1\nline2\n");
    PushOneCommit.Result r2 = createChange("Change 2", "foo.txt", "line1\nline2 formatted\n");

    PushOneCommit.Result p1 =
        createChange("Parent 1", ".git-blame-ignore-revs", r2.getCommit().name() + "\n");

    testRepo.reset(r2.getCommit());
    PushOneCommit.Result p2 = createChange("Parent 2", "other.txt", "other\n");

    PushOneCommit merge =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Merge Change",
            ImmutableMap.of(
                "foo.txt",
                "line1\nline2 formatted\nline3\n",
                ".git-blame-ignore-revs",
                r2.getCommit().name() + "\n",
                "other.txt",
                "other\n"));
    merge.setParents(ImmutableList.of(p1.getCommit(), p2.getCommit()));
    PushOneCommit.Result mergeResult = merge.to("refs/for/master");
    mergeResult.assertOkStatus();

    // Blame on the merge commit itself.
    List<BlameInfo> mergeBlame =
        gApi.changes().id(mergeResult.getChangeId()).current().file("foo.txt").blameRequest().get();
    assertThat(mergeBlame).hasSize(2);
    BlameInfo r1Blame = getBlameInfoForCommit(mergeBlame, r1.getCommit().name());
    assertThat(r1Blame.ranges).hasSize(1);
    assertThat(r1Blame.ranges.get(0).start).isEqualTo(1);
    assertThat(r1Blame.ranges.get(0).end).isEqualTo(2);

    // Blame on the automerge base of the 2-parent merge commit.
    List<BlameInfo> baseBlame =
        gApi.changes()
            .id(mergeResult.getChangeId())
            .current()
            .file("foo.txt")
            .blameRequest()
            .forBase(true)
            .get();
    assertThat(baseBlame).hasSize(1);
    assertThat(baseBlame.get(0).id).isEqualTo(r1.getCommit().name());
    assertThat(baseBlame.get(0).ranges).hasSize(1);
    assertThat(baseBlame.get(0).ranges.get(0).start).isEqualTo(1);
    assertThat(baseBlame.get(0).ranges.get(0).end).isEqualTo(2);
  }

  @Test
  public void withIgnoreRevsFileInlineCommentsAndInvalidLines() throws Exception {
    PushOneCommit.Result r1 = createChange("Change 1", "foo.txt", "line1\nline2\n");
    PushOneCommit.Result r2 = createChange("Change 2", "foo.txt", "line1\nline2 formatted\n");

    // Create a sibling commit that is not an ancestor of r3.
    PushOneCommit.Result nonAncestor = createChange("Unrelated", "other.txt", "other\n");
    testRepo.reset(r2.getCommit());

    PushOneCommit.Result r3 =
        createChange(
            "Change 3",
            ".git-blame-ignore-revs",
            "# Header comment\n\nnot-a-valid-sha\n"
                + nonAncestor.getCommit().name()
                + "\n"
                + "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef\n"
                + r2.getCommit().name()
                + "\t# tab-separated inline comment\n");

    List<BlameInfo> blameInfos =
        gApi.changes().id(r3.getChangeId()).current().file("foo.txt").blameRequest().get();

    assertThat(blameInfos).hasSize(1);
    assertThat(blameInfos.get(0).id).isEqualTo(r1.getCommit().name());
    assertThat(blameInfos.get(0).ranges).hasSize(1);
    assertThat(blameInfos.get(0).ranges.get(0).start).isEqualTo(1);
    assertThat(blameInfos.get(0).ranges.get(0).end).isEqualTo(2);
  }

  private static BlameInfo getBlameInfoForCommit(List<BlameInfo> blameInfos, String commitId) {
    return blameInfos.stream()
        .filter(b -> commitId.equals(b.id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No BlameInfo found for commit " + commitId));
  }
}
