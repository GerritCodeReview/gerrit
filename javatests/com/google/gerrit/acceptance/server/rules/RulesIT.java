// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.rules;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.change.IndexOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/**
 * Tests the Prolog rules to make sure they work even when the change and account indexes are not
 * available.
 */
@NoHttpd
public class RulesIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private SubmitRuleEvaluator.Factory evaluatorFactory;
  @Inject private IndexOperations.Change changeIndexOperations;
  @Inject private IndexOperations.Account accountIndexOperations;

  @Test
  public void testUnresolvedCommentsCountPredicate() throws Exception {
    modifySubmitRules("gerrit:unresolved_comments_count(0)");
    assertThat(statusForRule()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testUploaderPredicate() throws Exception {
    modifySubmitRules("gerrit:uploader(U)");
    assertThat(statusForRule()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testUnresolvedCommentsCount() throws Exception {
    modifySubmitRules("gerrit:commit_message_matches('.*')");
    assertThat(statusForRule()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testUserPredicate() throws Exception {
    modifySubmitRules(
        String.format(
            "gerrit:commit_author(user(%d), '%s', '%s')",
            user.id().get(), user.fullName(), user.email()));
    assertThat(statusForRule()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitAuthorPredicate() throws Exception {
    modifySubmitRules("gerrit:commit_author(Id)");
    assertThat(statusForRule()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testFileNamesPredicateWithANewFile() throws Exception {
    modifySubmitRules("gerrit:files([file('a.txt', 'A', 'REGULAR')])");
    assertThat(statusForRule()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testFileNamesPredicateWithADeletedFile() throws Exception {
    modifySubmitRules("gerrit:files([file('a.txt', 'D', 'REGULAR')])");
    assertThat(statusForRuleRemoveFile()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_pass() throws Exception {
    modifySubmitRules("gerrit:commit_delta('file1\\.txt')");
    assertThat(statusForRuleAddFile("file1.txt")).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_fail() throws Exception {
    modifySubmitRules("gerrit:commit_delta('no such file')");
    assertThat(statusForRuleAddFile("file1.txt")).isEqualTo(SubmitRecord.Status.RULE_ERROR);
  }

  @Test
  public void testCommitDelta_addOwners_pass() throws Exception {
    modifySubmitRules("gerrit:commit_delta('OWNERS', add, _, _)");
    assertThat(statusForRuleAddFile("foo/OWNERS")).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_addOwners_fail() throws Exception {
    modifySubmitRules("gerrit:commit_delta('OWNERS', add, _, _)");
    assertThat(statusForRuleAddFile("foobar")).isEqualTo(SubmitRecord.Status.RULE_ERROR);
  }

  @Test
  public void testCommitDelta_regexp() throws Exception {
    modifySubmitRules("gerrit:commit_delta('.*')");
    assertThat(statusForRuleAddFile("foo/bar")).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_add_provideNewName() throws Exception {
    modifySubmitRules("gerrit:commit_delta('.*', _, 'foo')");
    assertThat(statusForRuleAddFile("foo")).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_modify_provideNewName() throws Exception {
    modifySubmitRules("gerrit:commit_delta('.*', _, 'a.txt')");
    assertThat(statusForRuleModifyFile()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_delete_provideNewName() throws Exception {
    modifySubmitRules("gerrit:commit_delta('.*', _, 'a.txt')");
    assertThat(statusForRuleRemoveFile()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_rename_provideOldName() throws Exception {
    modifySubmitRules("gerrit:commit_delta('.*', _, 'a.txt')");
    assertThat(statusForRuleRenamedFile()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_rename_provideNewName() throws Exception {
    modifySubmitRules("gerrit:commit_delta('.*', _, 'b.txt')");
    assertThat(statusForRuleRenamedFile()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_rename_matchOldName() throws Exception {
    modifySubmitRules("gerrit:commit_delta('a\\.txt')");
    assertThat(statusForRuleRenamedFile()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void testCommitDelta_rename_matchNewName() throws Exception {
    modifySubmitRules("gerrit:commit_delta('b\\.txt')");
    assertThat(statusForRuleRenamedFile()).isEqualTo(SubmitRecord.Status.OK);
  }

  @Test
  public void typeError() throws Exception {
    modifySubmitRules("user(1000000)."); // the trailing '.' triggers a type error
    assertThat(statusForRuleAddFile("foo")).isEqualTo(SubmitRecord.Status.RULE_ERROR);
  }

  private SubmitRecord.Status statusForRule() throws Exception {
    String oldHead = projectOperations.project(project).getHead("master").name();
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), testRepo).to("refs/for/master");
    testRepo.reset(oldHead);
    return getStatus(result);
  }

  private SubmitRecord.Status statusForRuleAddFile(String... filenames) throws Exception {
    Map<String, String> fileToContentMap =
        Arrays.stream(filenames).collect(ImmutableMap.toImmutableMap(f -> f, f -> "file content"));
    String oldHead = projectOperations.project(project).getHead("master").name();
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "subject", fileToContentMap);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    testRepo.reset(oldHead);
    return getStatus(result);
  }

  private SubmitRecord.Status statusForRuleModifyFile() throws Exception {
    String oldHead = projectOperations.project(project).getHead("master").name();

    // create a.txt
    commitBuilder().add(PushOneCommit.FILE_NAME, "Hey, it's me!").message("subject").create();
    pushHead(testRepo, "refs/heads/master", false);

    PushOneCommit.Result result =
        pushFactory
            .create(
                user.newIdent(),
                testRepo,
                "subject",
                ImmutableMap.of(PushOneCommit.FILE_NAME, "I've changed!"))
            .rmFile(PushOneCommit.FILE_NAME)
            .to("refs/for/master");
    testRepo.reset(oldHead);
    return getStatus(result);
  }

  private SubmitRecord.Status statusForRuleRemoveFile() throws Exception {
    String oldHead = projectOperations.project(project).getHead("master").name();
    // create a.txt
    commitBuilder().add("a.txt", "4").message("subject").create();
    pushHead(testRepo, "refs/heads/master", false);

    // This implictly removes a.txt
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), testRepo).rm("refs/for/master");
    testRepo.reset(oldHead);
    return getStatus(result);
  }

  private SubmitRecord.Status statusForRuleRenamedFile() throws Exception {
    String oldHead = projectOperations.project(project).getHead("master").name();

    // create a.txt
    commitBuilder().add(PushOneCommit.FILE_NAME, "Hey, it's me!").message("subject").create();
    pushHead(testRepo, "refs/heads/master", false);

    PushOneCommit.Result result =
        pushFactory
            .create(user.newIdent(), testRepo, "subject", ImmutableMap.of("b.txt", "Hey, it's me!"))
            .rmFile(PushOneCommit.FILE_NAME)
            .to("refs/for/master");
    testRepo.reset(oldHead);
    return getStatus(result);
  }

  private SubmitRecord.Status getStatus(PushOneCommit.Result result) throws Exception {
    ChangeData cd = result.getChange();

    Collection<SubmitRecord> records;
    try (AutoCloseable ignored1 = changeIndexOperations.disableReadsAndWrites();
        AutoCloseable ignored2 = accountIndexOperations.disableReadsAndWrites()) {
      SubmitRuleEvaluator ruleEvaluator = evaluatorFactory.create(SubmitRuleOptions.defaults());
      records = ruleEvaluator.evaluate(cd);
    }

    assertThat(records).hasSize(1);
    SubmitRecord record = records.iterator().next();
    return record.status;
  }

  private void modifySubmitRules(String ruleTested) throws Exception {
    String newContent =
        String.format(
            "submit_rule(submit(W)) :- \n%s,\nW = label('OK', ok(user(1000000))).", ruleTested);

    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .author(admin.newIdent())
          .committer(admin.newIdent())
          .add("rules.pl", newContent)
          .message("Modify rules.pl")
          .create();
    }
    projectCache.evict(project);
  }
}
