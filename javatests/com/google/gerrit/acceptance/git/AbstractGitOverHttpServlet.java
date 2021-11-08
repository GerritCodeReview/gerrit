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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.FakeGroupAuditService;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.entities.Account;
import com.google.gerrit.pgm.http.jetty.JettyServer;
import com.google.gerrit.server.audit.HttpAuditEvent;
import com.google.inject.Inject;
import java.util.Optional;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;
import org.junit.Test;

public class AbstractGitOverHttpServlet extends AbstractPushForReview {
  @Inject protected FakeGroupAuditService auditService;
  private JettyServer jettyServer;

  @Before
  public void beforeEach() throws Exception {
    jettyServer = server.getHttpdInjector().getInstance(JettyServer.class);
    CredentialsProvider.setDefault(
        new UsernamePasswordCredentialsProvider(admin.username(), admin.httpPassword()));
    selectProtocol(AbstractPushForReview.Protocol.HTTP);
    // Don't clear audit events here, since we can't guarantee all test setup has run yet.
  }

  @Test
  @Sandboxed
  public void receivePackAuditEventLog() throws Exception {
    auditService.drainHttpAuditEvents();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master"))
        .call();

    ImmutableList<HttpAuditEvent> auditEvents = auditService.drainHttpAuditEvents();
    assertThat(auditEvents).hasSize(2);

    HttpAuditEvent lsRemote = auditEvents.get(0);
    assertThat(lsRemote.who.getAccountId()).isEqualTo(admin.id());
    assertThat(lsRemote.what).endsWith("/info/refs?service=git-receive-pack");
    assertThat(lsRemote.params).containsExactly("service", "git-receive-pack");
    assertThat(lsRemote.httpStatus).isEqualTo(HttpServletResponse.SC_OK);

    HttpAuditEvent receivePack = auditEvents.get(1);
    assertThat(receivePack.who.getAccountId()).isEqualTo(admin.id());
    assertThat(receivePack.what).endsWith("/git-receive-pack");
    assertThat(receivePack.params).isEmpty();
    assertThat(receivePack.httpStatus).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(jettyServer.numActiveSessions()).isEqualTo(0);
  }

  @Test
  public void anonymousUploadPackAuditEventLog() throws Exception {
    uploadPackAuditEventLog(Constants.DEFAULT_REMOTE_NAME, Optional.empty());
  }

  @Test
  public void authenticatedUploadPackAuditEventLog() throws Exception {
    String remote = "authenticated";
    Config cfg = testRepo.git().getRepository().getConfig();

    String uri = admin.getHttpUrl(server) + "/a/" + project.get();
    cfg.setString("remote", remote, "url", uri);
    cfg.setString("remote", remote, "fetch", "+refs/heads/*:refs/remotes/origin/*");

    uploadPackAuditEventLog(remote, Optional.of(admin.id()));
  }

  private void uploadPackAuditEventLog(String remote, Optional<Account.Id> accountId)
      throws Exception {
    auditService.drainHttpAuditEvents();
    // testRepo is already a clone. Make a server-side change so we have something to fetch.
    // testRepo will also close the wrapped repo
    Repository repo = repoManager.openRepository(project);
    try (TestRepository<?> testRepo = new TestRepository<>(repo)) {
      testRepo.branch("master").commit().create();
    }
    testRepo.git().fetch().setRemote(remote).call();

    ImmutableList<HttpAuditEvent> auditEvents = auditService.drainHttpAuditEvents();
    assertThat(auditEvents).hasSize(2);

    HttpAuditEvent lsRemote = auditEvents.get(0);
    assertThat(lsRemote.who.toString())
        .isEqualTo(
            accountId.map(id -> "IdentifiedUser[account " + id.get() + "]").orElse("ANONYMOUS"));
    assertThat(lsRemote.what).endsWith("/info/refs?service=git-upload-pack");
    assertThat(lsRemote.params).containsExactly("service", "git-upload-pack");
    assertThat(lsRemote.httpStatus).isEqualTo(HttpServletResponse.SC_OK);

    HttpAuditEvent uploadPack = auditEvents.get(1);
    assertThat(uploadPack.who.toString())
        .isEqualTo(
            accountId.map(id -> "IdentifiedUser[account " + id.get() + "]").orElse("ANONYMOUS"));
    assertThat(uploadPack.what).endsWith("/git-upload-pack");
    assertThat(uploadPack.params).isEmpty();
    assertThat(uploadPack.httpStatus).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(jettyServer.numActiveSessions()).isEqualTo(0);
  }
}
