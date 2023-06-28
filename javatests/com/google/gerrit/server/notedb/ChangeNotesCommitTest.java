// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.entities.Change;
import com.google.gerrit.server.util.time.TimeUtil;
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

public class ChangeNotesCommitTest extends AbstractChangeNotesTest {
  private TestRepository<InMemoryRepository> testRepo;
  private ChangeNotesCommit.ChangeNotesRevWalk walk;

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
  public void attentionSetCommitOnlyWhenNoChangeMessageIsPresentAndCorrectFooter()
      throws Exception {
    RevCommit commit =
        writeCommit(
            "Update patch set 1\n"
                + "\n"
                + "Patch-set: 1\n"
                + "Attention: {\"person_ident\":\""
                + FQ_USER_IDENT
                + "\\u003e\",\"operation\":\"ADD\",\"reason\":\"Added by Administrator using the hovercard menu\"}");

    newParser(commit).parseAll();
    assertThat(((ChangeNotesCommit) commit).isAttentionSetCommitOnly(false)).isEqualTo(true);
  }

  @Test
  public void noAttentionSetCommitOnlyWhenNoChangeMessageIsPresentAndFooterNotOnlyAS()
      throws Exception {
    RevCommit commit =
        writeCommit(
            "Update patch set 1\n"
                + "\n"
                + "Patch-set: 1\n"
                + "Subject: Change subject\n"
                + "Attention: {\"person_ident\":\""
                + FQ_USER_IDENT
                + "\\u003e\",\"operation\":\"ADD\",\"reason\":\"Added by Administrator using the hovercard menu\"}");

    newParser(commit).parseAll();
    assertThat(((ChangeNotesCommit) commit).isAttentionSetCommitOnly(false)).isEqualTo(false);
  }

  @Test
  public void noAttentionSetCommitOnlyWhenNoChangeMessageIsPresentAndGenericFooter()
      throws Exception {
    RevCommit commit = writeCommit("Update patch set 1\n" + "\n" + "Patch-set: 1\n");

    newParser(commit).parseAll();
    assertThat(((ChangeNotesCommit) commit).isAttentionSetCommitOnly(false)).isEqualTo(false);
  }

  @Test
  public void noAttentionSetCommitOnlyWhenChangeMessageIsPresent() throws Exception {
    RevCommit commit =
        writeCommit(
            "Update patch set 1\n"
                + "\n"
                + "Patch-set: 1\n"
                + "Attention: {\"person_ident\":\""
                + FQ_USER_IDENT
                + "\\u003e\",\"operation\":\"ADD\",\"reason\":\"Added by Administrator using the hovercard menu\"}");

    newParser(commit).parseAll();
    assertThat(((ChangeNotesCommit) commit).isAttentionSetCommitOnly(true)).isEqualTo(false);
  }

  private ChangeNotesParser newParser(ObjectId tip) throws Exception {
    walk.reset();
    ChangeNoteJson changeNoteJson = injector.getInstance(ChangeNoteJson.class);
    return new ChangeNotesParser(
        newChange().getId(),
        tip,
        walk,
        changeNoteJson,
        args.metrics,
        new NoteDbUtil(serverId, externalIdCache));
  }

  private RevCommit writeCommit(String body) throws Exception {
    Change change = newChange(true);
    ChangeNotes notes = newNotes(change).load();
    ChangeNoteUtil noteUtil = injector.getInstance(ChangeNoteUtil.class);
    PersonIdent author =
        noteUtil.newAccountIdIdent(changeOwner.getAccount().id(), TimeUtil.now(), serverIdent);
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
}
