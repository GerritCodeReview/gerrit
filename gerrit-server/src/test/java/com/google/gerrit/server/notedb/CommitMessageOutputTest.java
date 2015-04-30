// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.notedb.ReviewerState.CC;
import static com.google.gerrit.server.notedb.ReviewerState.REVIEWER;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.testutil.TestChanges;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

import java.util.Date;
import java.util.TimeZone;

public class CommitMessageOutputTest extends AbstractChangeNotesTest {
  @Test
  public void approvalsCommitFormatSimple() throws Exception {
    Change c = TestChanges.newChange(project, changeOwner.getAccountId(), 1);
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) 1);
    update.putApproval("Code-Review", (short) -1);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();
    assertThat(update.getRefName()).isEqualTo("refs/changes/01/1/meta");

    RevCommit commit = parseCommit(update.getRevision());
    assertBodyEquals("Update patch set 1\n"
        + "\n"
        + "Patch-set: 1\n"
        + "Reviewer: Change Owner <1@gerrit>\n"
        + "CC: Other Account <2@gerrit>\n"
        + "Label: Code-Review=-1\n"
        + "Label: Verified=+1\n",
        commit);

    PersonIdent author = commit.getAuthorIdent();
    assertThat(author.getName()).isEqualTo("Change Owner");
    assertThat(author.getEmailAddress()).isEqualTo("1@gerrit");
    assertThat(author.getWhen())
        .isEqualTo(new Date(c.getCreatedOn().getTime() + 1000));
    assertThat(author.getTimeZone())
        .isEqualTo(TimeZone.getTimeZone("GMT-7:00"));

    PersonIdent committer = commit.getCommitterIdent();
    assertThat(committer.getName()).isEqualTo("Gerrit Server");
    assertThat(committer.getEmailAddress()).isEqualTo("noreply@gerrit.com");
    assertThat(committer.getWhen()).isEqualTo(author.getWhen());
    assertThat(committer.getTimeZone()).isEqualTo(author.getTimeZone());
  }

  @Test
  public void changeMessageCommitFormatSimple() throws Exception {
    Change c = TestChanges.newChange(project, changeOwner.getAccountId(), 1);
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Just a little code change.\n"
        + "How about a new line");
    update.commit();
    assertThat(update.getRefName()).isEqualTo("refs/changes/01/1/meta");

    assertBodyEquals("Update patch set 1\n"
        + "\n"
        + "Just a little code change.\n"
        + "How about a new line\n"
        + "\n"
        + "Patch-set: 1\n",
        update.getRevision());
  }

  @Test
  public void approvalTombstoneCommitFormat() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.removeApproval("Code-Review");
    update.commit();

    assertBodyEquals("Update patch set 1\n"
        + "\n"
        + "Patch-set: 1\n"
        + "Label: -Code-Review\n",
        update.getRevision());
  }

  @Test
  public void submitCommitFormat() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");

    update.submit(ImmutableList.of(
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Code-Review", "NEED", null)),
        submitRecord("NOT_READY", null,
          submitLabel("Verified", "OK", changeOwner.getAccountId()),
          submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();

    RevCommit commit = parseCommit(update.getRevision());
    assertBodyEquals("Submit patch set 1\n"
        + "\n"
        + "Patch-set: 1\n"
        + "Status: submitted\n"
        + "Submitted-with: NOT_READY\n"
        + "Submitted-with: OK: Verified: Change Owner <1@gerrit>\n"
        + "Submitted-with: NEED: Code-Review\n"
        + "Submitted-with: NOT_READY\n"
        + "Submitted-with: OK: Verified: Change Owner <1@gerrit>\n"
        + "Submitted-with: NEED: Alternative-Code-Review\n",
        commit);

    PersonIdent author = commit.getAuthorIdent();
    assertThat(author.getName()).isEqualTo("Change Owner");
    assertThat(author.getEmailAddress()).isEqualTo("1@gerrit");
    assertThat(author.getWhen())
        .isEqualTo(new Date(c.getCreatedOn().getTime() + 1000));
    assertThat(author.getTimeZone())
        .isEqualTo(TimeZone.getTimeZone("GMT-7:00"));

    PersonIdent committer = commit.getCommitterIdent();
    assertThat(committer.getName()).isEqualTo("Gerrit Server");
    assertThat(committer.getEmailAddress()).isEqualTo("noreply@gerrit.com");
    assertThat(committer.getWhen()).isEqualTo(author.getWhen());
    assertThat(committer.getTimeZone()).isEqualTo(author.getTimeZone());
  }

  @Test
  public void anonymousUser() throws Exception {
    Account anon = new Account(new Account.Id(3), TimeUtil.nowTs());
    accountCache.put(anon);
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, userFactory.create(anon.getId()));
    update.setChangeMessage("Comment on the change.");
    update.commit();

    RevCommit commit = parseCommit(update.getRevision());
    assertBodyEquals("Update patch set 1\n"
        + "\n"
        + "Comment on the change.\n"
        + "\n"
        + "Patch-set: 1\n",
        commit);

    PersonIdent author = commit.getAuthorIdent();
    assertThat(author.getName()).isEqualTo("Anonymous Coward (3)");
    assertThat(author.getEmailAddress()).isEqualTo("3@gerrit");
  }

  @Test
  public void submitWithErrorMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubject("Submit patch set 1");

    update.submit(ImmutableList.of(
        submitRecord("RULE_ERROR", "Problem with patch set:\n1")));
    update.commit();

    assertBodyEquals("Submit patch set 1\n"
        + "\n"
        + "Patch-set: 1\n"
        + "Status: submitted\n"
        + "Submitted-with: RULE_ERROR Problem with patch set: 1\n",
        update.getRevision());
  }

  @Test
  public void noChangeMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.commit();

    assertBodyEquals("Update patch set 1\n"
        + "\n"
        + "Patch-set: 1\n"
        + "Reviewer: Change Owner <1@gerrit>\n",
        update.getRevision());
  }

  @Test
  public void changeMessageWithTrailingDoubleNewline() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing trailing double newline\n"
        + "\n");
    update.commit();

    assertBodyEquals("Update patch set 1\n"
        + "\n"
        + "Testing trailing double newline\n"
        + "\n"
        + "\n"
        + "\n"
        + "Patch-set: 1\n",
        update.getRevision());
  }

  @Test
  public void changeMessageWithMultipleParagraphs() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing paragraph 1\n"
        + "\n"
        + "Testing paragraph 2\n"
        + "\n"
        + "Testing paragraph 3");
    update.commit();

    assertBodyEquals("Update patch set 1\n"
        + "\n"
        + "Testing paragraph 1\n"
        + "\n"
        + "Testing paragraph 2\n"
        + "\n"
        + "Testing paragraph 3\n"
        + "\n"
        + "Patch-set: 1\n",
        update.getRevision());
  }

  private RevCommit parseCommit(ObjectId id) throws Exception {
    if (id instanceof RevCommit) {
      return (RevCommit) id;
    }
    try (RevWalk walk = new RevWalk(repo)) {
      RevCommit commit = walk.parseCommit(id);
      walk.parseBody(commit);
      return commit;
    }
  }

  private void assertBodyEquals(String expected, ObjectId commitId)
      throws Exception {
    RevCommit commit = parseCommit(commitId);
    assertThat(commit.getFullMessage()).isEqualTo(expected);
  }
}
