package com.google.gerrit.server.auth;

import static org.parboiled.common.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.AccountExternalId;

import javax.annotation.Nullable;

/**
 * An authenticated user as specified by the AuthBackend.
 */
public final class AuthUser {

  /**
   * Builder for an AuthUser.
   */
  public final class Builder {

    private AccountExternalId.Key uuid;
    private String userName;

    /**
     * A new builder for an AuthUser.
     *
     * @param uuid the globally unique ID.
     */
    public Builder(AccountExternalId.Key uuid) {
      this.uuid = checkNotNull(uuid);
    }

    /**
     * Sets the optional backend specific user name.
     *
     * @param userName the user name.
     * @return the builder.
     */
    public Builder setUserName(String userName) {
      this.userName = userName;
      return this;
    }

    /** @return a new AuthUser. */
    public AuthUser build() {
      return new AuthUser(this);
    }
  }

  private final AccountExternalId.Key uuid;
  private final String userName;

  private AuthUser(Builder builder) {
    this.uuid = builder.uuid;
    this.userName = builder.userName;
  }

  /** @return the globally unique identifier. */
  public AccountExternalId.Key getUUID() {
    return uuid;
  }

  /** @return the backend specific user name, or null if one does not exist. */
  @Nullable
  public String getUserName() {
    return userName;
  }

  /** @return {@code true} if {@link #getUserName()} is not null. */
  public boolean hasUserName() {
    return getUserName() != null;
  }
}
