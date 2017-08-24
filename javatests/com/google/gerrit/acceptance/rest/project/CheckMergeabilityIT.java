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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Strings;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.reviewdb.client.Branch;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Before;
import org.junit.Test;

public class CheckMergeabilityIT extends AbstractDaemonTest {

  private Branch.NameKey branch;

  @Before
  public void setUp() throws Exception {
    branch = new Branch.NameKey(project, "test");
    gApi.projects()
        .name(branch.getParentKey().get())
        .branch(branch.get())
        .create(new BranchInput());
  }

  @Test
  public void checkMergeableCommit() throws Exception {
    RevCommit initialHead = getRemoteHead();
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change in a")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo.reset(initialHead);
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change in b")
        .add("b.txt", "b contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/test"))
        .call();

    assertMergeable("master", "test", "recursive");
  }

  @Test
  public void checkUnMergeableCommit() throws Exception {
    RevCommit initialHead = getRemoteHead();
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change in a")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo.reset(initialHead);
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change in a too")
        .add("a.txt", "a contents too")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/test"))
        .call();

    assertUnMergeable("master", "test", "recursive", "a.txt");
  }

  @Test
  public void checkOursMergeStrategy() throws Exception {
    RevCommit initialHead = getRemoteHead();
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change in a")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo.reset(initialHead);
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change in a too")
        .add("a.txt", "a contents too")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/test"))
        .call();

    assertMergeable("master", "test", "ours");
  }

  @Test
  public void checkAlreadyMergedCommit() throws Exception {
    ObjectId c0 =
        testRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("first commit")
            .add("a.txt", "a contents ")
            .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("second commit")
        .add("b.txt", "b contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    assertCommitMerged("master", c0.getName(), "");
  }

  @Test
  public void checkContentMergedCommit() throws Exception {
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("first commit")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    // create a change, and cherrypick into master
    PushOneCommit.Result cId = createChange();
    RevCommit commitId = cId.getCommit();
    CherryPickInput cpi = new CherryPickInput();
    cpi.destination = "master";
    cpi.message = "cherry pick the commit";
    ChangeApi orig = gApi.changes().id(cId.getChangeId());
    ChangeApi cherry = orig.current().cherryPick(cpi);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    ObjectId remoteId = getRemoteHead();
    assertThat(remoteId).isNotEqualTo(commitId);
    assertContentMerged("master", commitId.getName(), "recursive");
  }

  @Test
  public void checkInvalidSource() throws Exception {
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("first commit")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    assertBadRequest("master", "fdsafsdf", "recursive", "Cannot resolve 'fdsafsdf' to a commit");
  }

  @Test
  public void checkInvalidStrategy() throws Exception {
    RevCommit initialHead = getRemoteHead();
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("first commit")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo.reset(initialHead);
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change in a too")
        .add("a.txt", "a contents too")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/test"))
        .call();

    assertBadRequest("master", "test", "octopus", "invalid merge strategy: octopus");
  }

  private void assertMergeable(String targetBranch, String source, String strategy)
      throws Exception {
    MergeableInfo mergeableInfo = getMergeableInfo(targetBranch, source, strategy);
    assertThat(mergeableInfo.mergeable).isTrue();
  }

  private void assertUnMergeable(
      String targetBranch, String source, String strategy, String... conflicts) throws Exception {
    MergeableInfo mergeableInfo = getMergeableInfo(targetBranch, source, strategy);
    assertThat(mergeableInfo.mergeable).isFalse();
    assertThat(mergeableInfo.conflicts).containsExactly((Object[]) conflicts);
  }

  private void assertCommitMerged(String targetBranch, String source, String strategy)
      throws Exception {
    MergeableInfo mergeableInfo = getMergeableInfo(targetBranch, source, strategy);
    assertThat(mergeableInfo.mergeable).isTrue();
    assertThat(mergeableInfo.commitMerged).isTrue();
  }

  private void assertContentMerged(String targetBranch, String source, String strategy)
      throws Exception {
    MergeableInfo mergeableInfo = getMergeableInfo(targetBranch, source, strategy);
    assertThat(mergeableInfo.mergeable).isTrue();
    assertThat(mergeableInfo.contentMerged).isTrue();
  }

  private void assertBadRequest(String targetBranch, String source, String strategy, String errMsg)
      throws Exception {
    String url = "/projects/" + project.get() + "/branches/" + targetBranch;
    url += "/mergeable?source=" + source;
    if (!Strings.isNullOrEmpty(strategy)) {
      url += "&strategy=" + strategy;
    }

    RestResponse r = userRestSession.get(url);
    r.assertBadRequest();
    assertThat(r.getEntityContent()).isEqualTo(errMsg);
  }

  private MergeableInfo getMergeableInfo(String targetBranch, String source, String strategy)
      throws Exception {
    String url = "/projects/" + project.get() + "/branches/" + targetBranch;
    url += "/mergeable?source=" + source;
    if (!Strings.isNullOrEmpty(strategy)) {
      url += "&strategy=" + strategy;
    }

    RestResponse r = userRestSession.get(url);
    r.assertOK();
    MergeableInfo result = newGson().fromJson(r.getReader(), MergeableInfo.class);
    r.consume();
    return result;
  }
}
