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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.mail.MailMessage;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jgit.lib.Constants;
import org.junit.Ignore;

@Ignore
public class AbstractMailIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  static final String FILE_NAME = "gerrit-server/test.txt";

  MailMessage.Builder messageBuilderWithDefaultFields() {
    MailMessage.Builder b = MailMessage.builder();
    b.id("some id");
    b.from(user.getNameEmail());
    b.addTo(user.getNameEmail()); // Not evaluated
    b.subject("");
    b.dateReceived(Instant.now());
    return b;
  }

  String createChangeWithReview() throws Exception {
    return createChangeWithReview(admin);
  }

  String createChangeWithReview(TestAccount reviewer) throws Exception {
    // Create change
    String contents = "contents \nlorem \nipsum \nlorem";
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "first subject", FILE_NAME, contents);
    PushOneCommit.Result r = push.to("refs/for/master");
    String changeId = r.getChangeId();

    // Review it
    requestScopeOperations.setApiUser(reviewer.id());
    ReviewInput input = new ReviewInput();
    input.message = "I have two comments";
    input.comments = new HashMap<>();
    CommentInput c1 = newComment(FILE_NAME, Side.REVISION, 0, "comment on file");
    CommentInput c2 = newComment(FILE_NAME, Side.REVISION, 2, "inline comment");
    input.comments.put(c1.path, ImmutableList.of(c1, c2));
    revision(r).review(input);
    return changeId;
  }

  String createChangeWithUnchangedFileReviewed(
      TestAccount reviewer, String adminInlineComment, String userInlineComment) throws Exception {

    // Create a change with a file and merge it
    String fileContent = "this is line 1 \nthis is line 2 \nthis is line 3 \nthis is line 4";
    PushOneCommit.Result firstChangeResult = createChange("First Change", FILE_NAME, fileContent);
    firstChangeResult.assertOkStatus();
    merge(firstChangeResult);

    // Create an second empty change
    String secondChangeId =
        gApi.changes()
            .create(new ChangeInput(project.get(), Constants.MASTER, "Second Change"))
            .get()
            .id;
    RevisionApi secondChangeRevision = gApi.changes().id(secondChangeId).current();

    // Add draft
    DraftInput draftInputFromAdmin =
        createInlineDraftInLine1(adminInlineComment, Side.REVISION, null);
    secondChangeRevision.createDraft(draftInputFromAdmin);

    // Review change
    ReviewInput adminInput = new ReviewInput();
    adminInput.drafts = ReviewInput.DraftHandling.PUBLISH_ALL_REVISIONS;
    secondChangeRevision.review(adminInput);

    requestScopeOperations.setApiUser(reviewer.id());

    // Add draft
    List<CommentInfo> comments = gApi.changes().id(secondChangeId).commentsRequest().getAsList();
    assertThat(comments).hasSize(1);
    CommentInfo adminCommentInfo = comments.get(0);
    DraftInput draftInputFromUser =
        createInlineDraftInLine1(userInlineComment, Side.REVISION, adminCommentInfo.id);
    gApi.changes().id(secondChangeId).current().createDraft(draftInputFromUser);

    // Review change
    ReviewInput reviewerInput = new ReviewInput();
    reviewerInput.drafts = ReviewInput.DraftHandling.PUBLISH_ALL_REVISIONS;
    gApi.changes().id(secondChangeId).current().review(reviewerInput);

    return secondChangeId;
  }

  private DraftInput createInlineDraftInLine1(String comment, Side side, String inReplyTo) {
    DraftInput draftInput = new DraftInput();
    draftInput.line = 1;
    draftInput.message = comment;
    draftInput.path = FILE_NAME;
    draftInput.side = side;
    draftInput.unresolved = true;
    draftInput.patchSet = 1;
    draftInput.inReplyTo = inReplyTo;

    return draftInput;
  }

  protected static CommentInput newComment(String path, Side side, int line, String message) {
    CommentInput c = new CommentInput();
    c.path = path;
    c.side = side;
    c.line = line != 0 ? line : null;
    c.message = message;
    if (line != 0) {
      Comment.Range range = new Comment.Range();
      range.startLine = line;
      range.startCharacter = 1;
      range.endLine = line;
      range.endCharacter = 5;
      c.range = range;
    }
    return c;
  }

  /**
   * Create a plaintext message body with the specified comments.
   *
   * @param c1 Comment in reply to first inline comment.
   * @param f1 Comment on file one.
   * @return A string with all inline comments and the original quoted email.
   */
  static String newPlaintextBody(String changeURL, String changeMessage, String c1, String f1) {
    return (changeMessage == null ? "" : changeMessage + "\n")
        + "> Foo Bar has posted comments on this change. (  \n"
        + "> "
        + changeURL
        + " )\n"
        + "> \n"
        + "> Change subject: Test change\n"
        + "> ...............................................................\n"
        + "> \n"
        + "> \n"
        + "> Patch Set 1: Code-Review+1\n"
        + "> \n"
        + "> (3 comments)\n"
        + "> \n"
        + "> "
        + changeURL
        + "/gerrit-server/test.txt\n"
        + "> File  \n"
        + "> gerrit-server/test.txt:\n"
        + (f1 == null ? "" : f1 + "\n")
        + "> \n"
        + "> Patch Set #4:\n"
        + "> "
        + changeURL
        + "/gerrit-server/test.txt\n"
        + "> \n"
        + "> Some comment\n"
        + "> \n"
        + "> "
        + changeURL
        + "/gerrit-server/test.txt@2\n"
        + "> PS1, Line 2: throw new Exception(\"Object has unsupported: \" +\n"
        + ">               :             entry.getValue() +\n"
        + ">               :             \" must be java.util.Date\");\n"
        + "> Should entry.getKey() be included in this message?\n"
        + "> \n"
        + (c1 == null ? "" : c1 + "\n")
        + "> \n";
  }

  static String textFooterForChange(int changeNumber, String timestamp) {
    return "Gerrit-Change-Number: "
        + changeNumber
        + "\n"
        + "Gerrit-PatchSet: 1\n"
        + "Gerrit-MessageType: comment\n"
        + "Gerrit-Comment-Date: "
        + timestamp
        + "\n";
  }
}
