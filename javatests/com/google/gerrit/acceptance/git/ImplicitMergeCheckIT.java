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

package com.google.gerrit.acceptance.git;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.server.util.CommitMessageUtil.generateChangeId;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.server.submit.IntegrationConflictException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.eclipse.jetty.util.Uptime.Impl;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

public class ImplicitMergeCheckIT extends AbstractDaemonTest {

  @Override
  protected boolean enableExperimentsRejectImplicitMergesOnMerge() {
    // Tests uses own experiment setup.
    return false;
  }

  @Test
  public void implicitMergeViaFastForward() throws Exception {
    setRejectImplicitMerges();

    pushHead(testRepo, "refs/heads/stable", false);
    PushOneCommit.Result m = push("refs/heads/master", "0", "file", "0");
    PushOneCommit.Result c = push("refs/for/stable", "1", "file", "1");

    c.assertMessage(implicitMergeOf(m.getCommit()));
    c.assertErrorStatus();
  }

  @Test
  public void implicitMergeViaRealMerge() throws Exception {
    setRejectImplicitMerges();

    ObjectId base = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/stable", "0", "f", "0");
    testRepo.reset(base);
    PushOneCommit.Result m = push("refs/heads/master", "1", "f", "1");
    PushOneCommit.Result c = push("refs/for/stable", "2", "f", "2");

    c.assertMessage(implicitMergeOf(m.getCommit()));
    c.assertErrorStatus();
  }

  @Test
  public void implicitMergeCheckOff() throws Exception {
    ObjectId base = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/stable", "0", "f", "0");
    testRepo.reset(base);
    PushOneCommit.Result m = push("refs/heads/master", "1", "f", "1");
    PushOneCommit.Result c = push("refs/for/stable", "2", "f", "2");

    assertThat(c.getMessage().toLowerCase(Locale.US))
        .doesNotContain(implicitMergeOf(m.getCommit()));
  }

  @Test
  public void notImplicitMerge_noWarning() throws Exception {
    setRejectImplicitMerges();

    ObjectId base = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/stable", "0", "f", "0");
    testRepo.reset(base);
    PushOneCommit.Result m = push("refs/heads/master", "1", "f", "1");
    PushOneCommit.Result c = push("refs/for/master", "2", "f", "2");

    assertThat(c.getMessage().toLowerCase(Locale.US))
        .doesNotContain(implicitMergeOf(m.getCommit()));
  }

  /**
   * Creates 2 changes for tests.
   *
   * <p>Returns arrays of the size 2 with changes ids.
   *
   * The changes forms the following tree:
   * change[1] (target - stable, merges stable branch and change[0])
   * |         \
   * |         change[0] (target - stable)
   * |          |
   * stable     master
   *
   * <p>Here change[1] is an expicit merge and can be submitted to stable. The change[0] if
   * submitted alone implicitly merges master into a stable branch.
   */
  ImplicitAndExplicitMerges prepareChangeWithExplicitAndImplicitMerges() throws Exception {
    setRejectImplicitMerges();
    ObjectId base = repo().exactRef("HEAD").getObjectId();
    RevCommit stableBranchCommit = push("refs/heads/stable", "Stable branch initial commit", "stable_file", "original_stable_content").getCommit();
    testRepo.reset(base);
    PushOneCommit.Result m = push("refs/heads/master", "Master branch initial commit", "master_file", "original_master_content");
    String changeId = generateChangeId().name();
    RevCommit revCommit =
        testRepo
            .branch("HEAD")
            .commit()
            .insertChangeId(changeId)
            .message("Change on top of master branch")
            .add("new_master_file", "new_master_file_content")
            .create();
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, "Explicit merge change", "new_stable_file", "explicit_merge_content");
    push.setParents(ImmutableList.of(stableBranchCommit, revCommit));
    PushOneCommit.Result c = push.to("refs/for/stable");
    assertThat(c.getMessage().toLowerCase(Locale.US))
        .doesNotContain(implicitMergeOf(m.getCommit()));
    gApi.changes().id("I" + changeId).current().review(ReviewInput.approve());
    gApi.changes().id(c.getChangeId()).current().review(ReviewInput.approve());
    return ImplicitAndExplicitMerges.create("I" + changeId, c.getChangeId(), "refs/for/stable");
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
        "GerritBackendFeature__always_reject_implicit_merges_on_merge"
      })
  public void alwaysRejectOnMerge_rejectImplicitMergeOnSubmit() throws Exception {
    ImplicitAndExplicitMerges changes = prepareChangeWithExplicitAndImplicitMerges();
    setRejectImplicitMerges(/*reject=*/ true);
    IntegrationConflictException e =
        assertThrows(
            IntegrationConflictException.class,
            () -> gApi.changes().id(changes.implicitMergeChangeId()).current().submit());
    assertThat(e.getMessage().toLowerCase()).contains("implicit merge detected");
    ChangeInfo ci = gApi.changes().id(changes.implicitMergeChangeId()).info();
    assertThat(ci.submitted).isNull();

    setRejectImplicitMerges(/*reject=*/ false);
    e =
        assertThrows(
            IntegrationConflictException.class,
            () -> gApi.changes().id(changes.implicitMergeChangeId()).current().submit());
    assertThat(e.getMessage().toLowerCase()).contains("implicit merge detected");
    ci = gApi.changes().id(changes.implicitMergeChangeId()).info();
    assertThat(ci.submitted).isNull();
  }

  @Test
