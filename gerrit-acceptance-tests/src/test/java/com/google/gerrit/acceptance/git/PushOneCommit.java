// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.acceptance.git.GitUtil.add;
import static com.google.gerrit.acceptance.git.GitUtil.amendCommit;
import static com.google.gerrit.acceptance.git.GitUtil.createCommit;
import static com.google.gerrit.acceptance.git.GitUtil.pushHead;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class PushOneCommit {
  public final static String SUBJECT = "test commit";

  private final static String FILE_NAME = "a.txt";
  private final static String FILE_CONTENT = "some content";

  private final ReviewDb db;
  private final PersonIdent i;

  private final String subject;
  private final String fileName;
  private final String content;
  private String changeId;

  public PushOneCommit(ReviewDb db, PersonIdent i) {
    this(db, i, SUBJECT, FILE_NAME, FILE_CONTENT);
  }

  public PushOneCommit(ReviewDb db, PersonIdent i, String subject,
      String fileName, String content) {
    this(db, i, subject, fileName, content, null);
  }

  public PushOneCommit(ReviewDb db, PersonIdent i, String subject,
      String fileName, String content, String changeId) {
    this.db = db;
    this.i = i;
    this.subject = subject;
    this.fileName = fileName;
    this.content = content;
    this.changeId = changeId;
  }

  public Result to(Git git, String ref)
      throws GitAPIException, IOException {
    add(git, fileName, content);
    if (changeId != null) {
      amendCommit(git, i, subject, changeId);
    } else {
      changeId = createCommit(git, i, subject);
    }
    return new Result(db, ref, pushHead(git, ref), changeId, subject);
  }

  public static class Result {
    private final ReviewDb db;
    private final String ref;
    private final PushResult result;
    private final String changeId;
    private final String subject;

    private Result(ReviewDb db, String ref, PushResult result, String changeId,
        String subject) {
      this.db = db;
      this.ref = ref;
      this.result = result;
      this.changeId = changeId;
      this.subject = subject;
    }

    public PatchSet.Id getPatchSetId() throws OrmException {
      return Iterables.getOnlyElement(
          db.changes().byKey(new Change.Key(changeId))).currentPatchSetId();
    }

    public String getChangeId() {
      return changeId;
    }

    public void assertChange(Change.Status expectedStatus,
        String expectedTopic, TestAccount... expectedReviewers)
        throws OrmException {
      Change c =
          Iterables.getOnlyElement(db.changes().byKey(new Change.Key(changeId)).toList());
      assertEquals(subject, c.getSubject());
      assertEquals(expectedStatus, c.getStatus());
      assertEquals(expectedTopic, Strings.emptyToNull(c.getTopic()));
      assertReviewers(c, expectedReviewers);
    }

    private void assertReviewers(Change c, TestAccount... expectedReviewers)
        throws OrmException {
      Set<Account.Id> expectedReviewerIds =
          Sets.newHashSet(Lists.transform(Arrays.asList(expectedReviewers),
              new Function<TestAccount, Account.Id>() {
                @Override
                public Account.Id apply(TestAccount a) {
                  return a.id;
                }
              }));

      for (PatchSetApproval psa : db.patchSetApprovals().byPatchSet(
          c.currentPatchSetId())) {
        assertTrue("unexpected reviewer " + psa.getAccountId(),
            expectedReviewerIds.remove(psa.getAccountId()));
      }
      assertTrue("missing reviewers: " + expectedReviewerIds,
          expectedReviewerIds.isEmpty());
    }

    public void assertOkStatus() {
      assertStatus(Status.OK, null);
    }

    public void assertErrorStatus(String expectedMessage) {
      assertStatus(Status.REJECTED_OTHER_REASON, expectedMessage);
    }

    private void assertStatus(Status expectedStatus, String expectedMessage) {
      RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
      assertEquals(message(refUpdate),
          expectedStatus, refUpdate.getStatus());
      assertEquals(expectedMessage, refUpdate.getMessage());
    }

    public void assertMessage(String expectedMessage) {
      RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
      assertTrue(message(refUpdate), message(refUpdate).toLowerCase().contains(
          expectedMessage.toLowerCase()));
    }

    private String message(RemoteRefUpdate refUpdate) {
      StringBuilder b = new StringBuilder();
      if (refUpdate.getMessage() != null) {
        b.append(refUpdate.getMessage());
        b.append("\n");
      }
      b.append(result.getMessages());
      return b.toString();
    }
  }
}
