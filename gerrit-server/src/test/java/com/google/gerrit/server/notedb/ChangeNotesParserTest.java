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
import static org.junit.Assert.fail;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChangeNotesParserTest extends AbstractChangeNotesTest {
  private TestRepository<InMemoryRepository> testRepo;
  private ChangeNotesRevWalk walk;

  @Before
  public void setUpTestRepo() throws Exception {
    testRepo = new TestRepository<>(repo);
    walk = ChangeNotesCommit.newRevWalk(repo);
  }

  @After
  public void tearDownTestRepo() throws Exception {
    walk.close();
  }

  @Test
  public void parseAuthor() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n");
    assertParseFails(
        writeCommit(
            "Update change\n\nPatch-set: 1\n",
            new PersonIdent(
                "Change Owner",
                "owner@example.com",
                serverIdent.getWhen(),
                serverIdent.getTimeZone())));
    assertParseFails(
        writeCommit(
            "Update change\n\nPatch-set: 1\n",
            new PersonIdent(
                "Change Owner", "x@gerrit", serverIdent.getWhen(), serverIdent.getTimeZone())));
    assertParseFails(
        writeCommit(
            "Update change\n\nPatch-set: 1\n",
            new PersonIdent(
                "Change\n\u1234<Owner>",
                "\n\nx<@>\u0002gerrit",
                serverIdent.getWhen(),
                serverIdent.getTimeZone())));
  }

  @Test
  public void parseStatus() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Status: NEW\n"
            + "Subject: This is a test change\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Status: new\n"
            + "Subject: This is a test change\n");
    assertParseFails("Update change\n\nPatch-set: 1\nStatus: OOPS\n");
    assertParseFails("Update change\n\nPatch-set: 1\nStatus: NEW\nStatus: NEW\n");
  }

  @Test
  public void parsePatchSetId() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n");
    assertParseFails("Update change\n\n");
    assertParseFails("Update change\n\nPatch-set: 1\nPatch-set: 1\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n");
    assertParseFails("Update change\n\nPatch-set: x\n");
  }

  @Test
  public void parseApproval() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Label: Label1=+1\n"
            + "Label: Label2=1\n"
            + "Label: Label3=0\n"
            + "Label: Label4=-1\n"
            + "Subject: This is a test change\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Label: -Label1\n"
            + "Label: -Label1 Other Account <2@gerrit>\n"
            + "Subject: This is a test change\n");
    assertParseFails("Update change\n\nPatch-set: 1\nLabel: Label1=X\n");
    assertParseFails("Update change\n\nPatch-set: 1\nLabel: Label1 = 1\n");
    assertParseFails("Update change\n\nPatch-set: 1\nLabel: X+Y\n");
    assertParseFails("Update change\n\nPatch-set: 1\nLabel: Label1 Other Account <2@gerrit>\n");
    assertParseFails("Update change\n\nPatch-set: 1\nLabel: -Label!1\n");
    assertParseFails("Update change\n\nPatch-set: 1\nLabel: -Label!1 Other Account <2@gerrit>\n");
  }

  @Test
  public void parseSubmitRecords() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n"
            + "Submitted-with: NOT_READY\n"
            + "Submitted-with: OK: Verified: Change Owner <1@gerrit>\n"
            + "Submitted-with: NEED: Code-Review\n"
            + "Submitted-with: NOT_READY\n"
            + "Submitted-with: OK: Verified: Change Owner <1@gerrit>\n"
            + "Submitted-with: NEED: Alternative-Code-Review\n");
    assertParseFails("Update change\n\nPatch-set: 1\nSubmitted-with: OOPS\n");
    assertParseFails("Update change\n\nPatch-set: 1\nSubmitted-with: NEED: X+Y\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Submitted-with: OK: X+Y: Change Owner <1@gerrit>\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Submitted-with: OK: Code-Review: 1@gerrit\n");
  }

  @Test
  public void parseSubmissionId() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n"
            + "Submission-id: 1-1453387607626-96fabc25");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Submission-id: 1-1453387607626-96fabc25\n"
            + "Submission-id: 1-1453387901516-5d1e2450");
  }

  @Test
  public void parseReviewer() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Reviewer: Change Owner <1@gerrit>\n"
            + "CC: Other Account <2@gerrit>\n"
            + "Subject: This is a test change\n");
    assertParseFails("Update change\n\nPatch-set: 1\nReviewer: 1@gerrit\n");
  }

  @Test
  public void parseTopic() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Topic: Some Topic\n"
            + "Subject: This is a test change\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Topic:\n"
            + "Subject: This is a test change\n");
    assertParseFails("Update change\n\nPatch-set: 1\nTopic: Some Topic\nTopic: Other Topic");
  }

  @Test
  public void parseBranch() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Branch: refs/heads/stable");
  }

  @Test
  public void parseChangeId() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Patch-set: 1\n"
            + "Subject: This is a test change\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Change-id: I159532ef4844d7c18f7f3fd37a0b275590d41b1b");
  }

  @Test
  public void parseSubject() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Some subject of a change\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Subject: Some subject of a change\n"
            + "Subject: Some other subject\n");
  }

  @Test
  public void parseCommit() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 2\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Some subject of a change\n"
            + "Commit: abcd1234abcd1234abcd1234abcd1234abcd1234");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 2\n"
            + "Branch: refs/heads/master\n"
            + "Subject: Some subject of a change\n"
            + "Commit: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
            + "Commit: deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    assertParseFails(
        "Update patch set 1\n"
            + "Uploaded patch set 1.\n"
            + "Patch-set: 2\n"
            + "Branch: refs/heads/master\n"
            + "Subject: Some subject of a change\n"
            + "Commit: beef");
  }

  @Test
  public void parsePatchSetState() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 1 (PUBLISHED)\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Some subject of a change\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 1 (DRAFT)\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Some subject of a change\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 1 (DELETED)\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Some subject of a change\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1 (NOT A STATUS)\n"
            + "Branch: refs/heads/master\n"
            + "Subject: Some subject of a change\n");
  }

  @Test
  public void parsePatchSetGroups() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 2\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Commit: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
            + "Subject: Change subject\n"
            + "Groups: a,b,c\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 2\n"
            + "Branch: refs/heads/master\n"
            + "Commit: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
            + "Subject: Change subject\n"
            + "Groups: a,b,c\n"
            + "Groups: d,e,f\n");
  }

  @Test
  public void parseServerIdent() throws Exception {
    String msg =
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Change subject\n";
    assertParseSucceeds(msg);
    assertParseSucceeds(writeCommit(msg, serverIdent));

    msg =
        "Update change\n"
            + "\n"
            + "With a message."
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Change subject\n";
    assertParseSucceeds(msg);
    assertParseSucceeds(writeCommit(msg, serverIdent));

    msg =
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Change subject\n"
            + "Label: Label1=+1\n";
    assertParseSucceeds(msg);
    assertParseFails(writeCommit(msg, serverIdent));
  }

  @Test
  public void parseTag() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Change subject\n"
            + "Tag:\n");
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Change subject\n"
            + "Tag: jenkins\n");
    assertParseFails(
        "Update change\n"
            + "\n"
            + "Patch-set: 1\n"
            + "Branch: refs/heads/master\n"
            + "Change-id: I577fb248e474018276351785930358ec0450e9f7\n"
            + "Subject: Change subject\n"
            + "Tag: ci\n"
            + "Tag: jenkins\n");
  }

  @Test
  public void parseWorkInProgress() throws Exception {
    // Change created in WIP remains in WIP.
    RevCommit commit = writeCommit("Update WIP change\n" + "\n" + "Patch-set: 1\n", true);
    ChangeNotesState state = newParser(commit).parseAll();
    assertThat(state.hasReviewStarted()).isFalse();

    // Moving change out of WIP starts review.
    commit =
        writeCommit("New ready change\n" + "\n" + "Patch-set: 1\n" + "Work-in-progress: false\n");
    state = newParser(commit).parseAll();
    assertThat(state.hasReviewStarted()).isTrue();

    // Change created not in WIP has always been in review started state.
    state = assertParseSucceeds("New change that doesn't declare WIP\n" + "\n" + "Patch-set: 1\n");
    assertThat(state.hasReviewStarted()).isTrue();
  }

  @Test
  public void caseInsensitiveFooters() throws Exception {
    assertParseSucceeds(
        "Update change\n"
            + "\n"
            + "BRaNch: refs/heads/master\n"
            + "Change-ID: I577fb248e474018276351785930358ec0450e9f7\n"
            + "patcH-set: 1\n"
            + "subject: This is a test change\n");
  }

  @Test
  public void currentPatchSet() throws Exception {
    assertParseSucceeds("Update change\n\nPatch-set: 1\nCurrent: true");
    assertParseSucceeds("Update change\n\nPatch-set: 1\nCurrent: tRUe");
    assertParseFails("Update change\n\nPatch-set: 1\nCurrent: false");
    assertParseFails("Update change\n\nPatch-set: 1\nCurrent: blah");
  }

  private RevCommit writeCommit(String body) throws Exception {
    ChangeNoteUtil noteUtil = injector.getInstance(ChangeNoteUtil.class);
    return writeCommit(
        body,
        noteUtil.newIdent(
            changeOwner.getAccount(), TimeUtil.nowTs(), serverIdent, "Anonymous Coward"),
        false);
  }

  private RevCommit writeCommit(String body, PersonIdent author) throws Exception {
    return writeCommit(body, author, false);
  }

  private RevCommit writeCommit(String body, boolean initWorkInProgress) throws Exception {
    ChangeNoteUtil noteUtil = injector.getInstance(ChangeNoteUtil.class);
    return writeCommit(
        body,
        noteUtil.newIdent(
            changeOwner.getAccount(), TimeUtil.nowTs(), serverIdent, "Anonymous Coward"),
        initWorkInProgress);
  }

  private RevCommit writeCommit(String body, PersonIdent author, boolean initWorkInProgress)
      throws Exception {
    Change change = newChange(initWorkInProgress);
    ChangeNotes notes = newNotes(change).load();
    try (ObjectInserter ins = testRepo.getRepository().newObjectInserter()) {
      CommitBuilder cb = new CommitBuilder();
      cb.setParentId(notes.getRevision());
      cb.setAuthor(author);
      cb.setCommitter(new PersonIdent(serverIdent, author.getWhen()));
      cb.setTreeId(testRepo.tree());
      cb.setMessage(body);
      ObjectId id = ins.insert(cb);
      ins.flush();
      RevCommit commit = walk.parseCommit(id);
      walk.parseBody(commit);
      return commit;
    }
  }

  private ChangeNotesState assertParseSucceeds(String body) throws Exception {
    return assertParseSucceeds(writeCommit(body));
  }

  private ChangeNotesState assertParseSucceeds(RevCommit commit) throws Exception {
    return newParser(commit).parseAll();
  }

  private void assertParseFails(String body) throws Exception {
    assertParseFails(writeCommit(body));
  }

  private void assertParseFails(RevCommit commit) throws Exception {
    try {
      newParser(commit).parseAll();
      fail("Expected parse to fail:\n" + commit.getFullMessage());
    } catch (ConfigInvalidException e) {
      // Expected
    }
  }

  private ChangeNotesParser newParser(ObjectId tip) throws Exception {
    walk.reset();
    ChangeNoteUtil noteUtil = injector.getInstance(ChangeNoteUtil.class);
    return new ChangeNotesParser(newChange().getId(), tip, walk, noteUtil, args.metrics);
  }
}
