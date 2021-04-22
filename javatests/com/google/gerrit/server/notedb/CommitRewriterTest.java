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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.notedb.CommitRewriter.BackfillResult;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CommitRewriter} */
public class CommitRewriterTest extends AbstractChangeNotesTest {

  private @Inject CommitRewriter rewriter;
  @Inject private ChangeNoteUtil changeNoteUtil;

  @Before
  public void setUp() throws Exception {}

  @Test
  public void validHistoryNoOp() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("verification from jenkins");
    update.setTag(tag);
    update.commit();

    ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
    updateWithSubject.setSubjectForCommit("Update with subject");
    updateWithSubject.commit();

    ChangeNotes notesBeforeRewrite = newNotes(c);
    Ref metaRefBefore = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, false, true);
    ChangeNotes notesAfterRewrite = newNotes(c);
    Ref metaRefAfter = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    assertThat(notesBeforeRewrite.getMetaId()).isEqualTo(notesAfterRewrite.getMetaId());
    assertThat(metaRefBefore.getObjectId()).isEqualTo(metaRefAfter.getObjectId());
    assertThat(backfillResult.refsFixed).isEmpty();
  }

  @Test
  public void failedVerification() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Unknown commit " + changeOwner.getName());
    update.setTag(tag);
    update.commit();

    ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
    updateWithSubject.setSubjectForCommit("Update with subject");
    updateWithSubject.commit();

    ChangeNotes notesBeforeRewrite = newNotes(c);
    Ref metaRefBefore = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, false, true);
    assertThat(backfillResult.refsFixed).isEmpty();
    assertThat(backfillResult.refsStillInvalidAfterFix)
        .containsExactly(RefNames.changeMetaRef(c.getId()));
    ChangeNotes notesAfterRewrite = newNotes(c);
    Ref metaRefAfter = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    assertThat(notesBeforeRewrite.getMetaId()).isEqualTo(notesAfterRewrite.getMetaId());
    assertThat(metaRefBefore.getObjectId()).isEqualTo(metaRefAfter.getObjectId());
  }

  @Test
  public void fixAuthorIdent() throws Exception {
    Change c = newChange();
    Timestamp when = TimeUtil.nowTs();
    PersonIdent invalidAuthorIdent =
        new PersonIdent(
            changeOwner.getName(),
            changeNoteUtil.getAccountIdAsEmailAddress(changeOwner.getAccountId()),
            when,
            serverIdent.getTimeZone());
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(c, /*changeMessage=*/ null),
            invalidAuthorIdent);
    ChangeUpdate validUpdate = newUpdate(c, changeOwner);
    validUpdate.setChangeMessage("verification from jenkins");
    validUpdate.setTag("jenkins");
    validUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    BackfillResult result = rewriter.backfillProject(project, repo, false, true);
    assertThat(result.refsFixed).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesAfterRewrite.getChange().getOwner())
        .isEqualTo(notesBeforeRewrite.getChange().getOwner());
    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());
    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);

    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));
    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    PersonIdent originalAuthorIdent = invalidUpdateCommit.getAuthorIdent();
    PersonIdent fixedAuthorIdent = fixedUpdateCommit.getAuthorIdent();
    assertThat(originalAuthorIdent).isNotEqualTo(fixedAuthorIdent);
    assertThat(fixedUpdateCommit.getAuthorIdent().getName())
        .isEqualTo("Gerrit User " + changeOwner.getAccountId());
    assertThat(originalAuthorIdent.getEmailAddress()).isEqualTo(fixedAuthorIdent.getEmailAddress());
    assertThat(originalAuthorIdent.getWhen()).isEqualTo(fixedAuthorIdent.getWhen());
    assertThat(originalAuthorIdent.getTimeZone()).isEqualTo(fixedAuthorIdent.getTimeZone());
    assertThat(invalidUpdateCommit.getFullMessage()).isEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
  }

  @Test
  public void fixAssigneeIdent() throws Exception {
    Change c = newChange();

    String assigneeIdentToFix = getAccountIdentToFix(changeOwner.getAccount());
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(c, "Assignee added", "Assignee: " + assigneeIdentToFix),
            getAuthorIdent(changeOwner.getAccount()));

    ChangeUpdate changeAssigneeUpdate = newUpdate(c, changeOwner);
    changeAssigneeUpdate.setAssignee(otherUserId);
    changeAssigneeUpdate.commit();

    ChangeUpdate removeAssigneeUpdate = newUpdate(c, changeOwner);
    removeAssigneeUpdate.removeAssignee();
    removeAssigneeUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    BackfillResult result = rewriter.backfillProject(project, repo, false, true);
    assertThat(result.refsFixed).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesBeforeRewrite.getChange().getAssignee()).isNull();
    assertThat(notesAfterRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesAfterRewrite.getChange().getAssignee()).isNull();

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));

    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent()).isEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(invalidUpdateCommit.getFullMessage())
        .isNotEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    assertThat(invalidUpdateCommit.getFullMessage()).contains(assigneeIdentToFix);
    String expectedFixedIdent = getValidIdentAsString(changeOwner.getAccount());
    assertThat(fixedUpdateCommit.getFullMessage()).contains(expectedFixedIdent);
  }

  @Test
  public void fixAssigneeChangeMessage() throws Exception {
    Change c = newChange();

    ImmutableList<RevCommit> commitsToFix =
        new ImmutableList.Builder<RevCommit>()
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        "Assignee added: " + changeOwner.getNameEmail(),
                        "Assignee: " + getValidIdentAsString(changeOwner.getAccount())),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        String.format(
                            "Assignee changed from: %s to: %s",
                            changeOwner.getNameEmail(), otherUser.getNameEmail()),
                        "Assignee: " + getValidIdentAsString(otherUser.getAccount())),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c, "Assignee deleted: " + otherUser.getNameEmail(), "Assignee:"),
                    getAuthorIdent(changeOwner.getAccount())))
            .build();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    BackfillResult result = rewriter.backfillProject(project, repo, false, true);
    assertThat(result.refsFixed).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesBeforeRewrite.getChange().getAssignee()).isNull();
    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly(
            "Assignee added: Change Owner <change@owner.com>",
            "Assignee changed from: Change Owner <change@owner.com> to: Other Account <other@account.com>",
            "Assignee deleted: Other Account <other@account.com>");

    assertThat(notesAfterRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesAfterRewrite.getChange().getAssignee()).isNull();
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "Assignee added: " + ChangeMessagesUtil.getAccountTemplate(changeOwner.getAccountId()),
            String.format(
                "Assignee changed from: %s to: %s",
                ChangeMessagesUtil.getAccountTemplate(changeOwner.getAccountId()),
                ChangeMessagesUtil.getAccountTemplate(otherUser.getAccountId())),
            "Assignee deleted: " + ChangeMessagesUtil.getAccountTemplate(otherUser.getAccountId()));

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
  }

  @Test
  public void singleRunFixesAll() throws Exception {
    Change c = newChange();
    Timestamp when = TimeUtil.nowTs();
    String assigneeIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    PersonIdent authorIdentToFix =
        new PersonIdent(
            changeOwner.getName(),
            changeNoteUtil.getAccountIdAsEmailAddress(changeOwner.getAccountId()),
            when,
            serverIdent.getTimeZone());

    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(
                c,
                "Assignee added: Other Account <other@account.com>",
                "Assignee: " + assigneeIdentToFix),
            authorIdentToFix);
    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    BackfillResult result = rewriter.backfillProject(project, repo, false, true);
    assertThat(result.refsFixed).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getChange().getAssignee()).isEqualTo(otherUserId);
    assertThat(notesAfterRewrite.getChange().getAssignee()).isEqualTo(otherUserId);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));

    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent())
        .isNotEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getFullMessage()).contains(otherUser.getName());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(otherUser.getName());
  }

  private RevCommit writeUpdate(String metaRef, String body, PersonIdent author) throws Exception {
    return tr.branch(metaRef).commit().message(body).author(author).committer(serverIdent).create();
  }

  private String getChangeUpdateBody(Change change, String changeMessage, String... footers) {
    StringBuilder commitBody = new StringBuilder();
    commitBody.append("Update patch set " + change.currentPatchSetId().get());
    commitBody.append("\n\n");
    if (changeMessage != null) {
      commitBody.append(changeMessage);
      commitBody.append("\n\n");
    }
    commitBody.append("Patch-set: " + change.currentPatchSetId().get());
    commitBody.append("\n");
    for (String footer : footers) {
      commitBody.append(footer);
      commitBody.append("\n");
    }
    return commitBody.toString();
  }

  private ImmutableList<RevCommit> logMetaRef(Repository repo, Ref metaRef) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE);
      if (metaRef == null) {
        return ImmutableList.of();
      }
      rw.markStart(rw.parseCommit(metaRef.getObjectId()));
      return ImmutableList.copyOf(rw);
    }
  }

  private void assertValidCommits(
      ImmutableList<RevCommit> commitsBeforeRewrite,
      ImmutableList<RevCommit> commitsAfterRewrite,
      ImmutableList<Integer> invalidCommits) {
    ImmutableList<RevCommit> validCommitsBeforeRewrite =
        IntStream.range(0, commitsBeforeRewrite.size())
            .filter(i -> invalidCommits.contains(i))
            .mapToObj(commitsBeforeRewrite::get)
            .collect(ImmutableList.toImmutableList());

    ImmutableList<RevCommit> validCommitsAfterRewrite =
        IntStream.range(0, commitsAfterRewrite.size())
            .filter(i -> invalidCommits.contains(i))
            .mapToObj(commitsAfterRewrite::get)
            .collect(ImmutableList.toImmutableList());

    assertThat(validCommitsBeforeRewrite).hasSize(validCommitsAfterRewrite.size());
    for (int i = 0; i < validCommitsAfterRewrite.size(); i++) {
      RevCommit actual = validCommitsBeforeRewrite.get(i);
      RevCommit expected = validCommitsBeforeRewrite.get(i);
      assertThat(actual.getAuthorIdent()).isEqualTo(expected.getAuthorIdent());
      assertThat(actual.getCommitterIdent()).isEqualTo(expected.getCommitterIdent());
      assertThat(actual.getFullMessage()).isEqualTo(expected.getFullMessage());
    }
  }

  private String getAccountIdentToFix(Account account) {
    return String.format("%s <%s>", account.getName(), account.id().get() + "@" + serverId);
  }

  private String getValidIdentAsString(Account account) {
    return String.format(
        "%s <%s>",
        ChangeNoteUtil.getAccountIdAsUsername(account.id()), account.id().get() + "@" + serverId);
  }

  private ImmutableList<String> changeMessages(ChangeNotes changeNotes) {
    return changeNotes.getChangeMessages().stream()
        .map(ChangeMessage::getMessage)
        .collect(toImmutableList());
  }

  private PersonIdent getAuthorIdent(Account account) {
    Timestamp when = TimeUtil.nowTs();
    return changeNoteUtil.newAccountIdIdent(account.id(), when, serverIdent);
  }
}
