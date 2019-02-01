// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.verifier.VerifierUUID;
import com.google.inject.Inject;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class VerifierRefsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private VerifierOperations verifierOperations;
  @Inject private Sequences seq;
  @Inject private ChangeInserter.Factory changeInserterFactory;
  @Inject private BatchUpdate.Factory updateFactory;

  @Test
  public void adminCanReadVerifierRefsByDefault() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, verifierRef + ":verifierRef");
  }

  @Test
  public void nonAdminCannotReadVerifierRefs() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, user);

    exception.expect(TransportException.class);
    exception.expectMessage(
        String.format("Remote does not have %s available for fetch.", verifierRef));
    fetch(repo, verifierRef + ":verifierRef");
  }

  @Test
  public void verifierAdminsCannotReadVerifierRefsWithoutExplicitReadPermission() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ADMINISTRATE_VERIFIERS);
    requestScopeOperations.setApiUser(user.getId());

    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, user);

    exception.expect(TransportException.class);
    exception.expectMessage(
        String.format("Remote does not have %s available for fetch.", verifierRef));
    fetch(repo, verifierRef + ":verifierRef");
  }

  @Test
  public void cannotCreateVerifierRef() throws Exception {
    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.CREATE);
    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.PUSH);

    String verifierRef = RefNames.refsVerifiers(VerifierUUID.make("my-verifier"));

    TestRepository<InMemoryRepository> testRepo = cloneProject(allProjects);
    PushOneCommit.Result r = pushFactory.create(admin.getIdent(), testRepo).to(verifierRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create verifier ref.");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      assertThat(repo.exactRef(verifierRef)).isNull();
    }
  }

  @Test
  public void cannotDeleteVerifierRef() throws Exception {
    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.DELETE, true, REGISTERED_USERS);

    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> testRepo = cloneProject(allProjects);
    PushResult r = deleteRef(testRepo, verifierRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(verifierRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(refUpdate.getMessage()).contains("Not allowed to delete verifier ref.");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      assertThat(repo.exactRef(verifierRef)).isNotNull();
    }
  }

  @Test
  public void updateVerifierRefsByPushIsDisabled() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, verifierRef + ":verifierRef");
    repo.reset("verifierRef");

    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.PUSH);
    PushOneCommit.Result r = pushFactory.create(admin.getIdent(), repo).to(verifierRef);
    r.assertErrorStatus();
    r.assertMessage("direct update of verifier ref not allowed");
  }

  @Test
  public void submitToVerifierRefsIsDisabled() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    String changeId = createChangeWithoutCommitValidation(verifierRef);

    grantLabel(
        "Code-Review",
        -2,
        2,
        allProjects,
        RefNames.REFS_VERIFIERS + "*",
        false,
        adminGroupUuid(),
        false);
    approve(changeId);

    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.SUBMIT);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("submit to verifier ref not allowed");
    gApi.changes().id(changeId).current().submit();
  }

  @Test
  public void createChangeForVerifierRefsByPushIsDisabled() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, verifierRef + ":verifierRef");
    repo.reset("verifierRef");

    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.PUSH);
    PushOneCommit.Result r =
        pushFactory.create(admin.getIdent(), repo).to("refs/for/" + verifierRef);
    r.assertErrorStatus();
    r.assertMessage("creating change for verifier ref not allowed");
  }

  @Test
  public void createChangeForVerifierRefsViaApiIsDisabled() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, verifierRef + ":verifierRef");
    repo.reset("verifierRef");
    RevCommit head = getHead(repo.getRepository(), "HEAD");

    ChangeInput input = new ChangeInput();
    input.project = allProjects.get();
    input.branch = verifierRef;
    input.baseCommit = head.name();
    input.subject = "A change.";

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("creating change for verifier ref not allowed");
    gApi.changes().create(input);
  }

  private String createChangeWithoutCommitValidation(String targetRef) throws Exception {
    try (Repository git = repoManager.openRepository(allProjects);
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      RevCommit head = rw.parseCommit(git.exactRef(targetRef).getObjectId());
      RevCommit commit =
          new TestRepository<>(git)
              .commit()
              .author(admin.getIdent())
              .message("A change.")
              .insertChangeId()
              .parent(head)
              .create();

      Change.Id changeId = new Change.Id(seq.nextChangeId());
      ChangeInserter ins = changeInserterFactory.create(changeId, commit, targetRef);
      ins.setValidate(false);
      ins.setMessage(String.format("Uploaded patch set %s.", ins.getPatchSetId().get()));
      try (BatchUpdate bu =
          updateFactory.create(
              allProjects, identifiedUserFactory.create(admin.id), TimeUtil.nowTs())) {
        bu.setRepository(git, rw, oi);
        bu.insertChange(ins);
        bu.execute();
      }
      return changeId.toString();
    }
  }
}