//  @GerritConfig(
//      name = "experiments.enabled",
//      values = {
//          "GerritBackendFeature__check_implicit_merges_on_merge",
//          "GerritBackendFeature__reject_implicit_merges_on_merge",
//          "GerritBackendFeature__always_reject_implicit_merges_on_merge"
//      })
  public void alwaysRejectOnMerge_cherryPick() throws Exception {
//    setRejectImplicitMerges();
    setSubmitType(SubmitType.CHERRY_PICK);
    ImplicitAndExplicitMerges changes = prepareChangeWithExplicitAndImplicitMerges();
    //String originalRevision = gApi.projects().name(project.get()).branch("refs/heads/stable").get().revision;
    ChangeInfo ci = gApi.changes().id(changes.implicitMergeChangeId()).current().submit();
    assertThat(ci.branch).isEqualTo("stable");
    assertThat(ci.project).isEqualTo(project.get());
    assertThat(ci.status).isEqualTo(ChangeStatus.MERGED);
    getRemoteBranchPaths("stable");
//    String revision = gApi.projects().name(project.get()).branch("refs/heads/stable").get().revision;
//    assertThat(revision).isNotEqualTo(originalRevision);
//    testRepo.git().fetch().setRemote("origin").call();
//    RevTree revTree = testRepo.getRepository().parseCommit(testRepo.getRepository().resolve(revision)).getTree();
//    byte[] data = testRepo.getRepository().open(testRepo.get(revTree, "stable_file")).getCachedBytes(Integer.MAX_VALUE);
//    assertThat(RawParseUtils.decode(data)).isEqualTo("0");
//    byte[] data2 = testRepo.getRepository().open(testRepo.get(revTree, "a")).getCachedBytes(Integer.MAX_VALUE);
//    assertThat(RawParseUtils.decode(data2)).isEqualTo("0");

  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
      })
  public void alwaysRejectOnMerge_rejectImplicitMergeOnSubmit_canSubmitExplicitMerge()
      throws Exception {
    ImplicitAndExplicitMerges merges = prepareChangeWithExplicitAndImplicitMerges();
    setRejectImplicitMerges(/*reject=*/ true);
    gApi.changes().id(merges.explicitMergeChangeId()).current().submit();

    ChangeInfo ci = gApi.changes().id(merges.explicitMergeChangeId()).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId).isEqualTo(atrScope.get().getUser().getAccountId().get());
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
        "GerritBackendFeature__reject_implicit_merges_on_merge",
      })
  public void doNotRejectOnMerge_submitted() throws Exception {
    ImplicitAndExplicitMerges merges = prepareChangeWithExplicitAndImplicitMerges();
    setRejectImplicitMerges(/*reject=*/ true);
    IntegrationConflictException e =
        assertThrows(
            IntegrationConflictException.class,
            () -> gApi.changes().id(merges.implicitMergeChangeId()).current().submit());
    assertThat(e.getMessage().toLowerCase()).contains("implicit merge detected");
    ChangeInfo ci = gApi.changes().id(merges.implicitMergeChangeId()).info();
    assertThat(ci.submitted).isNull();

    setRejectImplicitMerges(/*reject=*/ false);
    gApi.changes().id(merges.implicitMergeChangeId()).current().submit();

    ci = gApi.changes().id(merges.implicitMergeChangeId()).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId).isEqualTo(atrScope.get().getUser().getAccountId().get());
  }

  @Test
  @GerritConfig(
      name = "experiments.enabled",
      values = {
        "GerritBackendFeature__check_implicit_merges_on_merge",
      })
  public void checkOnly_submitted() throws Exception {
    ImplicitAndExplicitMerges changes = prepareChangeWithExplicitAndImplicitMerges();
    setRejectImplicitMerges(/*reject=*/ true);
    gApi.changes().id(changes.implicitMergeChangeId()).current().submit();

    ChangeInfo ci = gApi.changes().id(changes.implicitMergeChangeId()).info();
    assertThat(ci.submitted).isNotNull();
    assertThat(ci.submitter).isNotNull();
    assertThat(ci.submitter._accountId).isEqualTo(atrScope.get().getUser().getAccountId().get());
  }

  private String implicitMergeOf(ObjectId commit) throws Exception {
    return "implicit merge of "
        + ObjectIds.abbreviateName(commit, testRepo.getRevWalk().getObjectReader());
  }

  private void setRejectImplicitMerges() throws Exception {
    setRejectImplicitMerges(/*reject=*/ true);
  }

  private void setRejectImplicitMerges(boolean reject) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              p ->
                  p.setBooleanConfig(
                      BooleanProjectConfig.REJECT_IMPLICIT_MERGES,
                      reject ? InheritableBoolean.TRUE : InheritableBoolean.FALSE));
      u.save();
    }
  }

  private void setSubmitType(SubmitType submitType) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              p ->
                  p.setSubmitType(submitType));
      u.save();
    }
  }

  private PushOneCommit.Result push(String ref, String subject, String fileName, String content)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content);
    return push.to(ref);
  }

  @AutoValue
  abstract static class ImplicitAndExplicitMerges {
    public static ImplicitAndExplicitMerges create (String implicitMergeChangeId, String explicitMegeChangeId, String targetBranch) {
      return new AutoValue_ImplicitMergeCheckIT_ImplicitAndExplicitMerges(implicitMergeChangeId, explicitMegeChangeId, targetBranch);
    }
    public abstract String implicitMergeChangeId();
    public abstract String explicitMergeChangeId();
    public abstract String targetBranch();
  }
  ImmutableList<String> getRemoteBranchPaths(String targetBranch) throws Exception {
    testRepo.git().fetch().setRemote("origin").call();
    testRepo.git().checkout().setName("origin/" + targetBranch).call();
    return Arrays.stream(testRepo.getRepository().readDirCache().getEntriesWithin("")).map(
        DirCacheEntry::getPathString).collect(toImmutableList());
  }
}
