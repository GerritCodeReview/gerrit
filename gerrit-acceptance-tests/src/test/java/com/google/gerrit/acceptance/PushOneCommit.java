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

package com.google.gerrit.acceptance;

import static com.google.gerrit.acceptance.GitUtil.add;
import static com.google.gerrit.acceptance.GitUtil.amendCommit;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.GitUtil.Commit;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidTagNameException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class PushOneCommit {
  public static final String SUBJECT = "test commit";
  static final String FILE_NAME = "a.txt";
  private static final String FILE_CONTENT = "some content";

  public interface Factory {
    PushOneCommit create(
        ReviewDb db,
        PersonIdent i);

    PushOneCommit create(
        ReviewDb db,
        PersonIdent i,
        @Assisted("subject") String subject,
        @Assisted("fileName") String fileName,
        @Assisted("content") String content);

    PushOneCommit create(
        ReviewDb db,
        PersonIdent i,
        @Assisted("subject") String subject,
        @Assisted("fileName") String fileName,
        @Assisted("content") String content,
        @Assisted("changeId") String changeId);
  }

  private final ChangeNotes.Factory notesFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ReviewDb db;
  private final PersonIdent i;

  private final String subject;
  private final String fileName;
  private final String content;
  private String changeId;
  private String tagName;

  @AssistedInject
  PushOneCommit(ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      @Assisted ReviewDb db,
      @Assisted PersonIdent i) {
    this(notesFactory, approvalsUtil, db, i, SUBJECT, FILE_NAME, FILE_CONTENT);
  }

  @AssistedInject
  PushOneCommit(ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      @Assisted ReviewDb db,
      @Assisted PersonIdent i,
      @Assisted("subject") String subject,
      @Assisted("fileName") String fileName,
      @Assisted("content") String content) {
    this(notesFactory, approvalsUtil, db, i, subject, fileName, content, null);
  }

  @AssistedInject
  PushOneCommit(ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      @Assisted ReviewDb db,
      @Assisted PersonIdent i,
      @Assisted("subject") String subject,
      @Assisted("fileName") String fileName,
      @Assisted("content") String content,
      @Nullable @Assisted("changeId") String changeId) {
    this.db = db;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.i = i;
    this.subject = subject;
    this.fileName = fileName;
    this.content = content;
    this.changeId = changeId;
  }

  public Result to(Git git, String ref)
      throws GitAPIException, IOException {
    add(git, fileName, content);
    return execute(git, ref);
  }

  public Result rm(Git git, String ref)
      throws GitAPIException, IOException {
    GitUtil.rm(git, fileName);
    return execute(git, ref);
  }

  private Result execute(Git git, String ref) throws GitAPIException,
      IOException, ConcurrentRefUpdateException, InvalidTagNameException,
      NoHeadException {
    Commit c;
    if (changeId != null) {
      c = amendCommit(git, i, subject, changeId);
    } else {
      c = createCommit(git, i, subject);
      changeId = c.getChangeId();
    }
    if (tagName != null) {
      git.tag().setName(tagName).setAnnotated(false).call();
    }
    return new Result(ref, pushHead(git, ref, tagName != null), c, subject);
  }

  public void setTag(final String tagName) {
    this.tagName = tagName;
  }

  public class Result {
    private final String ref;
    private final PushResult result;
    private final Commit commit;
    private final String subject;

    private Result(String ref, PushResult result, Commit commit,
        String subject) {
      this.ref = ref;
      this.result = result;
      this.commit = commit;
      this.subject = subject;
    }

    public PatchSet.Id getPatchSetId() throws OrmException {
      return Iterables.getOnlyElement(
          db.changes().byKey(new Change.Key(commit.getChangeId()))).currentPatchSetId();
    }

    public String getChangeId() {
      return commit.getChangeId();
    }

    public ObjectId getCommitId() {
      return commit.getCommit().getId();
    }

    public RevCommit getCommit() {
      return commit.getCommit();
    }

    public void assertChange(Change.Status expectedStatus,
        String expectedTopic, TestAccount... expectedReviewers)
        throws OrmException {
      Change c =
          Iterables.getOnlyElement(db.changes().byKey(new Change.Key(commit.getChangeId())).toList());
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

      for (Account.Id accountId
          : approvalsUtil.getReviewers(db, notesFactory.create(c)).values()) {
        assertTrue("unexpected reviewer " + accountId,
            expectedReviewerIds.remove(accountId));
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
