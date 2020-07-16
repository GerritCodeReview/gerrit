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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.ContributorAgreement;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.PermissionRule;
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
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

@UseClockStep
public class AgreementsIT extends AbstractDaemonTest {
  private ContributorAgreement caAutoVerify;
  private ContributorAgreement caNoAutoVerify;
  @Inject private GroupOperations groupOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  protected void setUseContributorAgreements(InheritableBoolean value) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig config = projectConfigFactory.read(md);
      config.updateProject(
          p -> p.setBooleanConfig(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS, value));
      config.commit(md);
      projectCache.evict(config.getProject());
    }
  }

  protected ContributorAgreement configureContributorAgreement(boolean autoVerify)
      throws Exception {
    ContributorAgreement.Builder ca;
    String name = autoVerify ? "cla-test-group" : "cla-test-no-auto-verify-group";
    AccountGroup.UUID g = groupOperations.newGroup().name(name).create();
    GroupApi groupApi = gApi.groups().id(g.get());
    groupApi.description("CLA test group");
    InternalGroup caGroup = group(AccountGroup.uuid(groupApi.detail().id));
    GroupReference groupRef = GroupReference.create(caGroup.getGroupUUID(), caGroup.getName());
    PermissionRule rule =
        PermissionRule.builder(groupRef).setAction(PermissionRule.Action.ALLOW).build();
    if (autoVerify) {
      ca = ContributorAgreement.builder("cla-test");
      ca.setAutoVerify(groupRef);
      ca.setAccepted(ImmutableList.of(rule));
    } else {
      ca = ContributorAgreement.builder("cla-test-no-auto-verify");
    }
    ca.setDescription("description");
    ca.setAgreementUrl("agreement-url");
    ca.setAccepted(ImmutableList.of(rule));
    ca.setExcludeProjectsRegexes(ImmutableList.of("ExcludedProject"));

    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      ContributorAgreement contributorAgreement = ca.build();
      u.getConfig().replace(contributorAgreement);
      u.save();
      return contributorAgreement;
    }
  }

  @ConfigSuite.Config
  public static Config enableAgreementsConfig() {
    Config cfg = new Config();
    cfg.setBoolean("auth", null, "contributorAgreements", true);
    return cfg;
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
      // Sort to get a stable assertion as the API does not guarantee ordering.
      List<AgreementInfo> agreements =
          ImmutableList.sortedCopyOf(comparing(a -> a.name), info.auth.contributorAgreements);
      assertAgreement(agreements.get(0), caAutoVerify);
      assertAgreement(agreements.get(1), caNoAutoVerify);
    } else {
      assertThat(info.auth.useContributorAgreements).isNull();
      assertThat(info.auth.contributorAgreements).isNull();
    }
  }

  @Test
  public void signNonExistingAgreement() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.accounts().self().signAgreement("does-not-exist"));
    assertThat(thrown).hasMessageThat().contains("contributor agreement not found");
  }

  @Test
  public void signAgreementNoAutoVerify() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.accounts().self().signAgreement(caNoAutoVerify.getName()));
    assertThat(thrown).hasMessageThat().contains("cannot enter a non-autoVerify agreement");
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
  public void listAgreementPermission() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();
    requestScopeOperations.setApiUser(admin.id());
    // Allowed.
    gApi.accounts().id(user.id().get()).listAgreements();
    requestScopeOperations.setApiUser(user.id());

    // Not allowed.
    assertThrows(AuthException.class, () -> gApi.accounts().id(admin.id().get()).listAgreements());
  }

  @Test
  public void signAgreementAsOtherUser() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();
    assertThat(gApi.accounts().self().get().name).isNotEqualTo("admin");
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.accounts().id("admin").signAgreement(caAutoVerify.getName()));
    assertThat(thrown).hasMessageThat().contains("not allowed to enter contributor agreement");
  }

  @Test
  public void signAgreementAnonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.accounts().self().signAgreement(caAutoVerify.getName()));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void agreementsDisabledSign() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isFalse();
    MethodNotAllowedException thrown =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.accounts().self().signAgreement(caAutoVerify.getName()));
    assertThat(thrown).hasMessageThat().contains("contributor agreements disabled");
  }

  @Test
  public void agreementsDisabledList() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isFalse();
    MethodNotAllowedException thrown =
        assertThrows(
            MethodNotAllowedException.class, () -> gApi.accounts().self().listAgreements());
    assertThat(thrown).hasMessageThat().contains("contributor agreements disabled");
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
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.changes().id(change.changeId).revert());
    assertThat(thrown).hasMessageThat().contains("Contributor Agreement");
  }

  @Test
  public void revertSubmissionWithoutCLA() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // Create a change succeeds when agreement is not required
    setUseContributorAgreements(InheritableBoolean.FALSE);
    ChangeInfo change = gApi.changes().create(newChangeInput()).get();

    // Approve and submit it
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(change.changeId).current().review(ReviewInput.approve());
    gApi.changes().id(change.changeId).current().submit(new SubmitInput());

    // Revert Submission is not allowed when CLA is required but not signed
    requestScopeOperations.setApiUser(user.id());
    setUseContributorAgreements(InheritableBoolean.TRUE);
    AuthException thrown =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(change.changeId).revertSubmission());
    assertThat(thrown).hasMessageThat().contains("Contributor Agreement");
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
    AuthException thrown =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(change.changeId).current().cherryPick(in));
    assertThat(thrown).hasMessageThat().contains("Contributor Agreement");
  }

  @Test
  public void createChangeRespectsCLA() throws Exception {
    assume().that(isContributorAgreementsEnabled()).isTrue();

    // Create a change succeeds when agreement is not required
    setUseContributorAgreements(InheritableBoolean.FALSE);
    gApi.changes().create(newChangeInput());

    // Create a change is not allowed when CLA is required but not signed
    setUseContributorAgreements(InheritableBoolean.TRUE);
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.changes().create(newChangeInput()));
    assertThat(thrown).hasMessageThat().contains("Contributor Agreement");

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
