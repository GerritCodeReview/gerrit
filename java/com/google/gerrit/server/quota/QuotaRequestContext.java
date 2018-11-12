package com.google.gerrit.server.quota;

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import java.util.Optional;

@AutoValue
public abstract class QuotaRequestContext {
  private static final QuotaRequestContext defaults =
      new AutoValue_QuotaRequestContext.Builder()
          .user(new AnonymousUser())
          .project(Optional.empty())
          .change(Optional.empty())
          .account(Optional.empty())
          .build();

  public static Builder builder() {
    return defaults.toBuilder();
  }

  public abstract CurrentUser user();

  public abstract Optional<Project.NameKey> project();

  public abstract Optional<Change.Id> change();

  public abstract Optional<Account.Id> account();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract QuotaRequestContext.Builder user(CurrentUser user);

    public abstract QuotaRequestContext.Builder project(Optional<Project.NameKey> project);

    public abstract QuotaRequestContext.Builder change(Optional<Change.Id> change);

    public abstract QuotaRequestContext.Builder account(Optional<Account.Id> account);

    public abstract QuotaRequestContext build();
  }
}
