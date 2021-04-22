package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Injector;
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

public class CommitRewriterTest extends AbstractChangeNotesTest {

  private @Inject CommitRewriter rewriter;
  @Inject private ChangeNoteUtil changeNoteUtil;

  private String rewriterServerId = "rewriterServerId";

  @Before
  public void setUp() throws Exception {

  }

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
            invalidAuthorIdent);
    ChangeUpdate validUpdate = newUpdate(c, changeOwner);
    validUpdate.setChangeMessage("verification from jenkins");
    validUpdate.setTag("jenkins");
    validUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite =
        logMetaRef(repo, metaRefBeforeRewrite);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    rewriter.backfillProject(repo, false);

    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesAfterRewrite.getChange().getOwner())
        .isEqualTo(notesBeforeRewrite.getChange().getOwner());
    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());
    ImmutableList<RevCommit> commitsAfterRewrite =
        logMetaRef(repo, metaRefAfterRewrite);
    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);

    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommitIndex);
    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    PersonIdent originalAuthorIdent = invalidUpdateCommit.getAuthorIdent();
    PersonIdent fixedAuthorIdent = fixedUpdateCommit.getAuthorIdent();
    assertThat(originalAuthorIdent)
        .isNotEqualTo(fixedAuthorIdent);
    assertThat(fixedUpdateCommit.getAuthorIdent().getName())
        .isEqualTo("Gerrit User " + changeOwner.getAccountId());
    assertThat(originalAuthorIdent.getEmailAddress())
        .isEqualTo(fixedAuthorIdent.getEmailAddress());
    assertThat(originalAuthorIdent.getWhen())
        .isEqualTo(fixedAuthorIdent.getWhen());
    assertThat(originalAuthorIdent.getTimeZone())
        .isEqualTo(fixedAuthorIdent.getTimeZone());
    assertThat(invalidUpdateCommit.getFullMessage()).isEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
  }

  @Test
  public void fixInvalidAssigneeIdent() throws Exception {
    Change c = newChange();
    Timestamp when = TimeUtil.nowTs();
    String assigneeIdentToFix =  getAccountIdentToFix(changeOwner.getAccount());
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            String.format(
                "Update patch set %d\n\nPatch-set: %d\nAssignee: %s",
                c.currentPatchSetId().get(),
                c.currentPatchSetId().get(),
                assigneeIdentToFix),
            changeNoteUtil.newAccountIdIdent(changeOwner.getAccountId(), when, serverIdent));

    ChangeUpdate changeAssigneeUpdate = newUpdate(c, changeOwner);
    changeAssigneeUpdate.setAssignee(otherUserId);
    changeAssigneeUpdate.commit();

    ChangeUpdate removeAssigneeUpdate = newUpdate(c, changeOwner);
    removeAssigneeUpdate.removeAssignee();
    removeAssigneeUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite =
        logMetaRef(repo, metaRefBeforeRewrite);

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

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite =
        logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommitIndex);

    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent()).isEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(invalidUpdateCommit.getFullMessage())
        .isNotEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    assertThat(invalidUpdateCommit.getFullMessage()).contains(assigneeIdentToFix);
    String expectedFixedIdent = getExpectedFixedIdent(changeOwner.getAccount());
    assertThat(fixedUpdateCommit.getFullMessage()).contains(expectedFixedIdent);

  }

  private RevCommit writeUpdate(
      String metaRef, String body, PersonIdent author) throws Exception {
    return tr.branch(metaRef).commit().message(body).author(author).committer(serverIdent).create();
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

  private void assertValidCommits(ImmutableList<RevCommit> commitsBeforeRewrite, ImmutableList<RevCommit> commitsAfterRewrite, int invalidCommitIndex) {
    ImmutableList<RevCommit> validCommitsBeforeRewrite =
        IntStream.range(0, commitsBeforeRewrite.size())
            .filter(i -> i == invalidCommitIndex)
            .mapToObj(commitsBeforeRewrite::get)
            .collect(ImmutableList.toImmutableList());

    ImmutableList<RevCommit> validCommitsAfterRewrite =
        IntStream.range(0, commitsAfterRewrite.size())
            .filter(i -> i == invalidCommitIndex)
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
    return String.format(
        "%s <%s>", account.getName(), account.id().get() + "@" + serverId);
  }

  private String getExpectedFixedIdent(Account account){
    return String.format("%s <%s>", ChangeNoteUtil.getAccountIdAsUsername(account.id()), account.id().get() + "@" + serverId);
  }
}
