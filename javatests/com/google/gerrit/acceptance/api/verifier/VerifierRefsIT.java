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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.inject.Inject;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class VerifierRefsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private VerifierOperations verifierOperations;

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
  public void updateVerifierRefsByPushIsDisabled() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, verifierRef + ":verifierRef");
    repo.reset("verifierRef");

    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.PUSH);
    PushOneCommit.Result r = pushFactory.create(admin.getIdent(), repo).to(verifierRef);
    r.assertErrorStatus();
    r.assertMessage("verifier update not allowed");
  }

  @Test
  public void submitToVerifierRefsIsDisabled() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().create();
    String verifierRef = RefNames.refsVerifiers(verifierUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, verifierRef + ":verifierRef");
    repo.reset("verifierRef");

    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.PUSH);
    PushOneCommit.Result r =
        pushFactory.create(admin.getIdent(), repo).to("refs/for/" + verifierRef);
    r.assertOkStatus();

    grantLabel(
        "Code-Review",
        -2,
        2,
        allProjects,
        RefNames.REFS_VERIFIERS + "*",
        false,
        adminGroupUuid(),
        false);
    approve(r.getChange().getId().toString());

    grant(allProjects, RefNames.REFS_VERIFIERS + "*", Permission.SUBMIT);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("verifier update not allowed");
    gApi.changes().id(r.getChange().getId().get()).current().submit();
  }
}
