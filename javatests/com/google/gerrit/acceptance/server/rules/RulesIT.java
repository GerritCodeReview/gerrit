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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.util.Collection;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/**
 * Tests the Prolog rules to make sure they work even when the change and account indexes are not
 * available.
 */
@NoHttpd
public class RulesIT extends AbstractDaemonTest {
  private static final String RULE_TEMPLATE =
      "submit_rule(submit(W)) :- \n" + "%s,\n" + "W = label('OK', ok(user(1000000))).";

  @Inject private ProjectOperations projectOperations;
  @Inject private SubmitRuleEvaluator.Factory evaluatorFactory;

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

  private SubmitRecord.Status statusForRule() throws Exception {
    String oldHead = projectOperations.project(project).getHead("master").name();
    PushOneCommit.Result result1 =
        pushFactory.create(user.newIdent(), testRepo).to("refs/for/master");
    testRepo.reset(oldHead);
    return getStatus(result1);
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

  private SubmitRecord.Status getStatus(PushOneCommit.Result result1) throws Exception {
    ChangeData cd = result1.getChange();

    Collection<SubmitRecord> records;
    try (AutoCloseable changeIndex = disableChangeIndex()) {
      try (AutoCloseable accountIndex = disableAccountIndex()) {
        SubmitRuleEvaluator ruleEvaluator = evaluatorFactory.create(SubmitRuleOptions.defaults());
        records = ruleEvaluator.evaluate(cd);
      }
    }

    assertThat(records).hasSize(1);
    SubmitRecord record = records.iterator().next();
    return record.status;
  }

  private void modifySubmitRules(String ruleTested) throws Exception {
    String newContent = String.format(RULE_TEMPLATE, ruleTested);

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
