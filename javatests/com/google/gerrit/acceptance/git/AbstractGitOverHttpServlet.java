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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.pgm.http.jetty.JettyServer;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.AuditEvent;
import java.util.Collections;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;
import org.junit.Test;

public class AbstractGitOverHttpServlet extends AbstractPushForReview {
  private JettyServer jettyServer;

  @Before
  public void beforeEach() throws Exception {
    jettyServer = server.getHttpdInjector().getInstance(JettyServer.class);
    CredentialsProvider.setDefault(
        new UsernamePasswordCredentialsProvider(admin.username, admin.httpPassword));
    selectProtocol(AbstractPushForReview.Protocol.HTTP);
    auditService.clearEvents();
  }

  @Test
  public void receivePackAuditEventLog() throws Exception {
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master"))
        .call();

    // Git smart protocol makes two requests:
    // https://github.com/git/git/blob/master/Documentation/technical/http-protocol.txt
    assertThat(auditService.auditEvents.size()).isEqualTo(2);

    AuditEvent e = auditService.auditEvents.get(1);
    assertThat(e.who.getAccountId()).isEqualTo(admin.id);
    assertThat(e.what).endsWith("/git-receive-pack");
    assertThat(e.params).isEmpty();
    assertThat(jettyServer.numActiveSessions()).isEqualTo(0);
  }

  @Test
  public void anonymousUploadPackAuditEventLog() throws Exception {
    String remote = "anonymous";
    Config cfg = testRepo.git().getRepository().getConfig();

    String uri = server.getUrl() + "/" + project.get();
    cfg.setString("remote", remote, "url", uri);
    cfg.setString("remote", remote, "fetch", "+refs/heads/*:refs/remotes/origin/*");

    uploadPackAuditEventLog(remote, Optional.empty());
  }

  @Test
  public void authenticatedUploadPackAuditEventLog() throws Exception {
    String remote = "authenticated";
    Config cfg = testRepo.git().getRepository().getConfig();

    String uri = admin.getHttpUrl(server) + "/a/" + project.get();
    cfg.setString("remote", remote, "url", uri);
    cfg.setString("remote", remote, "fetch", "+refs/heads/*:refs/remotes/origin/*");

    uploadPackAuditEventLog(remote, Optional.of(admin.getId()));
  }

  private void uploadPackAuditEventLog(String remote, Optional<Account.Id> accountId)
      throws Exception {
    auditService.clearEvents();
    testRepo.git().fetch().setRemote(remote).call();

    assertThat(auditService.auditEvents.size()).isEqualTo(1);

    AuditEvent e = auditService.auditEvents.get(0);
    assertThat(e.who.toString())
        .isEqualTo(
            accountId.map(id -> "IdentifiedUser[account " + id.get() + "]").orElse("ANONYMOUS"));
    assertThat(e.params.get("service"))
        .containsExactlyElementsIn(Collections.singletonList("git-upload-pack"));
    assertThat(e.what).endsWith("service=git-upload-pack");
    assertThat(jettyServer.numActiveSessions()).isEqualTo(0);
  }
}
