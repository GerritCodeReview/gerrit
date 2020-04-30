package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.mail.Address;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.time.Instant;
import javax.inject.Inject;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class MessageIdGeneratorIT extends AbstractDaemonTest {
  @Inject private MessageIdGenerator messageIdGenerator;

  @Test
  public void fromAccountUpdate() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String messageId = messageIdGenerator.fromAccountUpdate(admin.id()).id();
      String prefixAccountId =
          (admin.id().get() % 100 > 9)
              ? String.valueOf(admin.id().get() % 100)
              : String.valueOf('0') + (admin.id().get() % 100);
      String sha1 =
          repo.getRefDatabase()
              .getRef(String.format("refs/users/%s/%d", prefixAccountId, admin.id().get()))
              .getObjectId()
              .getName();
      assertThat(sha1).isEqualTo(messageId);
    }
  }

  @Test
  public void fromChangeUpdate() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      PushOneCommit.Result result = createChange();
      PatchSet.Id patchsetId = result.getChange().currentPatchSet().id();
      String messageId = messageIdGenerator.fromChangeUpdate(project, patchsetId).id();
      String sha1 =
          repo.getRefDatabase()
              .getRef(String.format("%smeta", patchsetId.changeId().toRefPrefix()))
              .getObjectId()
              .getName();
      assertThat(sha1).isEqualTo(messageId);
    }
  }

  @Test
  public void fromMailMessage() throws Exception {
    String id = "unique-id";
    MailMessage mailMessage =
        MailMessage.builder()
            .id(id)
            .from(Address.create("email@email.com"))
            .dateReceived(Instant.EPOCH)
            .subject("subject")
            .build();
    assertThat(messageIdGenerator.fromMailMessage(mailMessage).id()).isEqualTo(id);
  }

  @Test
  public void fromReasonAccountIdAndTimestamp() throws Exception {
    String reason = "reason";
    Timestamp timestamp = TimeUtil.nowTs();
    assertThat(
            messageIdGenerator.fromReasonAccountIdAndTimestamp(reason, admin.id(), timestamp).id())
        .isEqualTo(reason + "-" + admin.id().toString() + "-" + timestamp.toString());
  }
}
