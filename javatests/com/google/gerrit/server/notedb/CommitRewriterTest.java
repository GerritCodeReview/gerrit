package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
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
import org.junit.Test;

public class CommitRewriterTest extends AbstractChangeNotesTest {

  @Inject private CommitRewriter rewriter;
  @Inject private ChangeNoteUtil changeNoteUtil;

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
    // assertThat(notes.getChangeMessages()).hasSize(1);
    // assertThat(notes.getChangeMessages().get(0).getTag()).isEqualTo(tag);
    rewriter.backfillProject(repo, false);
    ChangeNotes notesAfterRewrite = newNotes(c);
    Ref metaRefAfter = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    // RevWalk walk = ChangeNotesCommit.newRevWalk(repo);
    // RevCommit tipCommit = walk.parseCommit(metaRef.getObjectId());
    assertThat(notesBeforeRewrite.getMetaId()).isEqualTo(notesAfterRewrite.getMetaId());
    assertThat(metaRefBefore.getObjectId()).isEqualTo(metaRefAfter.getObjectId());
  }

  @Test
  public void fixInvalidAuthorIdent() throws Exception {
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
            String.format(
                "Update patch set %d\n\nPatch-set: %d\n",
                c.currentPatchSetId().get(), c.currentPatchSetId().get()),
            invalidAuthorIdent,
            new PersonIdent(serverIdent, when));
    ChangeUpdate validUpdate = newUpdate(c, changeOwner);
    validUpdate.setChangeMessage("verification from jenkins");
    validUpdate.setTag("jenkins");
    validUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite =
        logMetaRef(repo, RefNames.changeMetaRef(c.getId()));
    ImmutableList<RevCommit> validCommitsBeforeRewrite =
        commitsBeforeRewrite.stream()
            .filter(commit -> commit.equals(invalidUpdateCommit))
            .collect(ImmutableList.toImmutableList());
    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    rewriter.backfillProject(repo, false);

    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesAfterRewrite.getChange().getOwner())
        .isEqualTo(notesBeforeRewrite.getChange().getOwner());
    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());
    ImmutableList<RevCommit> commitsAfterRewrite =
        logMetaRef(repo, RefNames.changeMetaRef(c.getId()));
    ImmutableList<RevCommit> validCommitsAfterRewrite =
        IntStream.range(0, commitsAfterRewrite.size())
            .filter(i -> i == invalidCommitIndex)
            .mapToObj(commitsAfterRewrite::get)
            .collect(ImmutableList.toImmutableList());

    assertLogEqual(validCommitsBeforeRewrite, validCommitsAfterRewrite);
    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent())
        .isNotEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(fixedUpdateCommit.getAuthorIdent().getName())
        .isEqualTo("Gerrit User " + changeOwner.getAccountId());
    assertThat(invalidUpdateCommit.getFullMessage()).isEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
  }

  @Test
  public void fixInvalidAssigneeIdent() throws Exception {
    Change c = newChange();
    Timestamp when = TimeUtil.nowTs();
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            String.format(
                "Update patch set %d\n\nPatch-set: %d\nAssignee: %s",
                c.currentPatchSetId().get(),
                c.currentPatchSetId().get(),
                getAccountIdentToFix(changeOwner.getAccount())),
            changeNoteUtil.newAccountIdIdent(changeOwner.getAccountId(), when, serverIdent),
            new PersonIdent(serverIdent, when));

    ChangeUpdate changeAssigneeUpdate = newUpdate(c, changeOwner);
    changeAssigneeUpdate.setAssignee(otherUserId);
    changeAssigneeUpdate.commit();

    ChangeUpdate removeAssigneeUpdate = newUpdate(c, changeOwner);
    removeAssigneeUpdate.removeAssignee();
    removeAssigneeUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite =
        logMetaRef(repo, RefNames.changeMetaRef(c.getId()));
    ImmutableList<RevCommit> validCommitsBeforeRewrite =
        commitsBeforeRewrite.stream()
            .filter(commit -> commit.equals(invalidUpdateCommit))
            .collect(ImmutableList.toImmutableList());
    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    rewriter.backfillProject(repo, false);

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesBeforeRewrite.getChange().getAssignee()).isNull();
    assertThat(notesAfterRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesAfterRewrite.getChange().getAssignee()).isNull();

    assertThat(notesAfterRewrite.getChange().getOwner())
        .isEqualTo(notesBeforeRewrite.getChange().getOwner());
    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());
    ImmutableList<RevCommit> commitsAfterRewrite =
        logMetaRef(repo, RefNames.changeMetaRef(c.getId()));
    ImmutableList<RevCommit> validCommitsAfterRewrite =
        IntStream.range(0, commitsAfterRewrite.size())
            .filter(i -> i == invalidCommitIndex)
            .mapToObj(commitsAfterRewrite::get)
            .collect(ImmutableList.toImmutableList());

    assertLogEqual(validCommitsBeforeRewrite, validCommitsAfterRewrite);
    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent()).isEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(invalidUpdateCommit.getFullMessage())
        .isNotEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(invalidUpdateCommit.getFullMessage()).contains(changeOwner.getName());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
  }

  private RevCommit writeUpdate(
      String metaRef, String body, PersonIdent author, PersonIdent committer) throws Exception {
    return tr.branch(metaRef).commit().message(body).author(author).committer(committer).create();
  }

  private ImmutableList<RevCommit> logMetaRef(Repository repo, String refName) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE);
      Ref ref = repo.exactRef(refName);
      if (ref == null) {
        return ImmutableList.of();
      }
      rw.markStart(rw.parseCommit(ref.getObjectId()));
      return ImmutableList.copyOf(rw);
    }
  }

  private void assertLogEqual(
      ImmutableList<RevCommit> expectedCommits, ImmutableList<RevCommit> actualCommits) {
    {
      assertThat(actualCommits).hasSize(expectedCommits.size());
      for (int i = 0; i < expectedCommits.size(); i++) {
        RevCommit actual = actualCommits.get(i);
        RevCommit expected = actualCommits.get(i);
        assertThat(actual.getAuthorIdent()).isEqualTo(expected.getAuthorIdent());
        assertThat(actual.getCommitterIdent()).isEqualTo(expected.getCommitterIdent());
        assertThat(actual.getFullMessage()).isEqualTo(expected.getFullMessage());
      }
    }
  }

  private String getAccountIdentToFix(Account account) {
    return String.format(
        "%s <%s>", account.getName(), changeNoteUtil.getAccountIdAsEmailAddress(account.id()));
  }
}
