package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
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
  public void invalidAuthorIdent() throws Exception {
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
            "Update patch set " + c.currentPatchSetId().get() + "\n\n",
            invalidAuthorIdent,
            new PersonIdent(serverIdent, when));
    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefBeforeRewrite.getObjectId()).isEqualTo(invalidUpdateCommit);
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
    assertThat(invalidUpdateCommit.getAuthorIdent()).isEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getFullMessage()).isEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(fixedUpdateCommit.getFullMessage()).contains(changeOwner.getName());
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
}
