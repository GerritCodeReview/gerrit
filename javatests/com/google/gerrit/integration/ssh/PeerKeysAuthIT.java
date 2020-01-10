// Copyright (C) 2020 The Android Open Source Project
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
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.Version;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import org.junit.Test;

@NoHttpd
@UseSsh
public class PeerKeysAuthIT extends StandaloneSiteTest {
  private static final String[] SSH_KEYGEN_CMD =
      new String[] {"ssh-keygen", "-t", "rsa", "-q", "-P", "", "-f", "id_rsa"};
  private static final String[] SSH_COMMAND =
      new String[] {
        "ssh",
        "-o",
        "UserKnownHostsFile=/dev/null",
        "-o",
        "StrictHostKeyChecking=no",
        "-o",
        "IdentitiesOnly=yes",
        "-i"
      };

  @Inject private @TestSshServerAddress InetSocketAddress sshAddress;

  @Test
  public void test() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      // Generate private/public key for user
      execute(ImmutableList.<String>builder().add(SSH_KEYGEN_CMD).build());

      String[] parts =
          new String(Files.readAllBytes(sitePaths.data_dir.resolve("id_rsa.pub")), UTF_8)
              .split(" ");

      // Loose algorithm at index 0, verify the format: "key comment"
      Files.write(
          sitePaths.peer_keys, String.format("%s %s", parts[1], parts[2]).getBytes(ISO_8859_1));
      assertContent(execGerritVersionCommand());

      // Only preserve the key material: no algorithm and no comment
      Files.write(sitePaths.peer_keys, parts[1].getBytes(ISO_8859_1));
      assertContent(execGerritVersionCommand());

      // Wipe out the content of the peer keys file
      Files.delete(sitePaths.peer_keys);
      assertThrows(IOException.class, () -> execGerritVersionCommand());
    }
  }

  private String execGerritVersionCommand() throws Exception {
    return execute(
        ImmutableList.<String>builder()
            .add(SSH_COMMAND)
            .add(sitePaths.data_dir.resolve("id_rsa").toString())
            .add("-p " + sshAddress.getPort())
            .add(PeerDaemonUser.USER_NAME + "@" + sshAddress.getHostName())
            .add("suexec")
            .add("--as")
            .add("admin")
            .add("--")
            .add("gerrit")
            .add("version")
            .build());
  }

  private String execute(ImmutableList<String> cmd) throws Exception {
    return execute(cmd, sitePaths.data_dir.toFile(), ImmutableMap.of());
  }

  private static void assertContent(String result) {
    assertThat(result).contains("gerrit version " + Version.getVersion());
  }
}
