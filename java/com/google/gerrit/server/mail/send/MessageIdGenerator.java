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

package com.google.gerrit.server.mail.send;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.RepoView;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** A generator class that creates a {@link MessageId} */
public class MessageIdGenerator {
  private final GitRepositoryManager repositoryManager;
  private final AllUsersName allUsersName;

  @Inject
  public MessageIdGenerator(GitRepositoryManager repositoryManager, AllUsersName allUsersName) {
    this.repositoryManager = repositoryManager;
    this.allUsersName = allUsersName;
  }

  /**
   * A unique id used which is a part of the header of all emails sent through by Gerrit. All of the
   * emails are sent via {@link OutgoingEmail#send()}.
   */
  @AutoValue
  public abstract static class MessageId {
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
  public MessageId fromChangeUpdate(RepoView repoView, PatchSet.Id patchsetId) {
    return fromChangeUpdateAndReason(repoView, patchsetId, null);
  }

  public MessageId fromChangeUpdateAndReason(
      RepoView repoView, PatchSet.Id patchsetId, @Nullable String reason) {
    String suffix = (reason != null) ? ("-" + reason) : "";
    String metaRef = patchsetId.changeId().toRefPrefix() + "meta";
    Optional<ObjectId> metaSha1;
    try {
      metaSha1 = repoView.getRef(metaRef);
    } catch (IOException ex) {
      throw new StorageException("unable to extract info for Message-Id", ex);
    }
    return metaSha1
        .map(optional -> new AutoValue_MessageIdGenerator_MessageId(optional.getName() + suffix))
        .orElseThrow(() -> new IllegalStateException(metaRef + " doesn't exist"));
  }

  public MessageId fromChangeUpdate(Project.NameKey project, PatchSet.Id patchsetId) {
    String metaRef = patchsetId.changeId().toRefPrefix() + "meta";
    Ref ref = getRef(metaRef, project);
    checkState(ref != null, metaRef + " must exist");
    return new AutoValue_MessageIdGenerator_MessageId(ref.getObjectId().getName());
  }

  /**
   * Create a {@link MessageId} as a result of an account update
   *
   * @return {@link MessageId} that depends on the account id.
   */
  public MessageId fromAccountUpdate(Account.Id accountId) {
    String userRef = RefNames.refsUsers(accountId);
    Ref ref = getRef(userRef, allUsersName);
    checkState(ref != null, userRef + " must exist");
    return new AutoValue_MessageIdGenerator_MessageId(ref.getObjectId().getName());
  }

  /**
   * Create a {@link MessageId} from a mail message.
   *
   * @param mailMessage The message that was sent but was rejected.
   * @return MessageId that depends on the MailMessage that was rejected.
   */
  public MessageId fromMailMessage(MailMessage mailMessage) {
    return new AutoValue_MessageIdGenerator_MessageId(mailMessage.id() + "-REJECTION");
  }

  /**
   * Create a {@link MessageId} from a reason, Account.Id, and timestamp.
   *
   * @param reason for performing this account update
   * @return MessageId that depends on the reason, accountId, and timestamp.
   */
  public MessageId fromReasonAccountIdAndTimestamp(
      String reason, Account.Id accountId, Instant timestamp) {
    return new AutoValue_MessageIdGenerator_MessageId(
        reason + "-" + accountId.toString() + "-" + timestamp.toString());
  }

  private Ref getRef(String userRef, Project.NameKey project) {
    try (Repository repository = repositoryManager.openRepository(project)) {
      return repository.getRefDatabase().findRef(userRef);
    } catch (IOException ex) {
      throw new StorageException("unable to extract info for Message-Id", ex);
    }
  }
}
