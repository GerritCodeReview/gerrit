// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.sign;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.ContentCommitInserter;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.BatchUpdates;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

public class SignedCommitPolicyIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private ChangeInserter.Factory changeInserterFactory;
  @Inject private PatchSetInserter.Factory patchSetInserterFactory;
  @Inject private Sequences sequences;
  @Inject private BatchUpdates batchUpdates;
  @Inject private ContentCommitInserter contentCommitInserter;

  @Test
  public void cannotEnablePolicyWhenVerificationIsUnavailable() throws Exception {
    adminRestSession
        .put("/projects/" + project.get() + "/config", Map.of("require_signed_commits", "TRUE"))
        .assertStatus(HttpStatus.SC_CONFLICT);
  }

  @Test
  public void cannotCreateRequiredProjectWhenVerificationIsUnavailable() throws Exception {
    adminRestSession
        .put("/projects/required-project", Map.of("require_signed_commits", "TRUE"))
        .assertStatus(HttpStatus.SC_CONFLICT);
    assertThat(projectCache.get(Project.nameKey("required-project"))).isEmpty();
  }

  @Test
  public void configPushCannotEnablePolicyWhenVerificationIsUnavailable() throws Exception {
    RevCommit configCommit = policyConfigCommit("true");
    ObjectId oldConfig;
    try (Repository repo = repoManager.openRepository(project)) {
      oldConfig = repo.exactRef("refs/meta/config").getObjectId();
    }
    testRepo.update("HEAD", configCommit);
    var update = pushHead(testRepo, "refs/meta/config").getRemoteUpdate("refs/meta/config");
    assertThat(update.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(update.getMessage()).contains("commit signature verification");
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/meta/config").getObjectId()).isEqualTo(oldConfig);
    }
  }

  @Test
  public void configSubmitCannotEnablePolicyWhenVerificationIsUnavailable() throws Exception {
    RevCommit configCommit = policyConfigCommit("true");
    push(configCommit, "refs/for/refs/meta/config");
    String changeId = changeId(configCommit);
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    ObjectId oldConfig;
    try (Repository repo = repoManager.openRepository(project)) {
      oldConfig = repo.exactRef("refs/meta/config").getObjectId();
    }
    assertThrows(
        ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/meta/config").getObjectId()).isEqualTo(oldConfig);
    }
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void policyDefaultsToInheritAndFalse() throws Exception {
    var response = adminRestSession.get("/projects/" + project.get() + "/config");
    response.assertOK();
    JsonObject config = newGson().fromJson(response.getReader(), JsonObject.class);
    assertThat(config.has("require_signed_commits")).isTrue();
    JsonObject policy = config.getAsJsonObject("require_signed_commits");
    assertThat(policy.get("configured_value").getAsString()).isEqualTo("INHERIT");
    assertThat(policy.get("inherited_value").getAsBoolean()).isFalse();
    assertThat(policy.get("value").getAsBoolean()).isFalse();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void policyIsInheritedAndChildCanOverrideIt() throws Exception {
    adminRestSession
        .put("/projects/policy-child", Map.of("parent", project.get(), "create_empty_commit", true))
        .assertCreated();
    setPolicy(project.get(), "TRUE");
    JsonObject inherited = getPolicy("policy-child");
    assertThat(inherited.get("configured_value").getAsString()).isEqualTo("INHERIT");
    assertThat(inherited.get("inherited_value").getAsBoolean()).isTrue();
    assertThat(inherited.get("value").getAsBoolean()).isTrue();
    try (var childRepo = cloneProject(Project.nameKey("policy-child"))) {
      ObjectId childTip = childRepo.getRepository().resolve("HEAD");
      pushFactory
          .create(admin.newIdent(), childRepo)
          .to("refs/for/master")
          .assertErrorStatus("requires verified OpenPGP commit signatures");
      childRepo.reset(childTip);
      setPolicy("policy-child", "FALSE");
      pushFactory.create(admin.newIdent(), childRepo).to("refs/for/master").assertOkStatus();
    }
    JsonObject overridden = getPolicy("policy-child");
    assertThat(overridden.get("configured_value").getAsString()).isEqualTo("FALSE");
    assertThat(overridden.get("inherited_value").getAsBoolean()).isTrue();
    assertThat(overridden.get("value").getAsBoolean()).isFalse();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void unsignedReviewIsRejected() throws Exception {
    setPolicy(project.get(), "TRUE");
    pushFactory
        .create(admin.newIdent(), testRepo)
        .to("refs/for/master")
        .assertErrorStatus("requires verified OpenPGP commit signatures");
    assertThat(gApi.changes().query("project:" + project.get()).get()).isEmpty();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void signedReviewAndFastForwardPreserveTheOriginalObject() throws Exception {
    registerSigningKey();
    ConfigInput config = new ConfigInput();
    config.submitType = SubmitType.FAST_FORWARD_ONLY;
    gApi.projects().name(project.get()).config(config);
    setPolicy(project.get(), "TRUE");
    RevCommit commit = signedCommit(newCommit(testRepo, tip(), "signed", "signed.txt"));
    push(commit, "refs/for/master");
    String changeId = changeId(commit);
    assertThat(gApi.changes().id(changeId).current().signature().signer._accountId)
        .isEqualTo(user.id().get());
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    gApi.changes().id(changeId).current().submit();
    assertThat(tip()).isEqualTo(commit.getId());
    assertThat(gApi.changes().id(changeId).get().currentRevision).isEqualTo(commit.name());
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void directUnsignedPushIsRejected() throws Exception {
    ObjectId oldTip = tip();
    setPolicy(project.get(), "TRUE");
    pushFactory
        .create(admin.newIdent(), testRepo)
        .to("refs/heads/master")
        .assertErrorStatus("requires verified OpenPGP commit signatures");
    assertThat(tip()).isEqualTo(oldTip);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void skipValidationCannotBypassTheBranchPolicy() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.FORGE_AUTHOR).ref("refs/*").group(adminGroupUuid()))
        .add(allow(Permission.FORGE_COMMITTER).ref("refs/*").group(adminGroupUuid()))
        .add(allow(Permission.FORGE_SERVER).ref("refs/*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH_MERGE).ref("refs/*").group(adminGroupUuid()))
        .update();
    ObjectId oldTip = tip();
    setPolicy(project.get(), "TRUE");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(ImmutableList.of("skip-validation"));
    push.to("refs/heads/master").assertErrorStatus("requires verified OpenPGP commit signatures");
    assertThat(tip()).isEqualTo(oldTip);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void unsignedAncestorOnAnotherBranchIsNotGrandfathered() throws Exception {
    registerSigningKey();
    ObjectId oldTip = tip();
    gApi.projects().name(project.get()).branch("other").create(new BranchInput());
    PushOneCommit.Result ancestor =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/heads/other");
    ancestor.assertOkStatus();
    RevCommit signed = signedCommit(newCommit(testRepo, ancestor.getCommit(), "tip", "tip.txt"));
    setPolicy(project.get(), "TRUE");
    testRepo.update("HEAD", signed);
    var update = pushHead(testRepo, "refs/heads/master").getRemoteUpdate("refs/heads/master");
    assertThat(update.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(update.getMessage()).contains(ancestor.getCommit().name());
    assertThat(tip()).isEqualTo(oldTip);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void newRestBranchChecksItsWholeUnsignedHistory() throws Exception {
    ObjectId oldTip = tip();
    setPolicy(project.get(), "TRUE");
    assertThrows(
        ResourceConflictException.class,
        () -> gApi.projects().name(project.get()).branch("new").create(new BranchInput()));
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/new")).isNull();
    }
    assertThat(tip()).isEqualTo(oldTip);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void unsignedPatchSetUploadedBeforePolicyCannotBeSubmitted() throws Exception {
    PushOneCommit.Result original = createChange();
    ObjectId oldTip = tip();
    gApi.changes().id(original.getChangeId()).current().review(ReviewInput.approve());
    setPolicy(project.get(), "TRUE");
    assertThrows(
        ResourceConflictException.class,
        () -> gApi.changes().id(original.getChangeId()).current().submit());
    assertThat(tip()).isEqualTo(oldTip);
    assertThat(gApi.changes().id(original.getChangeId()).get().status)
        .isEqualTo(com.google.gerrit.extensions.client.ChangeStatus.NEW);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void submitRechecksTheCurrentRegisteredKey() throws Exception {
    registerSigningKey();
    ConfigInput config = new ConfigInput();
    config.submitType = SubmitType.FAST_FORWARD_ONLY;
    gApi.projects().name(project.get()).config(config);
    ObjectId oldTip = tip();
    setPolicy(project.get(), "TRUE");
    RevCommit signed = signedCommit(newCommit(testRepo, oldTip, "key removed", "key.txt"));
    push(signed, "refs/for/master");
    String changeId = changeId(signed);
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().self().gpgKey(validKeyWithoutExpiration().getKeyIdString()).delete();
    requestScopeOperations.setApiUser(admin.id());

    assertThrows(
        ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(tip()).isEqualTo(oldTip);
    assertThat(gApi.changes().id(changeId).get().currentRevision).isEqualTo(signed.name());
    assertThat(gApi.changes().id(changeId).get().status)
        .isEqualTo(com.google.gerrit.extensions.client.ChangeStatus.NEW);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void existingReviewObjectCannotBypassPolicy() throws Exception {
    PushOneCommit.Result original = createChange();
    setPolicy(project.get(), "TRUE");
    testRepo.update("HEAD", original.getCommit());
    var update = pushHead(testRepo, "refs/for/master").getRemoteUpdate("refs/for/master");
    assertThat(update.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(update.getMessage()).contains("requires verified OpenPGP commit signatures");
    assertThat(gApi.changes().id(original.getChangeId()).get().currentRevision)
        .isEqualTo(original.getCommit().name());
    assertThat(gApi.changes().id(original.getChangeId()).get().revisions).hasSize(1);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void newRestBranchAcceptsAnEntirelySignedHistory() throws Exception {
    registerSigningKey();
    RevCommit rootSource =
        testRepo
            .commit()
            .author(admin.newIdent())
            .committer(admin.newIdent())
            .message("signed root\n")
            .add("root.txt", "root")
            .create();
    testRepo.getRevWalk().parseBody(rootSource);
    RevCommit root = signedCommit(rootSource);
    RevCommit signed = signedCommit(newCommit(testRepo, root, "signed child", "child.txt"));
    push(signed, "refs/heads/signed-source");
    setPolicy(project.get(), "TRUE");
    BranchInput input = new BranchInput();
    input.revision = signed.name();
    gApi.projects().name(project.get()).branch("signed-history").create(input);
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/signed-history").getObjectId()).isEqualTo(signed);
    }
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void generatedRestFileCommitDoesNotUpdateTheBranch() throws Exception {
    ObjectId oldTip = tip();
    setPolicy(project.get(), "TRUE");
    adminRestSession
        .post(
            "/projects/" + project.get() + "/branches/master/commit",
            Map.of(
                "commit_message",
                "generated file commit",
                "files",
                Map.of("new.txt", Map.of("content", "bmV3IGNvbnRlbnQ="))))
        .assertStatus(HttpStatus.SC_CONFLICT);
    assertThat(tip()).isEqualTo(oldTip);
    assertThat(gApi.changes().query("project:" + project.get()).get()).isEmpty();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void unsignedGeneratedSubmitMergeDoesNotUpdateRefs() throws Exception {
    registerSigningKey();
    ConfigInput config = new ConfigInput();
    config.submitType = SubmitType.MERGE_ALWAYS;
    gApi.projects().name(project.get()).config(config);
    ObjectId oldTip = tip();
    RevCommit signed = signedCommit(newCommit(testRepo, oldTip, "merge", "merge.txt"));
    push(signed, "refs/for/master");
    String changeId = changeId(signed);
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    setPolicy(project.get(), "TRUE");
    assertThrows(
        ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(tip()).isEqualTo(oldTip);
    assertThat(gApi.changes().id(changeId).get().currentRevision).isEqualTo(signed.name());
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void unsignedRestPatchSetDoesNotUpdateChangeRefs() throws Exception {
    PushOneCommit.Result original = createChange();
    setPolicy(project.get(), "TRUE");
    assertThrows(
        ResourceConflictException.class,
        () ->
            gApi.changes()
                .id(original.getChangeId())
                .setMessage("new message\n\nChange-Id: " + original.getChangeId() + "\n"));
    assertThat(gApi.changes().id(original.getChangeId()).get().currentRevision)
        .isEqualTo(original.getCommit().name());
    assertThat(gApi.changes().id(original.getChangeId()).get().revisions).hasSize(1);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void disabledChangeValidationCannotBypassPolicy() throws Exception {
    setPolicy(project.get(), "TRUE");
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> serverRepo = new TestRepository<>(repo)) {
      RevCommit unsigned = newCommit(serverRepo, tip(), "unvalidated", "unvalidated.txt");
      Change.Id id = Change.id(sequences.nextChangeId());
      ChangeInserter inserter =
          changeInserterFactory.create(id, unsigned, "refs/heads/master").disableValidation();
      try (BatchUpdate update =
          batchUpdateFactory.create(
              project, identifiedUserFactory.create(admin.id()), TimeUtil.now())) {
        update.insertChange(inserter);
        testRefAction(() -> assertThrows(ResourceConflictException.class, update::execute));
      }
      assertThat(repo.exactRef(PatchSet.id(id, 1).toRefName())).isNull();
    }
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void disabledPatchSetValidationCannotBypassPolicy() throws Exception {
    PushOneCommit.Result original = createChange();
    setPolicy(project.get(), "TRUE");
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> serverRepo = new TestRepository<>(repo)) {
      RevCommit unsigned = newCommit(serverRepo, tip(), "replacement", "replacement.txt");
      PatchSet.Id psId = PatchSet.id(original.getChange().getId(), 2);
      PatchSetInserter inserter =
          patchSetInserterFactory
              .create(original.getChange().notes(), psId, unsigned)
              .disableValidation();
      try (BatchUpdate update =
          batchUpdateFactory.create(
              project, identifiedUserFactory.create(admin.id()), TimeUtil.now())) {
        update.addOp(psId.changeId(), inserter);
        testRefAction(() -> assertThrows(ResourceConflictException.class, update::execute));
      }
      assertThat(repo.exactRef(psId.toRefName())).isNull();
    }
  }

  @Test
  public void unavailableVerifierFailsClosedButPolicyCanBeDisabled() throws Exception {
    try (ProjectConfigUpdate update = updateProject(project)) {
      update
          .getConfig()
          .updateProject(
              p ->
                  p.setBooleanConfig(
                      BooleanProjectConfig.REQUIRE_SIGNED_COMMITS, InheritableBoolean.TRUE));
      update.save();
    }
    ObjectId oldTip = tip();
    pushFactory
        .create(admin.newIdent(), testRepo)
        .to("refs/heads/master")
        .assertErrorStatus("commit signature verification is unavailable");
    assertThat(tip()).isEqualTo(oldTip);
    setPolicy(project.get(), "FALSE");
    testRepo.update("HEAD", oldTip);
    pushFactory.create(admin.newIdent(), testRepo).to("refs/heads/master").assertOkStatus();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void unchangedUnsignedPostCommitReviewIsPermitted() throws Exception {
    pushFactory.create(admin.newIdent(), testRepo).to("refs/heads/master").assertOkStatus();
    ObjectId oldTip = tip();
    setPolicy(project.get(), "TRUE");
    var update =
        pushHead(testRepo, "refs/for/master%merged").getRemoteUpdate("refs/for/master%merged");
    assertThat(update.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);
    assertThat(tip()).isEqualTo(oldTip);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void initialCommitProjectCreationIsRejectedBeforeCreatingTheRepository() throws Exception {
    ProjectInput input = new ProjectInput();
    input.name = "required-initial-project";
    input.requireSignedCommits = InheritableBoolean.TRUE;
    input.createEmptyCommit = true;
    assertThrows(ResourceConflictException.class, () -> gApi.projects().create(input));
    assertThat(repoManager.getRepositoryStatus(Project.nameKey(input.name)))
        .isEqualTo(com.google.gerrit.server.git.GitRepositoryManager.Status.NON_EXISTENT);
    assertThat(projectCache.get(Project.nameKey(input.name))).isEmpty();
  }

  @Test
  public void unchangedInitOnlyProjectDoesNotRequireAnAvailableVerifier() throws Exception {
    ProjectInput input = new ProjectInput();
    input.name = "unchanged-required-project";
    input.createEmptyCommit = true;
    gApi.projects().create(input);
    Project.NameKey name = Project.nameKey(input.name);
    try (ProjectConfigUpdate update = updateProject(name)) {
      update
          .getConfig()
          .updateProject(
              p ->
                  p.setBooleanConfig(
                      BooleanProjectConfig.REQUIRE_SIGNED_COMMITS, InheritableBoolean.TRUE));
      update.save();
    }
    ObjectId oldConfig = projectOperations.project(name).getHead("refs/meta/config");
    ObjectId oldTip = projectOperations.project(name).getHead("refs/heads/master");
    input.initOnly = true;
    input.requireSignedCommits = InheritableBoolean.TRUE;
    adminRestSession.put("/projects/" + input.name, input).assertOK();
    assertThat(projectOperations.project(name).getHead("refs/meta/config")).isEqualTo(oldConfig);
    assertThat(projectOperations.project(name).getHead("refs/heads/master")).isEqualTo(oldTip);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void disablingPolicyAndAddingUnsignedContentStillUsesTheCurrentPolicy() throws Exception {
    setPolicy(project.get(), "TRUE");
    ObjectId oldTip = tip();
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> serverRepo = new TestRepository<>(repo);
        BatchUpdate update =
            batchUpdateFactory.create(
                project, identifiedUserFactory.create(admin.id()), TimeUtil.now())) {
      ObjectId oldConfig = repo.exactRef("refs/meta/config").getObjectId();
      RevCommit disable = policyConfigCommit(serverRepo, "false");
      RevCommit unsigned = newCommit(serverRepo, oldTip, "unsigned", "unsigned.txt");
      update.addRepoOnlyOp(branchUpdate(oldTip, unsigned));
      update.addRepoOnlyOp(refUpdate("refs/meta/config", oldConfig, disable));
      testRefAction(() -> assertThrows(ResourceConflictException.class, update::execute));
      assertThat(repo.exactRef("refs/heads/master").getObjectId()).isEqualTo(oldTip);
      assertThat(repo.exactRef("refs/meta/config").getObjectId()).isEqualTo(oldConfig);
    }
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void enablingPolicyAndAddingContentUsesThePolicyBeforeActivation() throws Exception {
    ObjectId oldTip = tip();
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> serverRepo = new TestRepository<>(repo);
        BatchUpdate update =
            batchUpdateFactory.create(
                project, identifiedUserFactory.create(admin.id()), TimeUtil.now())) {
      ObjectId oldConfig = repo.exactRef("refs/meta/config").getObjectId();
      RevCommit enable = policyConfigCommit(serverRepo, "true");
      RevCommit unsigned = newCommit(serverRepo, oldTip, "before activation", "old-policy.txt");
      update.addRepoOnlyOp(branchUpdate(oldTip, unsigned));
      update.addRepoOnlyOp(refUpdate("refs/meta/config", oldConfig, enable));
      testRefAction(
          () -> {
            update.execute();
          });
      assertThat(repo.exactRef("refs/heads/master").getObjectId()).isEqualTo(unsigned);
      assertThat(repo.exactRef("refs/meta/config").getObjectId()).isEqualTo(enable);
    }
    // The low-level RepoOnlyOp above does not run a config updater's cache eviction.
    projectCache.evict(project);
    assertThat(getPolicy(project.get()).get("value").getAsBoolean()).isTrue();
    ObjectId enabledTip = tip();
    fetch(testRepo, "refs/heads/master:refs/remotes/origin/master");
    testRepo.reset(enabledTip);
    pushFactory
        .create(admin.newIdent(), testRepo)
        .to("refs/heads/master")
        .assertErrorStatus("requires verified OpenPGP commit signatures");
    assertThat(tip()).isEqualTo(enabledTip);
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void generatedCommitIsRejectedBeforeObjectInsertion() throws Exception {
    setPolicy(project.get(), "TRUE");
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID);
    builder.setAuthor(admin.newIdent());
    builder.setCommitter(admin.newIdent());
    builder.setMessage("generated content\n");
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter()) {
      ObjectId id = inserter.idFor(OBJ_COMMIT, builder.build());
      try (var reader = inserter.newReader()) {
        assertThat(reader.has(id)).isFalse();
      }
      assertThrows(
          ResourceConflictException.class,
          () ->
              contentCommitInserter.insert(
                  BranchNameKey.create(project, "refs/heads/master"), inserter, builder));
      inserter.flush();
      try (var reader = repo.newObjectReader()) {
        assertThat(reader.has(id)).isFalse();
      }
    }
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void generatedEditIsRejectedBeforeCreatingAnEditRef() throws Exception {
    PushOneCommit.Result original = createChange();
    setPolicy(project.get(), "TRUE");
    assertThrows(
        ResourceConflictException.class,
        () ->
            gApi.changes()
                .id(original.getChangeId())
                .edit()
                .modifyFile("edited.txt", RawInputUtil.create("edited")));
    assertThat(gApi.changes().id(original.getChangeId()).edit().get()).isEmpty();
    assertThat(gApi.changes().id(original.getChangeId()).get().currentRevision)
        .isEqualTo(original.getCommit().name());
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void publishingAnUnchangedSignedEditPreservesItsObject() throws Exception {
    registerSigningKey();
    RevCommit original = signedCommit(newCommit(testRepo, tip(), "original", "original.txt"));
    push(original, "refs/for/master");
    String changeId = changeId(original);
    RevCommit editSource =
        testRepo
            .commit()
            .parent(original.getParent(0))
            .author(original.getAuthorIdent())
            .committer(admin.newIdent())
            .message(original.getFullMessage())
            .add("edited.txt", "edited")
            .create();
    RevCommit edit = signedCommit(editSource);
    setPolicy(project.get(), "TRUE");
    push(edit, "refs/for/master%edit");
    assertThat(gApi.changes().id(changeId).edit().get().orElseThrow().commit.commit)
        .isEqualTo(edit.name());
    gApi.changes().id(changeId).edit().publish();
    assertThat(gApi.changes().id(changeId).get().currentRevision).isEqualTo(edit.name());
    assertThat(gApi.changes().id(changeId).current().signature().status)
        .isEqualTo(com.google.gerrit.extensions.common.CommitSignatureInfo.Status.VERIFIED);
    assertThat(gApi.changes().id(changeId).edit().get()).isEmpty();
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void allProjectBranchPoliciesRunBeforeAnyRefUpdate() throws Exception {
    ProjectInput input = new ProjectInput();
    input.name = "second-project";
    input.createEmptyCommit = true;
    gApi.projects().create(input);
    Project.NameKey second = Project.nameKey(input.name);
    setPolicy(input.name, "TRUE");
    ObjectId firstTip = tip();
    try (Repository firstRepo = repoManager.openRepository(project);
        Repository secondRepo = repoManager.openRepository(second);
        TestRepository<Repository> first = new TestRepository<>(firstRepo);
        TestRepository<Repository> other = new TestRepository<>(secondRepo);
        BatchUpdate firstUpdate =
            batchUpdateFactory.create(
                project, identifiedUserFactory.create(admin.id()), TimeUtil.now());
        BatchUpdate secondUpdate =
            batchUpdateFactory.create(
                second, identifiedUserFactory.create(admin.id()), TimeUtil.now())) {
      ObjectId secondTip = secondRepo.exactRef("refs/heads/master").getObjectId();
      RevCommit firstCommit = newCommit(first, firstTip, "first", "first.txt");
      RevCommit secondCommit = newCommit(other, secondTip, "second", "second.txt");
      firstUpdate.addRepoOnlyOp(branchUpdate(firstTip, firstCommit));
      secondUpdate.addRepoOnlyOp(branchUpdate(secondTip, secondCommit));
      testRefAction(
          () ->
              assertThrows(
                  ResourceConflictException.class,
                  () ->
                      batchUpdates.execute(
                          ImmutableList.of(firstUpdate, secondUpdate), ImmutableList.of(), false)));
      assertThat(firstRepo.exactRef("refs/heads/master").getObjectId()).isEqualTo(firstTip);
      assertThat(secondRepo.exactRef("refs/heads/master").getObjectId()).isEqualTo(secondTip);
    }
  }

  @Test
  @GerritConfig(name = "gpg.enableCommitSignatureVerification", value = "true")
  public void finalHookCommandsAreCheckedBeforeAnyProjectRefUpdate() throws Exception {
    ProjectInput input = new ProjectInput();
    input.name = "hook-project";
    input.createEmptyCommit = true;
    gApi.projects().create(input);
    Project.NameKey second = Project.nameKey(input.name);
    setPolicy(input.name, "TRUE");
    ObjectId firstTip = tip();
    try (Repository firstRepo = repoManager.openRepository(project);
        Repository secondRepo = repoManager.openRepository(second);
        TestRepository<Repository> first = new TestRepository<>(firstRepo);
        TestRepository<Repository> other = new TestRepository<>(secondRepo);
        BatchUpdate firstUpdate =
            batchUpdateFactory.create(
                project, identifiedUserFactory.create(admin.id()), TimeUtil.now());
        BatchUpdate secondUpdate =
            batchUpdateFactory.create(
                second, identifiedUserFactory.create(admin.id()), TimeUtil.now())) {
      ObjectId secondTip = secondRepo.exactRef("refs/heads/master").getObjectId();
      RevCommit firstCommit = newCommit(first, firstTip, "first", "first.txt");
      RevCommit unsigned = newCommit(other, secondTip, "unsigned hook", "hook.txt");
      firstUpdate.addRepoOnlyOp(branchUpdate(firstTip, firstCommit));
      secondUpdate.addRepoOnlyOp(
          new RepoOnlyOp() {
            @Override
            public void updateRepo(RepoContext ctx) throws Exception {
              ctx.addRefUpdate(ObjectId.zeroId(), unsigned, "refs/meta/policy-test");
            }
          });
      BatchUpdateListener listener =
          new BatchUpdateListener() {
            @Override
            public BatchRefUpdate beforeUpdateRefs(BatchRefUpdate bru) {
              if (bru.getCommands().stream()
                  .noneMatch(c -> c.getRefName().equals("refs/meta/policy-test"))) {
                return bru;
              }
              BatchRefUpdate replacement = secondRepo.getRefDatabase().newBatchUpdate();
              replacement.setAtomic(true);
              replacement.addCommand(bru.getCommands());
              replacement.addCommand(new ReceiveCommand(secondTip, unsigned, "refs/heads/master"));
              return replacement;
            }
          };
      testRefAction(
          () ->
              assertThrows(
                  ResourceConflictException.class,
                  () ->
                      batchUpdates.execute(
                          ImmutableList.of(firstUpdate, secondUpdate),
                          ImmutableList.of(listener),
                          false)));
      assertThat(firstRepo.exactRef("refs/heads/master").getObjectId()).isEqualTo(firstTip);
      assertThat(secondRepo.exactRef("refs/heads/master").getObjectId()).isEqualTo(secondTip);
      assertThat(secondRepo.exactRef("refs/meta/policy-test")).isNull();
    }
  }

  private RevCommit policyConfigCommit(String value) throws Exception {
    fetch(testRepo, "refs/meta/config:refs/meta/config");
    return policyConfigCommit(testRepo, value);
  }

  private RevCommit policyConfigCommit(TestRepository<? extends Repository> repo, String value)
      throws Exception {
    ObjectId configTip = repo.getRepository().exactRef("refs/meta/config").getObjectId();
    var config = new BlobBasedConfig(null, repo.getRepository(), configTip, "project.config");
    config.setString("receive", null, "requireSignedCommits", value);
    return repo.commit()
        .parent(repo.getRevWalk().parseCommit(configTip))
        .author(admin.newIdent())
        .committer(admin.newIdent())
        .message(
            "Change signed-commit policy\n\nChange-Id: I"
                + CommitMessageUtil.generateChangeId().name()
                + "\n")
        .add("project.config", config.toText())
        .create();
  }

  private static RepoOnlyOp branchUpdate(ObjectId oldId, ObjectId newId) {
    return refUpdate("refs/heads/master", oldId, newId);
  }

  private static RepoOnlyOp refUpdate(String refName, ObjectId oldId, ObjectId newId) {
    return new RepoOnlyOp() {
      @Override
      public void updateRepo(RepoContext ctx) throws Exception {
        ctx.addRefUpdate(oldId, newId, refName);
      }
    };
  }

  private ObjectId tip() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return repo.exactRef("refs/heads/master").getObjectId();
    }
  }

  private RevCommit newCommit(
      TestRepository<? extends Repository> repo, ObjectId parent, String subject, String file)
      throws Exception {
    RevCommit commit =
        repo.commit()
            .parent(repo.getRevWalk().parseCommit(parent))
            .author(admin.newIdent())
            .committer(admin.newIdent())
            .message(
                subject + "\n\nChange-Id: I" + CommitMessageUtil.generateChangeId().name() + "\n")
            .add(file, subject)
            .create();
    repo.getRevWalk().parseBody(commit);
    return commit;
  }

  private void registerSigningKey() throws Exception {
    accountOperations
        .account(user.id())
        .forUpdate()
        .addSecondaryEmail("test1@example.com")
        .update();
    requestScopeOperations.setApiUser(user.id());
    gApi.accounts()
        .self()
        .putGpgKeys(List.of(validKeyWithoutExpiration().getPublicKeyArmored()), List.of());
    requestScopeOperations.setApiUser(admin.id());
  }

  private RevCommit signedCommit(RevCommit source) throws Exception {
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(source.getTree());
    builder.setParentIds(Arrays.asList(source.getParents()));
    builder.setAuthor(source.getAuthorIdent());
    builder.setCommitter(new PersonIdent("Test Signer", "test1@example.com"));
    builder.setMessage(source.getFullMessage());
    try (ObjectInserter inserter = testRepo.getRepository().newObjectInserter()) {
      ObjectId id =
          inserter.insert(
              OBJ_COMMIT, sign(builder.build(), validKeyWithoutExpiration(), Instant.now()));
      inserter.flush();
      return testRepo.getRevWalk().parseCommit(id);
    }
  }

  private void push(RevCommit commit, String ref) throws Exception {
    testRepo.update("HEAD", commit);
    assertThat(pushHead(testRepo, ref).getRemoteUpdate(ref).getStatus())
        .isEqualTo(RemoteRefUpdate.Status.OK);
  }

  private String changeId(RevCommit commit) throws Exception {
    return gApi.changes().query("commit:" + commit.name()).get().getFirst().id;
  }

  private void setPolicy(String projectName, String value) throws Exception {
    adminRestSession
        .put("/projects/" + projectName + "/config", Map.of("require_signed_commits", value))
        .assertOK();
  }

  private JsonObject getPolicy(String projectName) throws Exception {
    var response = adminRestSession.get("/projects/" + projectName + "/config");
    response.assertOK();
    JsonObject config = newGson().fromJson(response.getReader(), JsonObject.class);
    assertThat(config.has("require_signed_commits")).isTrue();
    return config.getAsJsonObject("require_signed_commits");
  }
}
