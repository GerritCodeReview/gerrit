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

import static com.google.common.truth.Truth.assertThat;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.CommitInfo;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import java.util.List;

@NoHttpd
public class GetMergeListIT extends AbstractDaemonTest {

  @Test
  public void getMergeList() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();

    PushOneCommit.Result gp1 = pushFactory
        .create(db, admin.getIdent(), testRepo, "grand parent 1",
            ImmutableMap.of("foo", "foo-1.1", "bar", "bar-1.1"))
        .to("refs/for/master");

    PushOneCommit.Result p1 = pushFactory
        .create(db, admin.getIdent(), testRepo, "parent 1",
            ImmutableMap.of("foo", "foo-1.2", "bar", "bar-1.2"))
        .to("refs/for/master");

    // reset HEAD in order to create a sibling of the first change
    testRepo.reset(initial);

    PushOneCommit.Result gp2 = pushFactory
        .create(db, admin.getIdent(), testRepo, "grand parent 1",
            ImmutableMap.of("foo", "foo-2.1", "bar", "bar-2.1"))
        .to("refs/for/master");

    PushOneCommit.Result p2 = pushFactory
        .create(db, admin.getIdent(), testRepo, "parent 2",
            ImmutableMap.of("foo", "foo-2.2", "bar", "bar-2.2"))
        .to("refs/for/master");

    PushOneCommit m = pushFactory.create(db, admin.getIdent(), testRepo,
        "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(p1.getCommit(), p2.getCommit()));
    PushOneCommit.Result result = m.to("refs/for/master");
    result.assertOkStatus();

    List<CommitInfo> mergeList =
        gApi.changes().id(result.getChangeId()).current().getMergeList().get();
    assertThat(mergeList).hasSize(2);
    assertThat(mergeList.get(0).commit).isEqualTo(p2.getCommit().name());
    assertThat(mergeList.get(1).commit).isEqualTo(gp2.getCommit().name());

    mergeList = gApi.changes().id(result.getChangeId()).current().getMergeList()
        .withUninterestingParent(2).get();
    assertThat(mergeList).hasSize(2);
    assertThat(mergeList.get(0).commit).isEqualTo(p1.getCommit().name());
    assertThat(mergeList.get(1).commit).isEqualTo(gp1.getCommit().name());
  }
}
