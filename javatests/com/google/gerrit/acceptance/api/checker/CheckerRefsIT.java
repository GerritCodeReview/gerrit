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

package com.google.gerrit.acceptance.api.checker;

import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.checker.CheckerOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.inject.Inject;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class CheckerRefsIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private CheckerOperations checkerOperations;

  @Test
  public void adminCanReadCheckerRefsByDefault() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = RefNames.refsCheckers(checkerUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, checkerRef + ":checkerRef");
  }

  @Test
  public void nonAdminCannotReadCheckerRefs() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = RefNames.refsCheckers(checkerUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, user);

    exception.expect(TransportException.class);
    exception.expectMessage(
        String.format("Remote does not have %s available for fetch.", checkerRef));
    fetch(repo, checkerRef + ":checkerRef");
  }

  @Test
  public void checkerAdminsCannotReadCheckerRefsWithoutExplicitReadPermission() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ADMINISTRATE_CHECKERS);
    requestScopeOperations.setApiUser(user.getId());

    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = RefNames.refsCheckers(checkerUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, user);

    exception.expect(TransportException.class);
    exception.expectMessage(
        String.format("Remote does not have %s available for fetch.", checkerRef));
    fetch(repo, checkerRef + ":checkerRef");
  }
}
