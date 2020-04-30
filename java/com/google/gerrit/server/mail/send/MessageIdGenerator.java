package com.google.gerrit.server.mail.send;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class MessageIdGenerator {
  @Inject private GitRepositoryManager repositoryManager;
  @Inject private AllUsersName allUsersName;

  public MessageId fromChangeUpdate(RepoView repoView, PatchSet.Id patchsetId) {
    String metaRef = patchsetId.changeId().toRefPrefix() + "meta";
    try {
      Optional<ObjectId> metaSha1 = repoView.getRef(metaRef);
      if (metaSha1.isPresent()) {
        return new AutoValue_MessageId(metaSha1.get().getName());
      } else {
        throw new IllegalStateException(metaRef + " doesn't exist");
      }
    } catch (IOException ex) {
      throw new StorageException("unable to extract info for Message-Id", ex);
    }
  }

  public MessageId fromChangeUpdate(Project.NameKey project, PatchSet.Id patchsetId) {
    try (Repository repository = repositoryManager.openRepository(project)) {
      String metaRef = patchsetId.changeId().toRefPrefix() + "meta";
      Ref ref = repository.getRefDatabase().getRef(metaRef);
      if (ref != null) {
        return new AutoValue_MessageId(ref.getObjectId().getName());
      } else {
        throw new IllegalStateException(metaRef + " doesn't exist");
      }
    } catch (IOException ex) {
      throw new StorageException("unable to extract info for Message-Id", ex);
    }
  }

  public MessageId fromAccountUpdate(Account.Id accountId) {
    try (Repository repository = repositoryManager.openRepository(allUsersName)) {
      String prefixAccountId =
          (accountId.get() % 100 > 9)
              ? String.valueOf(accountId.get() % 100)
              : String.valueOf('0') + (accountId.get() % 100);
      String userRef =
          String.format("refs/users/%s/%s", prefixAccountId, String.valueOf(accountId));
      Ref ref = repository.getRefDatabase().findRef(userRef);
      if (ref != null) {
        return new AutoValue_MessageId(ref.getObjectId().getName());
      } else {
        throw new IllegalStateException(userRef + " doesn't exist");
      }
    } catch (IOException ex) {
      throw new StorageException("unable to extract info for Message-Id", ex);
    }
  }

  public MessageId fromMailMessage(MailMessage mailMessage) {
    return new AutoValue_MessageId(mailMessage.id());
  }

  public MessageId fromReasonAccountIdAndTimestamp(
      String reason, Account.Id accountId, Timestamp timestamp) {
    return new AutoValue_MessageId(
        reason + "-" + accountId.toString() + "-" + timestamp.toString());
  }
}
