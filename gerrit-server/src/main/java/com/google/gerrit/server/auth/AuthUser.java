// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.auth;

import static org.parboiled.common.Preconditions.checkNotNull;

import javax.annotation.Nullable;

/**
 * An authenticated user as specified by the AuthBackend.
 */
public final class AuthUser {

  /**
   * Globally unique identifier for the user.
   */
  public final static class UUID {
    private final String uuid;

    /**
     * A new unique identifier.
     *
     * @param uuid the unique identifier.
     */
    public UUID(String uuid) {
      this.uuid = checkNotNull(uuid);
    }

    /** @return the globally unique identifier. */
    public String getUUID() {
      return uuid;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof UUID) {
        return getUUID().equals(((UUID) obj).getUUID());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return getUUID().hashCode();
    }

    @Override
    public String toString() {
      return String.format("AuthUser.UUID[%s]", getUUID());
    }
  }

  /**
   * Builder for an AuthUser.
   */
  public final static class Builder {

    private UUID uuid;
    private String userName;

    /**
     * A new builder for an AuthUser.
     *
     * @param uuid the globally unique ID.
     */
    public Builder(UUID uuid) {
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

  private final UUID uuid;
  private final String userName;

  private AuthUser(Builder builder) {
    this.uuid = builder.uuid;
    this.userName = builder.userName;
  }

  /** @return the globally unique identifier. */
  public UUID getUUID() {
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

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof AuthUser) {
      return getUUID().equals(((AuthUser) obj).getUUID());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getUUID().hashCode();
  }

  public String toString() {
    return String.format("AuthUser[uuid=%s, userName=%s]", getUUID(),
        getUserName());
  }
}
