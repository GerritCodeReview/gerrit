// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.integration.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class NoAccessSameAsNotFoundIT extends StandaloneSiteTest {
  private static final String PASSWORD = "secret";
  private static final String REPO = "foo";

  @Inject private @GerritServerConfig Config config;
  @Inject private AccountCreator accountCreator;
  @Inject private GerritApi gApi;
  @Inject private ProjectOperations projectOperations;

  private String repoUrl;

  @Test
  public void sameResponseForNonExistingAndNonAccessibleRepo() throws Exception {
    try (ServerContext ctx = startServer()) {
      setup(ctx);

      // clone non-existing REPO
      String nonExistingResponse = cloneRepo();

      // create REPO and make it non-accessible then clone it again
      createNonAccessibleRepo();
      String noAccessResponse = cloneRepo();

      // make sure the response is identical in both cases
      assertThat(noAccessResponse).isEqualTo(nonExistingResponse);
    }
  }

  private void setup(ServerContext ctx) throws Exception {
    ctx.getInjector().injectMembers(this);

    TestAccount user = accountCreator.user1();
    gApi.accounts().id(user.id().get()).setHttpPassword(PASSWORD);

    String canonical = config.getString("gerrit", null, "canonicalweburl");
    repoUrl =
        String.format(
            "http://%s:%s@%s/a/%s", user.username(), PASSWORD, canonical.substring(7), REPO);
  }

  private String cloneRepo() {
    try {
      return execute(ImmutableList.of("git", "clone", repoUrl));
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  private void createNonAccessibleRepo() throws RestApiException {
    // Create project
    Project.NameKey project = Project.nameKey(REPO);
    gApi.projects().create(project.get());

    // Block access for everyone
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(SystemGroupBackend.ANONYMOUS_USERS))
        .update();
  }

  private String execute(ImmutableList<String> cmd) throws Exception {
    return execute(cmd, sitePaths.data_dir.toFile(), ImmutableMap.of());
  }
}
