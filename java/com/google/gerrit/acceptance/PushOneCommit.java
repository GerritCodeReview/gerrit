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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

public class PushOneCommit {
  public static final String SUBJECT = "test commit";
  public static final String FILE_NAME = "a.txt";
  public static final String FILE_CONTENT = "some content";
  public static final String PATCH_FILE_ONLY =
      "diff --git a/a.txt b/a.txt\n"
          + "new file mode 100644\n"
          + "index 0000000..f0eec86\n"
          + "--- /dev/null\n"
          + "+++ b/a.txt\n"
          + "@@ -0,0 +1 @@\n"
          + "+some content\n"
          + "\\ No newline at end of file\n";
  public static final String PATCH =
      "From %s Mon Sep 17 00:00:00 2001\n"
          + "From: Administrator <admin@example.com>\n"
          + "Date: %s\n"
          + "Subject: [PATCH] test commit\n"
          + "\n"
          + "Change-Id: %s\n"
          + "---\n"
          + "\n"
          + PATCH_FILE_ONLY;

  public interface Factory {
    PushOneCommit create(PersonIdent i, TestRepository<?> testRepo);

    PushOneCommit create(
        PersonIdent i, TestRepository<?> testRepo, @Assisted("changeId") String changeId);

    PushOneCommit create(
        PersonIdent i,
        TestRepository<?> testRepo,
        @Assisted("subject") String subject,
        @Assisted("fileName") String fileName,
        @Assisted("content") String content);

    PushOneCommit create(
        PersonIdent i,
        TestRepository<?> testRepo,
        @Assisted String subject,
        @Assisted Map<String, String> files);

    PushOneCommit create(
        PersonIdent i,
        TestRepository<?> testRepo,
        @Assisted("subject") String subject,
        @Assisted("fileName") String fileName,
        @Assisted("content") String content,
        @Assisted("changeId") String changeId);
  }

  public static class Tag {
    public String name;

    public Tag(String name) {
      this.name = name;
    }
  }

  public static class AnnotatedTag extends Tag {
    public String message;
    public PersonIdent tagger;

    public AnnotatedTag(String name, String message, PersonIdent tagger) {
      super(name);
      this.message = message;
      this.tagger = tagger;
    }
  }

  private static final AtomicInteger CHANGE_ID_COUNTER = new AtomicInteger();

  private static String nextChangeId() {
    // Tests use a variety of mechanisms for setting temporary timestamps, so we can't guarantee
    // that the PersonIdent (or any other field used by the Change-Id generator) for any two test
    // methods in the same acceptance test class are going to be different. But tests generally
    // assume that Change-Ids are unique unless otherwise specified. So, don't even bother trying to
    // reuse JGit's Change-Id generator, just do the simplest possible thing and convert a counter
    // to hex.
    return String.format("%040x", CHANGE_ID_COUNTER.incrementAndGet());
  }

  private final ChangeNotes.Factory notesFactory;
  private final ApprovalsUtil approvalsUtil;
  private final Provider<InternalChangeQuery> queryProvider;
  private final TestRepository<?> testRepo;

  private final String subject;
  private final Map<String, String> files;
  private String changeId;
  private Tag tag;
  private boolean force;
  private List<String> pushOptions;

  private final TestRepository<?>.CommitBuilder commitBuilder;

  @AssistedInject
  PushOneCommit(
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      Provider<InternalChangeQuery> queryProvider,
      @Assisted PersonIdent i,
      @Assisted TestRepository<?> testRepo)
      throws Exception {
    this(notesFactory, approvalsUtil, queryProvider, i, testRepo, SUBJECT, FILE_NAME, FILE_CONTENT);
  }

  @AssistedInject
  PushOneCommit(
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      Provider<InternalChangeQuery> queryProvider,
      @Assisted PersonIdent i,
      @Assisted TestRepository<?> testRepo,
      @Assisted("changeId") String changeId)
      throws Exception {
    this(
        notesFactory,
        approvalsUtil,
        queryProvider,
        i,
        testRepo,
        SUBJECT,
        FILE_NAME,
        FILE_CONTENT,
        changeId);
  }

