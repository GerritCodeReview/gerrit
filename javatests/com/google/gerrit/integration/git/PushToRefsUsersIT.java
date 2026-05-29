// Copyright (C) 2022 The Android Open Source Project
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

// TODO(davido): In addition to push over HTTP also add a test for push over SSH
public class PushToRefsUsersIT extends StandaloneSiteTest {
  private static final String ADMIN_PASSWORD = "secret";
  private final String[] GIT_CLONE = new String[] {"git", "clone"};
  private final String[] GIT_FETCH_USERS_SELF =
      new String[] {"git", "fetch", "origin", "refs/users/self"};
  private final String[] GIT_CO_FETCH_HEAD = new String[] {"git", "checkout", "FETCH_HEAD"};
  private final String[] GIT_CONFIG_USER_EMAIL =
      new String[] {"git", "config", "user.email", "admin@example.com"};
  private final String[] GIT_CONFIG_USER_NAME =
      new String[] {"git", "config", "user.name", "Administrator"};
  private final String[] GIT_COMMIT = new String[] {"git", "commit", "-am", "OOO"};
  private final String[] GIT_PUSH_USERS_SELF =
      new String[] {"git", "push", "origin", "HEAD:refs/users/self"};

  @Inject private GerritApi gApi;
  @Inject private @GerritServerConfig Config config;
  @Inject private AllUsersName allUsersName;

  @Test
  public void testPushToRefsUsersOverHttp() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      // Setup admin password
      gApi.accounts().id(admin.id().get()).setHttpPassword(ADMIN_PASSWORD);

      // Get authenticated Git/HTTP URL
      String urlWithCredentials =
          config
              .getString("gerrit", null, "canonicalweburl")
              .replace("http://", "http://" + admin.username() + ":" + ADMIN_PASSWORD + "@");

      // Clone All-Users repository
      execute(
          ImmutableList.<String>builder()
              .add(GIT_CLONE)
              .add(urlWithCredentials + "/a/" + allUsersName)
              .add(sitePaths.data_dir.toFile().getAbsolutePath())
              .build(),
          sitePaths.site_path);

      // Fetch refs/users/self for admin user
      execute(GIT_FETCH_USERS_SELF);

      // Checkout FETCH_HEAD
      execute(GIT_CO_FETCH_HEAD);

      // Set admin user status to OOO
      Files.write(
          sitePaths.data_dir.resolve("account.config"),
          "  status = OOO".getBytes(UTF_8),
          StandardOpenOption.APPEND);

      // Set user email
      execute(GIT_CONFIG_USER_EMAIL);

      // Set user name
      execute(GIT_CONFIG_USER_NAME);

      // Commit
      execute(GIT_COMMIT);

      // Push
      assertThat(execute(GIT_PUSH_USERS_SELF)).contains("Processing changes: refs: 1, done");

      // Verify user status
      assertThat(gApi.accounts().id(admin.id().get()).detail().status).isEqualTo("OOO");
    }
  }

  private String execute(String... cmds) throws Exception {
    return execute(ImmutableList.<String>builder().add(cmds).build(), sitePaths.data_dir);
  }

  private String execute(ImmutableList<String> cmd, Path path) throws Exception {
    return execute(cmd, path.toFile(), ImmutableMap.of());
  }
}
