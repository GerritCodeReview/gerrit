// Copyright (C) 2017 The Android Open Source Project
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
// limitations under the Licens

package com.google.gerrit.server.account;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Class to prepare updates to an account. */
@AutoValue
public abstract class InternalAccountUpdate {
  public static Builder builder() {
    return new AutoValue_InternalAccountUpdate.Builder();
  }

  /**
   * Returns the new value for the full name.
   *
   * @return the new value for the full name, {@code Optional#empty()} if the full name is not being
   *     updated
   */
  public abstract Optional<String> getFullName();

  /**
   * Returns the new value for the preferred email.
   *
   * @return the new value for the preferred email, {@code Optional#empty()} if the preferred email
   *     is not being updated
   */
  public abstract Optional<String> getPreferredEmail();

  /**
   * Returns the new value for the active flag.
   *
   * @return the new value for the active flag, {@code Optional#empty()} if the active flag is not
   *     being updated
   */
  public abstract Optional<Boolean> getActive();

  /**
   * Returns the new value for the status.
   *
   * @return the new value for the status, {@code Optional#empty()} if the status is not being
   *     updated
   */
  public abstract Optional<String> getStatus();

  /**
   * Class to build an account update.
   *
   * <p>Account data is only updated if the corresponding setter is invoked. If a setter is not
   * invoked that corresponding data stays unchanged. To unset string values the setter must be
   * called with an empty string (not {@code null}).
   */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets a new full name for the account.
     *
     * @param fullName the new full name, use an empty string to unset the full name, must not be
     *     {@code null}
     * @return the builder
     */
    public abstract Builder setFullName(String fullName);

    /**
     * Sets a new preferred email for the account.
     *
     * @param preferredEmail the new preferred email, use an empty string to unset the preferred
     *     email, must not be {@code null}
     * @return the builder
     */
    public abstract Builder setPreferredEmail(String preferredEmail);

    /**
     * Sets the active flag for the account.
     *
     * @param active {@code true} if the account should be set to active, {@code false} if the
     *     account should be set to inactive
     * @return the builder
     */
    public abstract Builder setActive(boolean active);

    /**
     * Sets a new status for the account.
     *
     * @param status the new status, use an empty string to unset the status, must not be {@code
     *     null}
     * @return the builder
     */
    public abstract Builder setStatus(String status);

    /**
     * Builds the account update.
     *
     * @return the account update
     */
    public abstract InternalAccountUpdate build();
  }
}
