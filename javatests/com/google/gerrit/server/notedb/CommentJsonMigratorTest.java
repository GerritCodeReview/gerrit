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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.notedb.CommentJsonMigrator.ProjectMigrationResult;
import com.google.gerrit.testing.TestChanges;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class CommentJsonMigratorTest extends AbstractChangeNotesTest {
  private CommentJsonMigrator migrator;
  @Inject private ChangeNoteUtil noteUtil;
  @Inject private CommentsUtil commentsUtil;
  @Inject private LegacyChangeNoteWrite legacyChangeNoteWrite;
  @Inject private AllUsersName allUsersName;

  private AtomicInteger uuidCounter;

  @Before
  public void setUpCounter() {
    uuidCounter = new AtomicInteger();
    migrator = new CommentJsonMigrator(new ChangeNoteJson(), "gerrit", allUsersName);
  }

  @Test
  public void noOpIfAllCommentsAreJson() throws Exception {
    Change c = newChange();
    incrementPatchSet(c);

    ChangeNotes notes = newNotes(c);
    ChangeUpdate update = newUpdate(c, changeOwner);
    Comment ps1Comment = newComment(notes, 1, "comment on ps1");
    update.putComment(Status.PUBLISHED, ps1Comment);
    update.commit();

    notes = newNotes(c);
    update = newUpdate(c, changeOwner);
    Comment ps2Comment = newComment(notes, 2, "comment on ps2");
    update.putComment(Status.PUBLISHED, ps2Comment);
    update.commit();

    notes = newNotes(c);
    assertThat(getToStringRepresentations(notes.getComments()))
        .containsExactly(
            getRevId(notes, 1), ps1Comment.toString(),
            getRevId(notes, 2), ps2Comment.toString());

    ChangeNotes oldNotes = notes;
    checkMigrate(project, ImmutableList.of());
    assertNoDifferences(notes, oldNotes);
    assertThat(notes.getMetaId()).isEqualTo(oldNotes.getMetaId());
  }

  @Test
  public void migratePublishedComments() throws Exception {
    Change c = newChange();
    incrementPatchSet(c);

    ChangeNotes notes = newNotes(c);

    Comment ps1Comment1 = newComment(notes, 1, "first comment on ps1");
    Comment ps2Comment1 = newComment(notes, 2, "first comment on ps2");
    Comment ps1Comment2 = newComment(notes, 1, "second comment on ps1");

    // Construct legacy format 'by hand'.
    ByteArrayOutputStream out1 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder().put(1, ps1Comment1).build(), out1);

    ByteArrayOutputStream out2 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder().put(2, ps2Comment1).build(), out2);

    ByteArrayOutputStream out3 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder()
            .put(1, ps1Comment2)
            .put(1, ps1Comment1)
            .build(),
        out3);

    TestRepository<Repository> testRepository = new TestRepository<>(repo, rw);

    String metaRefName = RefNames.changeMetaRef(c.getId());
    testRepository
        .branch(metaRefName)
        .commit()
        .message("Review ps 1\n\nPatch-set: 1")
        .add(ps1Comment1.revId, out1.toString())
        .author(serverIdent)
        .committer(serverIdent)
        .create();

    testRepository
        .branch(metaRefName)
        .commit()
        .message("Review ps 2\n\nPatch-set: 2")
        .add(ps2Comment1.revId, out2.toString())
        .add(ps1Comment1.revId, out3.toString())
        .author(serverIdent)
        .committer(serverIdent)
        .create();

    notes = newNotes(c);
    assertThat(getToStringRepresentations(notes.getComments()))
        .containsExactly(
            getRevId(notes, 1), ps1Comment1.toString(),
            getRevId(notes, 1), ps1Comment2.toString(),
            getRevId(notes, 2), ps2Comment1.toString());

    // Comments at each commit all have legacy format.
    ImmutableList<RevCommit> oldLog = log(project, RefNames.changeMetaRef(c.getId()));
    assertThat(oldLog).hasSize(4);
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(0))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(1))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(2)))
        .containsExactly(ps1Comment1.key, true);
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(3)))
        .containsExactly(ps1Comment1.key, true, ps1Comment2.key, true, ps2Comment1.key, true);

    ChangeNotes oldNotes = notes;
    checkMigrate(project, ImmutableList.of(RefNames.changeMetaRef(c.getId())));

    // Comment content is the same.
    notes = newNotes(c);
    assertNoDifferences(notes, oldNotes);
    assertThat(getToStringRepresentations(notes.getComments()))
        .containsExactly(
            getRevId(notes, 1), ps1Comment1.toString(),
            getRevId(notes, 1), ps1Comment2.toString(),
            getRevId(notes, 2), ps2Comment1.toString());

    // Comments at each commit all have JSON format.
    ImmutableList<RevCommit> newLog = log(project, RefNames.changeMetaRef(c.getId()));
    assertLogEqualExceptTrees(newLog, oldLog);
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(0))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(1))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(2)))
        .containsExactly(ps1Comment1.key, false);
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(3)))
        .containsExactly(ps1Comment1.key, false, ps1Comment2.key, false, ps2Comment1.key, false);
  }

  @Test
  public void migrateDraftComments() throws Exception {
    Change c = newChange();
    incrementPatchSet(c);

    ChangeNotes notes = newNotes(c);
    ObjectId origMetaId = notes.getMetaId();

    Comment ownerCommentPs1 = newComment(notes, 1, "owner comment on ps1", changeOwner);
    Comment ownerCommentPs2 = newComment(notes, 2, "owner comment on ps2", changeOwner);
    Comment otherCommentPs1 = newComment(notes, 1, "other user comment on ps1", otherUser);

    ByteArrayOutputStream out1 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder().put(1, ownerCommentPs1).build(), out1);

    ByteArrayOutputStream out2 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder().put(2, ownerCommentPs2).build(), out2);

    ByteArrayOutputStream out3 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder().put(1, otherCommentPs1).build(), out3);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        RevWalk allUsersRw = new RevWalk(allUsersRepo)) {
      TestRepository<Repository> testRepository = new TestRepository<>(allUsersRepo, allUsersRw);

      testRepository
          .branch(RefNames.refsDraftComments(c.getId(), changeOwner.getAccountId()))
          .commit()
          .message("Review ps 1\n\nPatch-set: 1")
          .add(ownerCommentPs1.revId, out1.toString())
          .author(serverIdent)
          .committer(serverIdent)
          .create();

      testRepository
          .branch(RefNames.refsDraftComments(c.getId(), changeOwner.getAccountId()))
          .commit()
          .message("Review ps 1\n\nPatch-set: 2")
          .add(ownerCommentPs2.revId, out2.toString())
          .author(serverIdent)
          .committer(serverIdent)
          .create();

      testRepository
          .branch(RefNames.refsDraftComments(c.getId(), otherUser.getAccountId()))
          .commit()
          .message("Review ps 2\n\nPatch-set: 2")
          .add(otherCommentPs1.revId, out3.toString())
          .author(serverIdent)
          .committer(serverIdent)
          .create();
    }

    notes = newNotes(c);
    assertThat(getToStringRepresentations(notes.getDraftComments(changeOwner.getAccountId())))
        .containsExactly(
            getRevId(notes, 1), ownerCommentPs1.toString(),
            getRevId(notes, 2), ownerCommentPs2.toString());
    assertThat(getToStringRepresentations(notes.getDraftComments(otherUser.getAccountId())))
        .containsExactly(getRevId(notes, 1), otherCommentPs1.toString());

    // Comments at each commit all have legacy format.
    ImmutableList<RevCommit> oldOwnerLog =
        log(allUsers, RefNames.refsDraftComments(c.getId(), changeOwner.getAccountId()));
    assertThat(oldOwnerLog).hasSize(2);
    assertThat(getLegacyFormatMapForDraftComments(notes, oldOwnerLog.get(0)))
        .containsExactly(ownerCommentPs1.key, true);
    assertThat(getLegacyFormatMapForDraftComments(notes, oldOwnerLog.get(1)))
        .containsExactly(ownerCommentPs1.key, true, ownerCommentPs2.key, true);

    ImmutableList<RevCommit> oldOtherLog =
        log(allUsers, RefNames.refsDraftComments(c.getId(), otherUser.getAccountId()));
    assertThat(oldOtherLog).hasSize(1);
    assertThat(getLegacyFormatMapForDraftComments(notes, oldOtherLog.get(0)))
        .containsExactly(otherCommentPs1.key, true);

    ChangeNotes oldNotes = notes;
    checkMigrate(
        allUsers,
        ImmutableList.of(
            RefNames.refsDraftComments(c.getId(), changeOwner.getAccountId()),
            RefNames.refsDraftComments(c.getId(), otherUser.getAccountId())));
    assertNoDifferences(notes, oldNotes);

    // Migration doesn't touch change ref.
    assertThat(repo.exactRef(RefNames.changeMetaRef(c.getId())).getObjectId())
        .isEqualTo(origMetaId);

    // Comment content is the same.
    notes = newNotes(c);
    assertThat(getToStringRepresentations(notes.getDraftComments(changeOwner.getAccountId())))
        .containsExactly(
            getRevId(notes, 1), ownerCommentPs1.toString(),
            getRevId(notes, 2), ownerCommentPs2.toString());
    assertThat(getToStringRepresentations(notes.getDraftComments(otherUser.getAccountId())))
        .containsExactly(getRevId(notes, 1), otherCommentPs1.toString());

    // Comments at each commit all have JSON format.
    ImmutableList<RevCommit> newOwnerLog =
        log(allUsers, RefNames.refsDraftComments(c.getId(), changeOwner.getAccountId()));
    assertLogEqualExceptTrees(newOwnerLog, oldOwnerLog);
    assertThat(getLegacyFormatMapForDraftComments(notes, newOwnerLog.get(0)))
        .containsExactly(ownerCommentPs1.key, false);
    assertThat(getLegacyFormatMapForDraftComments(notes, newOwnerLog.get(1)))
        .containsExactly(ownerCommentPs1.key, false, ownerCommentPs2.key, false);

    ImmutableList<RevCommit> newOtherLog =
        log(allUsers, RefNames.refsDraftComments(c.getId(), otherUser.getAccountId()));
    assertLogEqualExceptTrees(newOtherLog, oldOtherLog);
    assertThat(getLegacyFormatMapForDraftComments(notes, newOtherLog.get(0)))
        .containsExactly(otherCommentPs1.key, false);
  }

  @Test
  public void migrateMixOfJsonAndLegacyComments() throws Exception {
    // 3 comments: legacy, JSON, legacy. Because adding a comment necessarily rewrites the entire
    // note, these comments need to be on separate patch sets.
    Change c = newChange();
    incrementPatchSet(c);
    incrementPatchSet(c);

    ChangeNotes notes = newNotes(c);

    Comment ps1Comment = newComment(notes, 1, "comment on ps1 (legacy)");

    ByteArrayOutputStream out1 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder().put(1, ps1Comment).build(), out1);

    TestRepository<Repository> testRepository = new TestRepository<>(repo, rw);

    String metaRefName = RefNames.changeMetaRef(c.getId());
    testRepository
        .branch(metaRefName)
        .commit()
        .message("Review ps 1\n\nPatch-set: 1")
        .add(ps1Comment.revId, out1.toString())
        .author(serverIdent)
        .committer(serverIdent)
        .create();

    notes = newNotes(c);
    ChangeUpdate update = newUpdate(c, changeOwner);
    Comment ps2Comment = newComment(notes, 2, "comment on ps2 (JSON)");
    update.putComment(Status.PUBLISHED, ps2Comment);
    update.commit();

    Comment ps3Comment = newComment(notes, 3, "comment on ps3 (legacy)");
    ByteArrayOutputStream out3 = new ByteArrayOutputStream(0);
    legacyChangeNoteWrite.buildNote(
        ImmutableListMultimap.<Integer, Comment>builder().put(3, ps3Comment).build(), out3);

    testRepository
        .branch(metaRefName)
        .commit()
        .message("Review ps 3\n\nPatch-set: 3")
        .add(ps3Comment.revId, out3.toString())
        .author(serverIdent)
        .committer(serverIdent)
        .create();

    notes = newNotes(c);
    assertThat(getToStringRepresentations(notes.getComments()))
        .containsExactly(
            getRevId(notes, 1), ps1Comment.toString(),
            getRevId(notes, 2), ps2Comment.toString(),
            getRevId(notes, 3), ps3Comment.toString());

    // Comments at each commit match expected format.
    ImmutableList<RevCommit> oldLog = log(project, RefNames.changeMetaRef(c.getId()));
    assertThat(oldLog).hasSize(6);
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(0))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(1))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(2))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(3)))
        .containsExactly(ps1Comment.key, true);
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(4)))
        .containsExactly(ps1Comment.key, true, ps2Comment.key, false);
    assertThat(getLegacyFormatMapForPublishedComments(notes, oldLog.get(5)))
        .containsExactly(ps1Comment.key, true, ps2Comment.key, false, ps3Comment.key, true);

    ChangeNotes oldNotes = notes;
    checkMigrate(project, ImmutableList.of(RefNames.changeMetaRef(c.getId())));
    assertNoDifferences(notes, oldNotes);

    // Comment content is the same.
    notes = newNotes(c);
    assertThat(getToStringRepresentations(notes.getComments()))
        .containsExactly(
            getRevId(notes, 1), ps1Comment.toString(),
            getRevId(notes, 2), ps2Comment.toString(),
            getRevId(notes, 3), ps3Comment.toString());

    // Comments at each commit all have JSON format.
    ImmutableList<RevCommit> newLog = log(project, RefNames.changeMetaRef(c.getId()));
    assertLogEqualExceptTrees(newLog, oldLog);
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(0))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(1))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(2))).isEmpty();
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(3)))
        .containsExactly(ps1Comment.key, false);
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(4)))
        .containsExactly(ps1Comment.key, false, ps2Comment.key, false);
    assertThat(getLegacyFormatMapForPublishedComments(notes, newLog.get(5)))
        .containsExactly(ps1Comment.key, false, ps2Comment.key, false, ps3Comment.key, false);
  }

  private void checkMigrate(Project.NameKey project, List<String> expectedRefs) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      ProjectMigrationResult progress = migrator.migrateProject(project, repo, false);

      assertThat(progress.ok).isTrue();
      assertThat(progress.refsUpdated).isEqualTo(expectedRefs);
    }
  }

  private Comment newComment(ChangeNotes notes, int psNum, String message) {
    return newComment(notes, psNum, message, changeOwner);
  }

  private Comment newComment(
      ChangeNotes notes, int psNum, String message, IdentifiedUser commenter) {
    return newComment(
        new PatchSet.Id(notes.getChangeId(), psNum),
        "filename",
        "uuid-" + uuidCounter.getAndIncrement(),
        null,
        0,
        commenter,
        null,
        TimeUtil.nowTs(),
        message,
        (short) 1,
        getRevId(notes, psNum).get(),
        false);
  }

  private void incrementPatchSet(Change c) throws Exception {
    TestChanges.incrementPatchSet(c);
    RevCommit commit = tr.commit().message("PS" + c.currentPatchSetId().get()).create();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setCommit(rw, commit);
    update.commit();
  }

  private static RevId getRevId(ChangeNotes notes, int psNum) {
    PatchSet.Id psId = new PatchSet.Id(notes.getChangeId(), psNum);
    PatchSet ps = notes.getPatchSets().get(psId);
    checkArgument(ps != null, "no patch set %s: %s", psNum, notes.getPatchSets());
    return ps.getRevision();
  }

  private static ListMultimap<RevId, String> getToStringRepresentations(
      ListMultimap<RevId, Comment> comments) {
    // Use string representation for equality comparison in this test, because Comment#equals only
    // compares keys.
    return Multimaps.transformValues(comments, Comment::toString);
  }

  private ImmutableMap<Comment.Key, Boolean> getLegacyFormatMapForPublishedComments(
      ChangeNotes notes, ObjectId metaId) throws Exception {
    return getLegacyFormatMap(project, notes.getChangeId(), metaId, Status.PUBLISHED);
  }

  private ImmutableMap<Comment.Key, Boolean> getLegacyFormatMapForDraftComments(
      ChangeNotes notes, ObjectId metaId) throws Exception {
    return getLegacyFormatMap(allUsers, notes.getChangeId(), metaId, Status.DRAFT);
  }

  private ImmutableMap<Comment.Key, Boolean> getLegacyFormatMap(
      Project.NameKey project, Change.Id changeId, ObjectId metaId, Status status)
      throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      NoteMap noteMap = NoteMap.read(reader, rw.parseCommit(metaId));
      RevisionNoteMap<ChangeRevisionNote> revNoteMap =
          RevisionNoteMap.parse(
              noteUtil.getChangeNoteJson(),
              noteUtil.getLegacyChangeNoteRead(),
              changeId,
              reader,
              noteMap,
              status);
      return revNoteMap
          .revisionNotes
          .values()
          .stream()
          .flatMap(crn -> crn.getComments().stream())
          .collect(toImmutableMap(c -> c.key, c -> c.legacyFormat));
    }
  }

  private ImmutableList<RevCommit> log(Project.NameKey project, String refName) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE);
      Ref ref = repo.exactRef(refName);
      checkArgument(ref != null, "missing ref: %s", refName);
      rw.markStart(rw.parseCommit(ref.getObjectId()));
      return ImmutableList.copyOf(rw);
    }
  }

  private static void assertLogEqualExceptTrees(
      ImmutableList<RevCommit> actualLog, ImmutableList<RevCommit> expectedLog) {
    assertThat(actualLog).hasSize(expectedLog.size());
    for (int i = 0; i < expectedLog.size(); i++) {
      RevCommit actual = actualLog.get(i);
      RevCommit expected = expectedLog.get(i);
      assertThat(actual.getAuthorIdent())
          .named("author of entry %s", i)
          .isEqualTo(expected.getAuthorIdent());
      assertThat(actual.getCommitterIdent())
          .named("committer of entry %s", i)
          .isEqualTo(expected.getCommitterIdent());
      assertThat(actual.getFullMessage()).named("message of entry %s", i).isNotNull();
      assertThat(actual.getFullMessage())
          .named("message of entry %s", i)
          .isEqualTo(expected.getFullMessage());
    }
  }

  private void assertNoDifferences(ChangeNotes actual, ChangeNotes expected) throws Exception {
    assertThat(
            ChangeBundle.fromNotes(commentsUtil, actual)
                .differencesFrom(ChangeBundle.fromNotes(commentsUtil, expected)))
        .isEmpty();
  }
}