  @AssistedInject
  PushOneCommit(
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      Provider<InternalChangeQuery> queryProvider,
      @Assisted PersonIdent i,
      @Assisted TestRepository<?> testRepo,
      @Assisted("subject") String subject,
      @Assisted("fileName") String fileName,
      @Assisted("content") String content)
      throws Exception {
    this(notesFactory, approvalsUtil, queryProvider, i, testRepo, subject, fileName, content, null);
  }

  @AssistedInject
  PushOneCommit(
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      Provider<InternalChangeQuery> queryProvider,
      @Assisted PersonIdent i,
      @Assisted TestRepository<?> testRepo,
      @Assisted String subject,
      @Assisted Map<String, String> files)
      throws Exception {
    this(notesFactory, approvalsUtil, queryProvider, i, testRepo, subject, files, null);
  }

  @AssistedInject
  PushOneCommit(
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      Provider<InternalChangeQuery> queryProvider,
      @Assisted PersonIdent i,
      @Assisted TestRepository<?> testRepo,
      @Assisted("subject") String subject,
      @Assisted("fileName") String fileName,
      @Assisted("content") String content,
      @Nullable @Assisted("changeId") String changeId)
      throws Exception {
    this(
        notesFactory,
        approvalsUtil,
        queryProvider,
        i,
        testRepo,
        subject,
        ImmutableMap.of(fileName, content),
        changeId);
  }

  private PushOneCommit(
      ChangeNotes.Factory notesFactory,
      ApprovalsUtil approvalsUtil,
      Provider<InternalChangeQuery> queryProvider,
      PersonIdent i,
      TestRepository<?> testRepo,
      String subject,
      Map<String, String> files,
      String changeId)
      throws Exception {
    this.testRepo = testRepo;
    this.notesFactory = notesFactory;
    this.approvalsUtil = approvalsUtil;
    this.queryProvider = queryProvider;
    this.subject = subject;
    this.files = files;
    this.changeId = changeId;
    if (changeId != null) {
      commitBuilder = testRepo.amendRef("HEAD").insertChangeId(changeId.substring(1));
    } else {
      commitBuilder = testRepo.branch("HEAD").commit().insertChangeId(nextChangeId());
    }
    commitBuilder.message(subject).author(i).committer(new PersonIdent(i, testRepo.getDate()));
  }

  public PushOneCommit setParents(List<RevCommit> parents) throws Exception {
    commitBuilder.noParents();
    for (RevCommit p : parents) {
      commitBuilder.parent(p);
    }
    return this;
  }

  public PushOneCommit setParent(RevCommit parent) throws Exception {
    commitBuilder.noParents();
    commitBuilder.parent(parent);
    return this;
  }

  public Result to(String ref) throws Exception {
    for (Map.Entry<String, String> e : files.entrySet()) {
      commitBuilder.add(e.getKey(), e.getValue());
    }
    return execute(ref);
  }

  public Result rm(String ref) throws Exception {
    for (String fileName : files.keySet()) {
      commitBuilder.rm(fileName);
    }
    return execute(ref);
  }

  public Result execute(String ref) throws Exception {
    RevCommit c = commitBuilder.create();
    if (changeId == null) {
      changeId = GitUtil.getChangeId(testRepo, c).get();
    }
    if (tag != null) {
      TagCommand tagCommand = testRepo.git().tag().setName(tag.name);
      if (tag instanceof AnnotatedTag) {
        AnnotatedTag annotatedTag = (AnnotatedTag) tag;
        tagCommand
            .setAnnotated(true)
            .setMessage(annotatedTag.message)
            .setTagger(annotatedTag.tagger);
      } else {
        tagCommand.setAnnotated(false);
      }
      tagCommand.call();
    }
    return new Result(ref, pushHead(testRepo, ref, tag != null, force, pushOptions), c, subject);
  }

