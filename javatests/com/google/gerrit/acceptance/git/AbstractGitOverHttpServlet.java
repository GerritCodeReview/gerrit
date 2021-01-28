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

  /**
   * As of today only fetch Protocol V2 is supported on the git client.
   * https://git.eclipse.org/r/c/jgit/jgit/+/172595
   */
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

  /**
   * Git client use Protocol V2 fetch by default, see https://git.eclipse.org/r/c/jgit/jgit/+/172595
   * See {@code org.eclipse.jgit.transport.BasePackFetchConnection#doFetchV2} for the negotiation
   * details.
   */
  @Test
  @Sandboxed
  public void uploadPackAuditEventLog() throws Exception {
    auditService.drainHttpAuditEvents();
    // testRepo is already a clone. Make a server-side change so we have something to fetch.
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<?> testRepo = new TestRepository<>(repo)) {
      testRepo.branch("master").commit().create();
    }

    testRepo.git().fetch().call();

    ImmutableList<HttpAuditEvent> auditEvents = auditService.drainHttpAuditEvents();
    assertThat(auditEvents).hasSize(4);

    // Protocol V2 Capability advertisement
    // https://git-scm.com/docs/protocol-v2#_capability_advertisement
    HttpAuditEvent infoRef = auditEvents.get(0);

    // JGit supports shared session during fetch and push, see
    // org.eclipse.jgit.transport.http.HttpConnectionFactory2.GitSession, thus the authentication
    // details are carried over from the first git clone request.
    assertThat(infoRef.who.getAccountId()).isEqualTo(admin.id());
    assertThat(infoRef.what).endsWith("/info/refs?service=git-upload-pack");
    assertThat(infoRef.params).containsExactly("service", "git-upload-pack");
    assertThat(infoRef.httpStatus).isEqualTo(HttpServletResponse.SC_OK);

    // Smart service negotiations, as described here
    // https://git-scm.com/docs/http-protocol#_smart_service_git_upload_pack
    // Protocol V2 client sends command=ls-ref https://git-scm.com/docs/protocol-v2#_ls_refs
    // followed by command=fetch, thus the request may overflow see
    // org.eclipse.jgit.transport.MultiRequestService
    HttpAuditEvent uploadPackLsRef = auditEvents.get(1);

    assertThat(uploadPackLsRef.what).endsWith("/git-upload-pack");
    assertThat(uploadPackLsRef.params).isEmpty();
    assertThat(uploadPackLsRef.httpStatus).isEqualTo(HttpServletResponse.SC_OK);
    HttpAuditEvent uploadPackFetch = auditEvents.get(2);

    assertThat(uploadPackFetch.what).endsWith("/git-upload-pack");
    assertThat(uploadPackFetch.params).isEmpty();
    assertThat(uploadPackFetch.httpStatus).isEqualTo(HttpServletResponse.SC_OK);
    HttpAuditEvent uploadPackDone = auditEvents.get(3);

    assertThat(uploadPackDone.what).endsWith("/git-upload-pack");
    assertThat(uploadPackDone.params).isEmpty();
    assertThat(uploadPackDone.httpStatus).isEqualTo(HttpServletResponse.SC_OK);
  }
}
