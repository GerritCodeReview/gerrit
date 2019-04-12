// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.audit.HttpAuditEvent;
import com.google.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;
import org.junit.Test;

public class HttpPushForReviewIT extends AbstractPushForReview {
  @Inject private FakeGroupAuditService auditService;

  @Before
  public void selectHttpUrl() throws Exception {
    CredentialsProvider.setDefault(
        new UsernamePasswordCredentialsProvider(admin.username(), admin.httpPassword()));
    selectProtocol(Protocol.HTTP);
    // Don't clear audit events here, since we can't guarantee all test setup has run yet.
  }

  @Test
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
  }

  @Test
  public void uploadPackAuditEventLog() throws Exception {
    auditService.drainHttpAuditEvents();
    // testRepo is already a clone. Make a server-side change so we have something to fetch.
    try (Repository repo = repoManager.openRepository(project)) {
      new TestRepository<>(repo).branch("master").commit().create();
    }
    testRepo.git().fetch().call();

    ImmutableList<HttpAuditEvent> auditEvents = auditService.drainHttpAuditEvents();
    assertThat(auditEvents).hasSize(2);

    HttpAuditEvent lsRemote = auditEvents.get(0);
    // Repo URL doesn't include /a, so fetching doesn't cause authentication.
    assertThat(lsRemote.who).isInstanceOf(AnonymousUser.class);
    assertThat(lsRemote.what).endsWith("/info/refs?service=git-upload-pack");
    assertThat(lsRemote.params).containsExactly("service", "git-upload-pack");
    assertThat(lsRemote.httpStatus).isEqualTo(HttpServletResponse.SC_OK);

    HttpAuditEvent uploadPack = auditEvents.get(1);
    assertThat(lsRemote.who).isInstanceOf(AnonymousUser.class);
    assertThat(uploadPack.what).endsWith("/git-upload-pack");
    assertThat(uploadPack.params).isEmpty();
    assertThat(uploadPack.httpStatus).isEqualTo(HttpServletResponse.SC_OK);
  }
}
