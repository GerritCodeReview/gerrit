// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.SubmitType.CHERRY_PICK;
import static com.google.gerrit.extensions.client.SubmitType.FAST_FORWARD_ONLY;
import static com.google.gerrit.extensions.client.SubmitType.MERGE_ALWAYS;
import static com.google.gerrit.extensions.client.SubmitType.MERGE_IF_NECESSARY;
import static com.google.gerrit.extensions.client.SubmitType.REBASE_ALWAYS;
import static com.google.gerrit.extensions.client.SubmitType.REBASE_IF_NECESSARY;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.testutil.ConfigSuite;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class SubmitTypeRuleIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  private class RulesPl extends VersionedMetaData {
    private static final String FILENAME = "rules.pl";

    private String rule;

    @Override
    protected String getRefName() {
      return RefNames.REFS_CONFIG;
    }

    @Override
    protected void onLoad() throws IOException, ConfigInvalidException {
      rule = readUTF8(FILENAME);
    }

    @Override
    protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
      TestSubmitRuleInput in = new TestSubmitRuleInput();
      in.rule = rule;
      try {
        gApi.changes().id(testChangeId.get()).current().testSubmitType(in);
      } catch (RestApiException e) {
        throw new ConfigInvalidException("Invalid submit type rule", e);
      }

      saveUTF8(FILENAME, rule);
      return true;
    }
  }

  private AtomicInteger fileCounter;
  private Change.Id testChangeId;

  @Before
  public void setUp() throws Exception {
    fileCounter = new AtomicInteger();
    gApi.projects().name(project.get()).branch("test").create(new BranchInput());
    testChangeId = createChange("test", "test change").getChange().getId();
  }

  private void setRulesPl(String rule) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      RulesPl r = new RulesPl();
      r.load(md);
      r.rule = rule;
      r.commit(md);
    }
  }

  private static final String SUBMIT_TYPE_FROM_SUBJECT =
      "submit_type(fast_forward_only) :-"
          + "gerrit:commit_message(M),"
          + "regex_matches('.*FAST_FORWARD_ONLY.*', M),"
          + "!.\n"
          + "submit_type(merge_if_necessary) :-"
          + "gerrit:commit_message(M),"
          + "regex_matches('.*MERGE_IF_NECESSARY.*', M),"
          + "!.\n"
          + "submit_type(rebase_if_necessary) :-"
          + "gerrit:commit_message(M),"
          + "regex_matches('.*REBASE_IF_NECESSARY.*', M),"
          + "!.\n"
          + "submit_type(rebase_always) :-"
          + "gerrit:commit_message(M),"
          + "regex_matches('.*REBASE_ALWAYS.*', M),"
          + "!.\n"
          + "submit_type(merge_always) :-"
          + "gerrit:commit_message(M),"
          + "regex_matches('.*MERGE_ALWAYS.*', M),"
          + "!.\n"
          + "submit_type(cherry_pick) :-"
          + "gerrit:commit_message(M),"
          + "regex_matches('.*CHERRY_PICK.*', M),"
          + "!.\n"
          + "submit_type(T) :- gerrit:project_default_submit_type(T).";

  private PushOneCommit.Result createChange(String dest, String subject) throws Exception {
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            subject,
            "file" + fileCounter.incrementAndGet(),
            PushOneCommit.FILE_CONTENT);
    PushOneCommit.Result r = push.to("refs/for/" + dest);
    r.assertOkStatus();
    return r;
  }

  @Test
  public void unconditionalCherryPick() throws Exception {
    PushOneCommit.Result r = createChange();
    assertSubmitType(MERGE_IF_NECESSARY, r.getChangeId());
    setRulesPl("submit_type(cherry_pick).");
    assertSubmitType(CHERRY_PICK, r.getChangeId());
  }

  @Test
  public void submitTypeFromSubject() throws Exception {
    PushOneCommit.Result r1 = createChange("master", "Default 1");
    PushOneCommit.Result r2 = createChange("master", "FAST_FORWARD_ONLY 2");
    PushOneCommit.Result r3 = createChange("master", "MERGE_IF_NECESSARY 3");
    PushOneCommit.Result r4 = createChange("master", "REBASE_IF_NECESSARY 4");
    PushOneCommit.Result r5 = createChange("master", "REBASE_ALWAYS 5");
    PushOneCommit.Result r6 = createChange("master", "MERGE_ALWAYS 6");
    PushOneCommit.Result r7 = createChange("master", "CHERRY_PICK 7");

    assertSubmitType(MERGE_IF_NECESSARY, r1.getChangeId());
    assertSubmitType(MERGE_IF_NECESSARY, r2.getChangeId());
    assertSubmitType(MERGE_IF_NECESSARY, r3.getChangeId());
    assertSubmitType(MERGE_IF_NECESSARY, r4.getChangeId());
    assertSubmitType(MERGE_IF_NECESSARY, r5.getChangeId());
    assertSubmitType(MERGE_IF_NECESSARY, r6.getChangeId());
    assertSubmitType(MERGE_IF_NECESSARY, r7.getChangeId());

    setRulesPl(SUBMIT_TYPE_FROM_SUBJECT);

    assertSubmitType(MERGE_IF_NECESSARY, r1.getChangeId());
    assertSubmitType(FAST_FORWARD_ONLY, r2.getChangeId());
    assertSubmitType(MERGE_IF_NECESSARY, r3.getChangeId());
    assertSubmitType(REBASE_IF_NECESSARY, r4.getChangeId());
    assertSubmitType(REBASE_ALWAYS, r5.getChangeId());
    assertSubmitType(MERGE_ALWAYS, r6.getChangeId());
    assertSubmitType(CHERRY_PICK, r7.getChangeId());
  }

  @Test
  public void submitTypeIsUsedForSubmit() throws Exception {
    setRulesPl(SUBMIT_TYPE_FROM_SUBJECT);

    PushOneCommit.Result r = createChange("master", "CHERRY_PICK 1");

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();

    List<RevCommit> log = log("master", 1);
    assertThat(log.get(0).getShortMessage()).isEqualTo("CHERRY_PICK 1");
    assertThat(log.get(0).name()).isNotEqualTo(r.getCommit().name());
    assertThat(log.get(0).getFullMessage()).contains("Change-Id: " + r.getChangeId());
    assertThat(log.get(0).getFullMessage()).contains("Reviewed-on: ");
  }

  @Test
  public void mixingSubmitTypesAcrossBranchesSucceeds() throws Exception {
    setRulesPl(SUBMIT_TYPE_FROM_SUBJECT);

    PushOneCommit.Result r1 = createChange("master", "MERGE_IF_NECESSARY 1");

    RevCommit initialCommit = r1.getCommit().getParent(0);
    BranchInput bin = new BranchInput();
    bin.revision = initialCommit.name();
    gApi.projects().name(project.get()).branch("branch").create(bin);

    testRepo.reset(initialCommit);
    PushOneCommit.Result r2 = createChange("branch", "MERGE_ALWAYS 1");

    gApi.changes().id(r1.getChangeId()).topic(name("topic"));
    gApi.changes().id(r1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r2.getChangeId()).topic(name("topic"));
    gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r2.getChangeId()).current().submit();

    assertThat(log("master", 1).get(0).name()).isEqualTo(r1.getCommit().name());

    List<RevCommit> branchLog = log("branch", 1);
    assertThat(branchLog.get(0).getParents()).hasLength(2);
    assertThat(branchLog.get(0).getParent(1).name()).isEqualTo(r2.getCommit().name());
  }

  @Test
  public void mixingSubmitTypesOnOneBranchFails() throws Exception {
    setRulesPl(SUBMIT_TYPE_FROM_SUBJECT);

    PushOneCommit.Result r1 = createChange("master", "CHERRY_PICK 1");
    PushOneCommit.Result r2 = createChange("master", "MERGE_IF_NECESSARY 2");

    gApi.changes().id(r1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r2.getChangeId()).current().review(ReviewInput.approve());

    try {
      gApi.changes().id(r2.getChangeId()).current().submit();
      fail("Expected ResourceConflictException");
    } catch (ResourceConflictException e) {
      assertThat(e)
          .hasMessage(
              "Failed to submit 2 changes due to the following problems:\n"
                  + "Change "
                  + r1.getChange().getId()
                  + ": Change has submit type "
                  + "CHERRY_PICK, but previously chose submit type MERGE_IF_NECESSARY "
                  + "from change "
                  + r2.getChange().getId()
                  + " in the same batch");
    }
  }

  private List<RevCommit> log(String commitish, int n) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        Git git = new Git(repo)) {
      ObjectId id = repo.resolve(commitish);
      assertThat(id).isNotNull();
      return ImmutableList.copyOf(git.log().add(id).setMaxCount(n).call());
    }
  }

  private void assertSubmitType(SubmitType expected, String id) throws Exception {
    assertThat(gApi.changes().id(id).current().submitType()).isEqualTo(expected);
  }
}
