// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.reviewdb.client.Patch.MERGE_LIST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class MergeListIT extends AbstractDaemonTest {

  private String changeId;
  private RevCommit merge;
  private RevCommit parent1;
  private RevCommit grandParent1;
  private RevCommit parent2;
  private RevCommit grandParent2;

  @Inject private ChangeEditModifier modifier;

  @Inject private ChangeEditUtil editUtil;

  @Before
  public void setup() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();

    PushOneCommit.Result gp1 =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                testRepo,
                "grand parent 1",
                ImmutableMap.of("foo", "foo-1.1", "bar", "bar-1.1"))
            .to("refs/for/master");
    grandParent1 = gp1.getCommit();

    PushOneCommit.Result p1 =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                testRepo,
                "parent 1",
                ImmutableMap.of("foo", "foo-1.2", "bar", "bar-1.2"))
            .to("refs/for/master");
    parent1 = p1.getCommit();

    // reset HEAD in order to create a sibling of the first change
    testRepo.reset(initial);

    PushOneCommit.Result gp2 =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                testRepo,
                "grand parent 2",
                ImmutableMap.of("foo", "foo-2.1", "bar", "bar-2.1"))
            .to("refs/for/master");
    grandParent2 = gp2.getCommit();

    PushOneCommit.Result p2 =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                testRepo,
                "parent 2",
                ImmutableMap.of("foo", "foo-2.2", "bar", "bar-2.2"))
            .to("refs/for/master");
    parent2 = p2.getCommit();

    PushOneCommit m =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            "merge",
            ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(p1.getCommit(), p2.getCommit()));
    PushOneCommit.Result result = m.to("refs/for/master");
    result.assertOkStatus();
    merge = result.getCommit();
    changeId = result.getChangeId();
  }

  @Test
  public void getMergeList() throws Exception {
    List<CommitInfo> mergeList = current(changeId).getMergeList().get();
    assertThat(mergeList).hasSize(2);
    assertThat(mergeList.get(0).commit).isEqualTo(parent2.name());
    assertThat(mergeList.get(1).commit).isEqualTo(grandParent2.name());

    mergeList = current(changeId).getMergeList().withUninterestingParent(2).get();
    assertThat(mergeList).hasSize(2);
    assertThat(mergeList.get(0).commit).isEqualTo(parent1.name());
    assertThat(mergeList.get(1).commit).isEqualTo(grandParent1.name());
  }

  @Test
  public void getMergeListContent() throws Exception {
    BinaryResult bin = current(changeId).file(MERGE_LIST).content();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String content = new String(os.toByteArray(), UTF_8);
    assertThat(content).isEqualTo(getMergeListContent(parent2, grandParent2));
  }

  @Test
  public void getFileList() throws Exception {
    assertThat(getFiles(changeId)).contains(MERGE_LIST);
    assertThat(getFiles(changeId, 1)).contains(MERGE_LIST);
    assertThat(getFiles(changeId, 2)).contains(MERGE_LIST);

    assertThat(getFiles(createChange().getChangeId())).doesNotContain(MERGE_LIST);
  }

  @Test
  public void getDiffForMergeList() throws Exception {
    DiffInfo diff = getMergeListDiff(changeId);
    assertDiffForNewFile(diff, merge, MERGE_LIST, getMergeListContent(parent2, grandParent2));

    diff = getMergeListDiff(changeId, 1);
    assertDiffForNewFile(diff, merge, MERGE_LIST, getMergeListContent(parent2, grandParent2));

    diff = getMergeListDiff(changeId, 2);
    assertDiffForNewFile(diff, merge, MERGE_LIST, getMergeListContent(parent1, grandParent1));
  }

  @Test
  public void editMergeList() throws Exception {
    ChangeData cd = getOnlyElement(queryProvider.get().byKeyPrefix(changeId));
    modifier.createEdit(cd.change(), cd.currentPatchSet());

    exception.expect(InvalidPathException.class);
    exception.expectMessage("Invalid path: " + MERGE_LIST);
    modifier.modifyFile(
        editUtil.byChange(cd.change()).get(), MERGE_LIST, RawInputUtil.create("new content"));
  }

  @Test
  public void deleteMergeList() throws Exception {
    ChangeData cd = getOnlyElement(queryProvider.get().byKeyPrefix(changeId));
    modifier.createEdit(cd.change(), cd.currentPatchSet());

    exception.expect(InvalidChangeOperationException.class);
    exception.expectMessage("no changes were made");
    modifier.deleteFile(editUtil.byChange(cd.change()).get(), MERGE_LIST);
  }

  private String getMergeListContent(RevCommit... commits) {
    StringBuilder mergeList = new StringBuilder("Merge List:\n\n");
    for (RevCommit c : commits) {
      mergeList
          .append("* ")
          .append(c.abbreviate(8).name())
          .append(" ")
          .append(c.getShortMessage())
          .append("\n");
    }
    return mergeList.toString();
  }

  private Set<String> getFiles(String changeId) throws Exception {
    return current(changeId).files().keySet();
  }

  private Set<String> getFiles(String changeId, int parent) throws Exception {
    return current(changeId).files(parent).keySet();
  }

  private DiffInfo getMergeListDiff(String changeId) throws Exception {
    return current(changeId).file(MERGE_LIST).diff();
  }

  private DiffInfo getMergeListDiff(String changeId, int parent) throws Exception {
    return current(changeId).file(MERGE_LIST).diff(parent);
  }

  private RevisionApi current(String changeId) throws Exception {
    return gApi.changes().id(changeId).current();
  }
}
