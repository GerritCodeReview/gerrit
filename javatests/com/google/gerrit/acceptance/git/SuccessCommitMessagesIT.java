// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Change;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class SuccessCommitMessagesIT extends AbstractPushForReview {

  @Test
  public void sequentialCommitMessages() throws Exception {
    String url = canonicalWebUrl.get() + "c/" + project.get() + "/+/";
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");

    PushOneCommit.Result r1 = pushTo("refs/for/master");
    Change.Id id1 = r1.getChange().getId();
    r1.assertOkStatus();
    r1.assertChange(Change.Status.NEW, null);
    r1.assertMessage(
        url + id1 + " " + r1.getCommit().getShortMessage() + NEW_CHANGE_INDICATOR + "\n");

    PushOneCommit.Result r2 = pushTo("refs/for/master");
    Change.Id id2 = r2.getChange().getId();
    r2.assertOkStatus();
    r2.assertChange(Change.Status.NEW, null);
    r2.assertMessage(
        url + id2 + " " + r2.getCommit().getShortMessage() + NEW_CHANGE_INDICATOR + "\n");

    testRepo.reset(initialHead);

    // rearrange the commit so that change no. 2 is the parent of change no. 1
    String r1Message = "Position 2";
    String r2Message = "Position 1";
    testRepo
        .branch("HEAD")
        .commit()
        .message(r2Message)
        .insertChangeId(r2.getChangeId().substring(1))
        .create();
    testRepo
        .branch("HEAD")
        .commit()
        .message(r1Message)
        .insertChangeId(r1.getChangeId().substring(1))
        .create();

    PushOneCommit.Result r3 =
        pushFactory
            .create(admin.getIdent(), testRepo, "another commit", "b.txt", "bbb")
            .to("refs/for/master");
    Change.Id id3 = r3.getChange().getId();
    r3.assertOkStatus();
    r3.assertChange(Change.Status.NEW, null);
    // should display commit r2, r1, r3 in that order.
    r3.assertMessage(
        "success\n"
            + "\n"
            + "  "
            + url
            + id2
            + " "
            + r2Message
            + "\n"
            + "  "
            + url
            + id1
            + " "
            + r1Message
            + "\n"
            + "  "
            + url
            + id3
            + " another commit"
            + NEW_CHANGE_INDICATOR
            + "\n");
  }
}
