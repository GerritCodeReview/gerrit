// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.AgreementInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AgreementsIT extends AbstractDaemonTest {
  private ContributorAgreement caAutoVerify;
  private ContributorAgreement caNoAutoVerify;
  @Inject private GroupOperations groupOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  protected void setUseContributorAgreements(InheritableBoolean value) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig config = projectConfigFactory.read(md);
      config.getProject().setBooleanConfig(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS, value);
      config.commit(md);
      projectCache.evict(config.getProject());
    }
  }

  protected ContributorAgreement configureContributorAgreement(boolean autoVerify)
      throws Exception {
    ContributorAgreement ca;
    String name = autoVerify ? "cla-test-group" : "cla-test-no-auto-verify-group";
    AccountGroup.UUID g = groupOperations.newGroup().name(name).create();
    GroupApi groupApi = gApi.groups().id(g.get());
    groupApi.description("CLA test group");
    InternalGroup caGroup = group(AccountGroup.uuid(groupApi.detail().id));
    GroupReference groupRef = new GroupReference(caGroup.getGroupUUID(), caGroup.getName());
    PermissionRule rule = new PermissionRule(groupRef);
    rule.setAction(PermissionRule.Action.ALLOW);
    if (autoVerify) {
      ca = new ContributorAgreement("cla-test");
      ca.setAutoVerify(groupRef);
      ca.setAccepted(ImmutableList.of(rule));
    } else {
      ca = new ContributorAgreement("cla-test-no-auto-verify");
    }
    ca.setDescription("description");
    ca.setAgreementUrl("agreement-url");
    ca.setAccepted(ImmutableList.of(rule));
    ca.setExcludeProjectsRegexes(ImmutableList.of("ExcludedProject"));

    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().replace(ca);
      u.save();
      return ca;
    }
  }

  @ConfigSuite.Config
  public static Config enableAgreementsConfig() {
    Config cfg = new Config();
    cfg.setBoolean("auth", null, "contributorAgreements", true);
    return cfg;
  }

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Before
  public void setUp() throws Exception {
    caAutoVerify = configureContributorAgreement(true);
    caNoAutoVerify = configureContributorAgreement(false);
    requestScopeOperations.setApiUser(user.id());
  }

  @Test
  public void getAvailableAgreements() throws Exception {
    ServerInfo info = gApi.config().server().getInfo();
    if (isContributorAgreementsEnabled()) {
      assertThat(info.auth.useContributorAgreements).isTrue();
      assertThat(info.auth.contributorAgreements).hasSize(2);
      assertAgreement(info.auth.contributorAgreements.get(0), caAutoVerify);
      assertAgreement(info.auth.contributorAgreements.get(1), caNoAutoVerify);
    } else {
      assertThat(info.auth.useContributorAgreements).isNull();
      assertThat(info.auth.contributorAgreements).isNull();
    }
  }

  @Test
  public void signNonExistingAgreement() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("contributor agreement not found");
    gApi.accounts().self().signAgreement("does-not-exist");
  }

  @Test
  public void signAgreementNoAutoVerify() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();
    exception.expect(BadRequestException.class);
    exception.expectMessage("cannot enter a non-autoVerify agreement");
    gApi.accounts().self().signAgreement(caNoAutoVerify.getName());
  }

  @Test
  public void signAgreement() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // List of agreements is initially empty
    List<AgreementInfo> result = gApi.accounts().self().listAgreements();
    assertThat(result).isEmpty();

    // Sign the agreement
    gApi.accounts().self().signAgreement(caAutoVerify.getName());

    // Explicitly reset the user to force a new request context
    requestScopeOperations.setApiUser(user.id());

    // Verify that the agreement was signed
    result = gApi.accounts().self().listAgreements();
    assertThat(result).hasSize(1);
    AgreementInfo info = result.get(0);
    assertAgreement(info, caAutoVerify);

    // Signing the same agreement again has no effect
    gApi.accounts().self().signAgreement(caAutoVerify.getName());
    result = gApi.accounts().self().listAgreements();
    assertThat(result).hasSize(1);
  }

  @Test
  public void signAgreementAsOtherUser() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();
    assertThat(gApi.accounts().self().get().name).isNotEqualTo("admin");
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to enter contributor agreement");
    gApi.accounts().id("admin").signAgreement(caAutoVerify.getName());
  }

  @Test
  public void signAgreementAnonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    exception.expect(AuthException.class);
    exception.expectMessage("Authentication required");
    gApi.accounts().self().signAgreement(caAutoVerify.getName());
  }

  @Test
  public void agreementsDisabledSign() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isFalse();
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("contributor agreements disabled");
    gApi.accounts().self().signAgreement(caAutoVerify.getName());
  }

  @Test
  public void agreementsDisabledList() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isFalse();
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("contributor agreements disabled");
    gApi.accounts().self().listAgreements();
  }

  @Test
  public void revertChangeWithoutCLA() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // Create a change succeeds when agreement is not required
    setUseContributorAgreements(InheritableBoolean.FALSE);
    ChangeInfo change = gApi.changes().create(newChangeInput()).get();

    // Approve and submit it
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(change.changeId).current().review(ReviewInput.approve());
    gApi.changes().id(change.changeId).current().submit(new SubmitInput());

    // Revert is not allowed when CLA is required but not signed
    requestScopeOperations.setApiUser(user.id());
    setUseContributorAgreements(InheritableBoolean.TRUE);
    exception.expect(AuthException.class);
    exception.expectMessage("Contributor Agreement");
    gApi.changes().id(change.changeId).revert();
  }

  @Test
  public void revertExcludedProjectChangeWithoutCLA() throws Exception {
    // Contributor agreements configured with excludeProjects = ExcludedProject
    // in AbstractDaemonTest.configureContributorAgreement(...)
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // Create a change succeeds when agreement is not required
    setUseContributorAgreements(InheritableBoolean.FALSE);
    // Project name includes test method name which contains ExcludedProject
    ChangeInfo change = gApi.changes().create(newChangeInput()).get();

    // Approve and submit it
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(change.changeId).current().review(ReviewInput.approve());
    gApi.changes().id(change.changeId).current().submit(new SubmitInput());

    // Revert in excluded project is allowed even when CLA is required but not signed
    requestScopeOperations.setApiUser(user.id());
    setUseContributorAgreements(InheritableBoolean.TRUE);
    gApi.changes().id(change.changeId).revert();
  }

  @Test
  public void cherrypickChangeWithoutCLA() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // Create a new branch
    requestScopeOperations.setApiUser(admin.id());
    BranchInfo dest =
        gApi.projects()
            .name(project.get())
            .branch("cherry-pick-to")
            .create(new BranchInput())
            .get();

    // Create a change succeeds when agreement is not required
    setUseContributorAgreements(InheritableBoolean.FALSE);
    ChangeInfo change = gApi.changes().create(newChangeInput()).get();

    // Approve and submit it
    gApi.changes().id(change.changeId).current().review(ReviewInput.approve());
    gApi.changes().id(change.changeId).current().submit(new SubmitInput());

    // Cherry-pick is not allowed when CLA is required but not signed
    requestScopeOperations.setApiUser(user.id());
    setUseContributorAgreements(InheritableBoolean.TRUE);
    CherryPickInput in = new CherryPickInput();
    in.destination = dest.ref;
    in.message = change.subject;
    exception.expect(AuthException.class);
    exception.expectMessage("Contributor Agreement");
    gApi.changes().id(change.changeId).current().cherryPick(in);
  }

  @Test
  public void createChangeRespectsCLA() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // Create a change succeeds when agreement is not required
    setUseContributorAgreements(InheritableBoolean.FALSE);
    gApi.changes().create(newChangeInput());

    // Create a change is not allowed when CLA is required but not signed
    setUseContributorAgreements(InheritableBoolean.TRUE);
    try {
      gApi.changes().create(newChangeInput());
      fail("Expected AuthException");
    } catch (AuthException e) {
      assertThat(e.getMessage()).contains("Contributor Agreement");
    }

    // Sign the agreement
    gApi.accounts().self().signAgreement(caAutoVerify.getName());

    // Explicitly reset the user to force a new request context
    requestScopeOperations.setApiUser(user.id());

    // Create a change succeeds after signing the agreement
    gApi.changes().create(newChangeInput());
  }

  @Test
  public void createExcludedProjectChangeIgnoresCLA() throws Exception {
    // Contributor agreements configured with excludeProjects = ExcludedProject
    // in AbstractDaemonTest.configureContributorAgreement(...)
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // Create a change in excluded project is allowed even when CLA is required but not signed.
    setUseContributorAgreements(InheritableBoolean.TRUE);
    gApi.changes().create(newChangeInput());
  }

  private void assertAgreement(AgreementInfo info, ContributorAgreement ca) {
    assertThat(info.name).isEqualTo(ca.getName());
    assertThat(info.description).isEqualTo(ca.getDescription());
    assertThat(info.url).isEqualTo(ca.getAgreementUrl());
    if (ca.getAutoVerify() != null) {
      assertThat(info.autoVerifyGroup.name).isEqualTo(ca.getAutoVerify().getName());
    } else {
      assertThat(info.autoVerifyGroup).isNull();
    }
  }

  private ChangeInput newChangeInput() {
    ChangeInput in = new ChangeInput();
    in.branch = "master";
    in.subject = "test";
    in.project = project.get();
    return in;
  }

  @Test
  @GerritConfig(name = "auth.contributorAgreements", value = "true")
  public void anonymousAccessServerInfoEvenWithCLAs() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    gApi.config().server().getInfo();
  }

  @Test
  public void publishEditRestWithoutCLA() throws Exception {
    String filename = "foo";
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "subject1", filename, "contentold");
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();
    String changeId = result.getChangeId();

    gApi.changes().id(changeId).edit().create();
    gApi.changes()
        .id(changeId)
        .edit()
        .modifyFile(filename, RawInputUtil.create("newcontent".getBytes(UTF_8)));

    String url = "/changes/" + changeId + "/edit:publish";
    setUseContributorAgreements(InheritableBoolean.TRUE);
    userRestSession.post(url).assertForbidden();
    setUseContributorAgreements(InheritableBoolean.FALSE);
    userRestSession.post(url).assertNoContent();
  }
}
