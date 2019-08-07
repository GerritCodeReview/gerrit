package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIds;
import java.io.IOException;

/** Result of an {@link com.google.gerrit.server.account.AccountsUpdate.AccountUpdate} */
@AutoValue
public abstract class UpdatedAccount {
  public abstract ImmutableSet<ExternalId> externalIds();

  public abstract String message();

  public abstract AccountConfig accountConfig();

  public abstract ExternalIdNotes extIdNotes();

  private boolean created;

  private UpdatedAccount(
      ExternalIds externalIds,
      String message,
      AccountConfig accountConfig,
      ExternalIdNotes extIdNotes) {
    checkState(!Strings.isNullOrEmpty(message), "message for account update must be set");
    this.externalIds = requireNonNull(externalIds);
    this.message = requireNonNull(message);
    this.accountConfig = requireNonNull(accountConfig);
    this.extIdNotes = requireNonNull(extIdNotes);
  }

  public String getMessage() {
    return message;
  }

  public AccountConfig getAccountConfig() {
    return accountConfig;
  }

  public AccountState getAccount() throws IOException {
    return AccountState.fromAccountConfig(externalIds(), accountConfig(), extIdNotes()).get();
  }

  public ExternalIdNotes getExternalIdNotes() {
    return extIdNotes;
  }

  public void setCreated(boolean created) {
    this.created = created;
  }

  public boolean isCreated() {
    return created;
  }
}