  public void setTag(Tag tag) {
    this.tag = tag;
  }

  public void setForce(boolean force) {
    this.force = force;
  }

  public List<String> getPushOptions() {
    return pushOptions;
  }

  public void setPushOptions(List<String> pushOptions) {
    this.pushOptions = pushOptions;
  }

  public void noParents() {
    commitBuilder.noParents();
  }

  public class Result {
    private final String ref;
    private final PushResult result;
    private final RevCommit commit;
    private final String resSubj;

    private Result(String ref, PushResult resSubj, RevCommit commit, String subject) {
      this.ref = ref;
      this.result = resSubj;
      this.commit = commit;
      this.resSubj = subject;
    }

    public ChangeData getChange() {
      return Iterables.getOnlyElement(queryProvider.get().byKeyPrefix(changeId));
    }

    public PatchSet getPatchSet() {
      return getChange().currentPatchSet();
    }

    public PatchSet.Id getPatchSetId() {
      return getChange().change().currentPatchSetId();
    }

    public String getChangeId() {
      return changeId;
    }

    public RevCommit getCommit() {
      return commit;
    }

    public void assertPushOptions(List<String> pushOptions) {
      assertEquals(pushOptions, getPushOptions());
    }

    public void assertChange(
        Change.Status expectedStatus, String expectedTopic, TestAccount... expectedReviewers) {
      assertChange(
          expectedStatus, expectedTopic, Arrays.asList(expectedReviewers), ImmutableList.of());
    }

    public void assertChange(
        Change.Status expectedStatus,
        String expectedTopic,
        List<TestAccount> expectedReviewers,
        List<TestAccount> expectedCcs) {
      Change c = getChange().change();
      assertThat(c.getSubject()).isEqualTo(resSubj);
      assertThat(c.getStatus()).isEqualTo(expectedStatus);
      assertThat(Strings.emptyToNull(c.getTopic())).isEqualTo(expectedTopic);
      assertReviewers(c, ReviewerStateInternal.REVIEWER, expectedReviewers);
      assertReviewers(c, ReviewerStateInternal.CC, expectedCcs);
    }

    private void assertReviewers(
        Change c, ReviewerStateInternal state, List<TestAccount> expectedReviewers) {
      Iterable<Account.Id> actualIds =
          approvalsUtil.getReviewers(notesFactory.createChecked(c)).byState(state);
      assertThat(actualIds)
          .containsExactlyElementsIn(Sets.newHashSet(TestAccount.ids(expectedReviewers)));
    }

    public void assertOkStatus() {
      assertStatus(Status.OK, null);
    }

    public void assertErrorStatus(String expectedMessage) {
      assertStatus(Status.REJECTED_OTHER_REASON, expectedMessage);
    }

    public void assertErrorStatus() {
      RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
      assertThat(refUpdate).isNotNull();
      assertWithMessage(message(refUpdate))
          .that(refUpdate.getStatus())
          .isEqualTo(Status.REJECTED_OTHER_REASON);
    }

    private void assertStatus(Status expectedStatus, String expectedMessage) {
      RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
      assertThat(refUpdate).isNotNull();
      assertWithMessage(message(refUpdate)).that(refUpdate.getStatus()).isEqualTo(expectedStatus);
      if (expectedMessage == null) {
        assertThat(refUpdate.getMessage()).isNull();
      } else {
        assertThat(refUpdate.getMessage()).contains(expectedMessage);
      }
    }

    public void assertMessage(String expectedMessage) {
      RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
      assertThat(refUpdate).isNotNull();
      assertThat(message(refUpdate).toLowerCase()).contains(expectedMessage.toLowerCase());
    }

    public void assertNotMessage(String message) {
      RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
      assertThat(message(refUpdate).toLowerCase()).doesNotContain(message.toLowerCase());
    }

    public String getMessage() {
      RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
      assertThat(refUpdate).isNotNull();
      return message(refUpdate);
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
