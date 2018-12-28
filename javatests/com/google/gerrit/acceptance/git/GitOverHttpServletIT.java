// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.server.AuditEvent;
import com.google.gerrit.server.audit.HttpAuditEvent;
import java.util.Collections;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;
import org.junit.Test;

public class GitOverHttpServletIT extends AbstractPushForReview {
  private static final long AUDIT_EVENT_TIMEOUT = 500L;

  @Before
  public void beforeEach() throws Exception {
    CredentialsProvider.setDefault(
        new UsernamePasswordCredentialsProvider(admin.username, admin.httpPassword));
    selectProtocol(AbstractPushForReview.Protocol.HTTP);
    auditService.clearEvents();
  }

  @Test
  @Sandboxed
  public void receivePackAuditEventLog() throws Exception {
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master"))
        .call();
    waitForAudit();

    // Git smart protocol makes two requests:
    // https://github.com/git/git/blob/master/Documentation/technical/http-protocol.txt
    assertThat(auditService.auditEvents.size()).isEqualTo(2);

    AuditEvent e = auditService.auditEvents.get(1);
    assertThat(e.who.getAccountId()).isEqualTo(admin.id);
    assertThat(e.what).endsWith("/git-receive-pack");
    assertThat(e.params).isEmpty();
    assertThat(((HttpAuditEvent) e).httpStatus).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  @Sandboxed
  public void uploadPackAuditEventLog() throws Exception {
    testRepo.git().fetch().call();
    waitForAudit();

    assertThat(auditService.auditEvents.size()).isEqualTo(1);

    AuditEvent e = auditService.auditEvents.get(0);
    assertThat(e.who.toString()).isEqualTo("ANONYMOUS");
    assertThat(e.params.get("service"))
        .containsExactlyElementsIn(Collections.singletonList("git-upload-pack"));
    assertThat(e.what).endsWith("service=git-upload-pack");
    assertThat(((HttpAuditEvent) e).httpStatus).isEqualTo(HttpServletResponse.SC_OK);
  }

  private void waitForAudit() throws InterruptedException {
    synchronized (auditService.auditEvents) {
      auditService.auditEvents.wait(AUDIT_EVENT_TIMEOUT);
    }
  }
}
