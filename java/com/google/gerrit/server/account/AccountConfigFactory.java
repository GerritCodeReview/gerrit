package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class AccountConfigFactory {
  @ImplementedBy(ConfigImpl.class)
  public interface Config {
    boolean defaultNewAccountHidden();
  }

  /** Default implementation {@link Config} */
  @Singleton
  public static class ConfigImpl implements Config {
    private final boolean defaultNewAccountHidden;

    @Inject
    public ConfigImpl(AuthConfig authConfig) {
      this.defaultNewAccountHidden = authConfig.getDefaultNewAccountHidden();
    }

    @Override
    public boolean defaultNewAccountHidden() {
      return defaultNewAccountHidden;
    }
  }

  private final boolean defaultNewAccountHidden;

  @Inject
  public AccountConfigFactory(Config config) {
    this.defaultNewAccountHidden = config.defaultNewAccountHidden();
  }

  public AccountConfig create(
      Account.Id accountId, AllUsersName allUsersName, Repository allUsersRepo) {
    // This way the existing accounts, that do not have the property set, will also get a default.
    // Before enabling this property globally:
    // 1) The UI provides a way to explicitly set the property
    // 2) the backfill that sets hidden on existing accounts is run.
    // 3) The setting is enabled.

    return new AccountConfig(accountId, allUsersName, allUsersRepo, defaultNewAccountHidden);
  }
}
