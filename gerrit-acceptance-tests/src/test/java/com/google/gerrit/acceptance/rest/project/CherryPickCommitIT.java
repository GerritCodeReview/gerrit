// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import java.util.Iterator;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class CherryPickCommitIT extends AbstractDaemonTest {

  @Test
  public void cherryPickCommitWithoutChangeIdToNewBranch() throws Exception {
    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.message = "it goes to foo branch";
    gApi.projects().name(project.get()).branch(input.destination).create(new BranchInput());

    RevCommit revCommit = createNewCommitWithoutChangeId();
    ChangeInfo changeInfo = getCherryPickChange(revCommit, input);

    assertThat(changeInfo.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = changeInfo.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", revCommit.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);
    assertThat(changeInfo.subject).contains(input.message);

    gApi.changes().id(changeInfo.id).current().review(ReviewInput.approve());
    gApi.changes().id(changeInfo.id).current().submit();
  }

  @Test
  public void cherryPickCommitWithoutChangeIdToSameBranch() throws Exception {
    CherryPickInput input = new CherryPickInput();
    input.destination = "master";
    input.message = "it goes to master branch";

    RevCommit revCommit = createNewCommitWithoutChangeId();
    RestResponse response =
        userRestSession.post(
            "/projects/" + project.get() + "/commits/" + revCommit.getName() + "/cherrypickcommit",
            input);
    response.assertConflict();
  }

  @Test
  public void cherryPickCommitWithChangeIdToNewBranch() throws Exception {
    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.message = "it goes to foo branch";
    gApi.projects().name(project.get()).branch(input.destination).create(new BranchInput());

    RevCommit revCommit = createChange().getCommit();
    ChangeInfo changeInfo = getCherryPickChange(revCommit, input);

    assertThat(changeInfo.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = changeInfo.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", revCommit.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);
    assertThat(changeInfo.subject).contains(input.message);

    gApi.changes().id(changeInfo.id).current().review(ReviewInput.approve());
    gApi.changes().id(changeInfo.id).current().submit();
  }

  @Test
  public void cherryPickCommitWithChangeIdToSameBranch() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput input = new CherryPickInput();
    input.message = "it generates a new patch set\n\nChange-Id: " + r.getChangeId();
    input.destination = "master";

    ChangeInfo changeInfo = getCherryPickChange(r.getCommit(), input);

    assertThat(changeInfo.messages).hasSize(2);
    Iterator<ChangeMessageInfo> cherryIt = changeInfo.messages.iterator();
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 2.");

    gApi.changes().id(changeInfo.id).current().review(ReviewInput.approve());
    gApi.changes().id(changeInfo.id).current().submit();
  }

  private RevCommit createNewCommitWithoutChangeId() throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk walk = new RevWalk(repo)) {
      Ref ref = repo.exactRef("refs/heads/master");
      RevCommit tip = null;
      if (ref != null) {
        tip = walk.parseCommit(ref.getObjectId());
      }
      TestRepository<?> testSrcRepo = new TestRepository<>(repo);
      TestRepository<?>.BranchBuilder builder = testSrcRepo.branch("refs/heads/master");
      RevCommit revCommit =
          tip == null
              ? builder.commit().message("commit 1").add("a.txt", "content").create()
              : builder.commit().parent(tip).message("commit 1").add("a.txt", "content").create();
      assertThat(GitUtil.getChangeId(testSrcRepo, revCommit).isPresent()).isFalse();
      return revCommit;
    }
  }

  private ChangeInfo getCherryPickChange(RevCommit revCommit, CherryPickInput input)
      throws Exception {
    RestResponse response =
        userRestSession.post(
            "/projects/" + project.get() + "/commits/" + revCommit.getName() + "/cherrypickcommit",
            input);
    response.assertOK();
    ChangeInfo info = newGson().fromJson(response.getReader(), ChangeInfo.class);
    return gApi.changes().id(info.id).get();
  }
}
