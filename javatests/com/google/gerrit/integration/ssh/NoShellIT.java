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

package com.google.gerrit.integration.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.Test;

@NoHttpd
@UseSsh
public class NoShellIT extends StandaloneSiteTest {
  private static final String[] SSH_KEYGEN_CMD =
      new String[] {"ssh-keygen", "-t", "rsa", "-q", "-P", "", "-f"};

  @Inject private GerritApi gApi;
  @Inject private @TestSshServerAddress InetSocketAddress sshAddress;

  private String identityPath;

  @Test(timeout = 60000)
  public void verifyCommandsIsClosed() throws Exception {
    try (ServerContext ctx = startServer()) {
      setUpTestHarness(ctx);

      IOException thrown = assertThrows(IOException.class, () -> execute(cmd()));
      assertThat(thrown)
          .hasMessageThat()
          .contains("Hi Administrator, you have successfully connected over SSH.");
    }
  }

  private void setUpTestHarness(ServerContext ctx) throws Exception {
    ctx.getInjector().injectMembers(this);
    setUpAuthentication();
    identityPath = sitePaths.data_dir.resolve(String.format("id_rsa_%s", "admin")).toString();
  }

  private void setUpAuthentication() throws Exception {
    execute(
        ImmutableList.<String>builder()
            .add(SSH_KEYGEN_CMD)
            .add(String.format("id_rsa_%s", "admin"))
            .build());
    gApi.accounts()
        .id(admin.id().get())
        .addSshKey(
            new String(
                java.nio.file.Files.readAllBytes(
                    sitePaths.data_dir.resolve(String.format("id_rsa_%s.pub", "admin"))),
                UTF_8));
  }

  private ImmutableList<String> cmd() {
    return ImmutableList.<String>builder()
        .add("ssh")
        .add("-tt")
        .add("-o")
        .add("StrictHostKeyChecking=no")
        .add("-o")
        .add("UserKnownHostsFile=/dev/null")
        .add("-p")
        .add(String.valueOf(sshAddress.getPort()))
        .add("admin@" + sshAddress.getHostName())
        .add("-i")
        .add(identityPath)
        .build();
  }

  private String execute(ImmutableList<String> cmd) throws Exception {
    return execute(cmd, sitePaths.data_dir.toFile(), ImmutableMap.of());
  }
}
