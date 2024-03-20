package com.google.gerrit.server.mail.send;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.server.update.RepoView;
import java.time.Instant;

public interface MessageIdGenerator {
  /**
   * A unique id used which is a part of the header of all emails sent through by Gerrit. All of the
   * emails are sent via {@link OutgoingEmail#send()}.
   */
  @AutoValue
  abstract class MessageId {
    public abstract String id();

    public static MessageId create(String id) {
      return new AutoValue_MessageIdGenerator_MessageId(id);
    }
  }

  /**
   * Create a {@link MessageId} as a result of a change update.
   *
   * @return MessageId that depends on the patchset.
   */
  MessageId fromChangeUpdate(RepoView repoView, PatchSet.Id patchsetId);

  MessageId fromChangeUpdateAndReason(
      RepoView repoView, PatchSet.Id patchsetId, @Nullable String reason);

  MessageId fromChangeUpdate(Project.NameKey project, PatchSet.Id patchsetId);

  /**
   * Create a {@link MessageId} as a result of an account update
   *
   * @return {@link MessageId} that depends on the account id.
   */
  MessageId fromAccountUpdate(Account.Id accountId);

  /**
   * Create a {@link MessageId} from a mail message.
   *
   * @param mailMessage The message that was sent but was rejected.
   * @return MessageId that depends on the MailMessage that was rejected.
   */
  MessageId fromMailMessage(MailMessage mailMessage);

  /**
   * Create a {@link MessageId} from a reason, Account.Id, and timestamp.
   *
   * @param reason for performing this account update
   * @return MessageId that depends on the reason, accountId, and timestamp.
   */
  MessageId fromReasonAccountIdAndTimestamp(String reason, Account.Id accountId, Instant timestamp);
}
