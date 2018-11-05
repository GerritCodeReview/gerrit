package com.google.gerrit.acceptance.git;

import com.google.gerrit.server.audit.AuditEvent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

public class GitOverHttpServletIT extends AbstractPushForReview {

  @Before
  public void setUp() throws Exception {
    CredentialsProvider.setDefault(
        new UsernamePasswordCredentialsProvider(admin.username, admin.httpPassword));
    selectProtocol(AbstractPushForReview.Protocol.HTTP);
    auditService.clearEvents();
  }

  @Test
  public void receivePackAuditEventLog() throws Exception {
    pushHead(testRepo, "refs/heads/stable", false);

    // Git smart protocol makes two requests:
    // https://github.com/git/git/blob/master/Documentation/technical/http-protocol.txt
    assertThat(auditService.auditEvents.size()).isEqualTo(2);

    AuditEvent e = auditService.auditEvents.get(1);
    assertThat(e.who.getAccountId()).isEqualTo(admin.id);
    assertThat(e.what).endsWith("/git-receive-pack");
    assertThat(e.params).isEmpty();
  }

  @Test
  public void uploadPackAuditEventLog() throws Exception {
    fetch(testRepo, "refs/heads/master");

    assertThat(auditService.auditEvents.size()).isEqualTo(1);

    AuditEvent e = auditService.auditEvents.get(0);
    assertThat(e.what).endsWith("git-upload-pack");
    assertThat(e.params.get("service")).containsExactlyElementsIn(new String[] {"git-upload-pack"});
  }
}
