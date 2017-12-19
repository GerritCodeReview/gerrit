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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import java.util.Collection;
import java.util.Optional;

/**
 * Class to prepare updates to an account.
 *
 * <p>The getters in this class and the setters in the {@link Builder} correspond to fields in
 * {@link Account}. The account ID and the registration date cannot be updated.
 */
@AutoValue
public abstract class InternalAccountUpdate {
  public static Builder builder() {
    return new Builder.WrapperThatConvertsNullStringArgsToEmptyStrings(
        new AutoValue_InternalAccountUpdate.Builder());
  }

  /**
   * Returns the new value for the full name.
   *
   * @return the new value for the full name, {@code Optional#empty()} if the full name is not being
   *     updated, {@code Optional#of("")} if the full name is unset, the wrapped value is never
   *     {@code null}
   */
  public abstract Optional<String> getFullName();

  /**
   * Returns the new value for the preferred email.
   *
   * @return the new value for the preferred email, {@code Optional#empty()} if the preferred email
   *     is not being updated, {@code Optional#of("")} if the preferred email is unset, the wrapped
   *     value is never {@code null}
   */
  public abstract Optional<String> getPreferredEmail();

  /**
   * Returns the new value for the active flag.
   *
   * @return the new value for the active flag, {@code Optional#empty()} if the active flag is not
   *     being updated, the wrapped value is never {@code null}
   */
  public abstract Optional<Boolean> getActive();

  /**
   * Returns the new value for the status.
   *
   * @return the new value for the status, {@code Optional#empty()} if the status is not being
   *     updated, {@code Optional#of("")} if the status is unset, the wrapped value is never {@code
   *     null}
   */
  public abstract Optional<String> getStatus();

  /**
   * Returns external IDs that should be newly created for the account.
   *
   * @return external IDs that should be newly created for the account
   */
  public abstract ImmutableSet<ExternalId> getCreatedExternalIds();

  /**
   * Returns external IDs that should be updated for the account.
   *
   * @return external IDs that should be updated for the account
   */
  public abstract ImmutableSet<ExternalId> getUpdatedExternalIds();

  /**
   * Returns external IDs that should be deleted for the account.
   *
   * @return external IDs that should be deleted for the account
   */
  public abstract ImmutableSet<ExternalId> getDeletedExternalIds();

  /**
   * Class to build an account update.
   *
   * <p>Account data is only updated if the corresponding setter is invoked. If a setter is not
   * invoked the corresponding data stays unchanged. To unset string values the setter can be
   * invoked with either {@code null} or an empty string ({@code null} is converted to an empty
   * string by using the {@link WrapperThatConvertsNullStringArgsToEmptyStrings} wrapper, see {@link
   * InternalAccountUpdate#builder()}).
   */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets a new full name for the account.
     *
     * @param fullName the new full name, if {@code null} or empty string the full name is unset
     * @return the builder
     */
    public abstract Builder setFullName(String fullName);

    /**
     * Sets a new preferred email for the account.
     *
     * @param preferredEmail the new preferred email, if {@code null} or empty string the preferred
     *     email is unset
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
     * @param status the new status, if {@code null} or empty string the status is unset
     * @return the builder
     */
    public abstract Builder setStatus(String status);

    /**
     * Returns a builder for the set of created external IDs.
     *
     * @return builder for the set of created external IDs.
     */
    abstract ImmutableSet.Builder<ExternalId> createdExternalIdsBuilder();

    /**
     * Adds a new external ID for the account.
     *
     * <p>The account ID of the external ID must match the account ID of the account that is
     * updated.
     *
     * <p>If an external ID with the same ID already exists the account update will fail with {@link
     * DuplicateExternalIdKeyException}.
     *
     * @param extId external ID that should be added
     * @return the builder
     */
    public Builder addExternalId(ExternalId extId) {
      return addExternalIds(ImmutableSet.of(extId));
    }

    /**
     * Adds new external IDs for the account.
     *
     * <p>The account IDs of the external IDs must match the account ID of the account that is
     * updated.
     *
     * <p>If any of the external ID keys already exists, the insert fails with {@link
     * DuplicateExternalIdKeyException}.
     *
     * @param extIds external IDs that should be added
     * @return the builder
     */
    public Builder addExternalIds(Collection<ExternalId> extIds) {
      createdExternalIdsBuilder().addAll(extIds);
      return this;
    }

    /**
     * Returns a builder for the set of updated external IDs.
     *
     * @return builder for the set of updated external IDs.
     */
    abstract ImmutableSet.Builder<ExternalId> updatedExternalIdsBuilder();

    /**
     * Updates an external ID for the account.
     *
     * <p>The account ID of the external ID must match the account ID of the account that is
     * updated.
     *
     * <p>If no external ID with the ID exists the external ID is created.
     *
     * @param extId external ID that should be updated
     * @return the builder
     */
    public Builder updateExternalId(ExternalId extId) {
      return updateExternalIds(ImmutableSet.of(extId));
    }

    /**
     * Updates external IDs for the account.
     *
     * <p>The account IDs of the external IDs must match the account ID of the account that is
     * updated.
     *
     * <p>If any of the external IDs already exists, it is overwritten. New external IDs are
     * inserted.
     *
     * @param extIds external IDs that should be updated
     * @return the builder
     */
    public Builder updateExternalIds(Collection<ExternalId> extIds) {
      updatedExternalIdsBuilder().addAll(extIds);
      return this;
    }

    /**
     * Returns a builder for the set of deleted external IDs.
     *
     * @return builder for the set of deleted external IDs.
     */
    abstract ImmutableSet.Builder<ExternalId> deletedExternalIdsBuilder();

    /**
     * Deletes an external ID for the account.
     *
     * <p>The account ID of the external ID must match the account ID of the account that is
     * updated.
     *
     * <p>If no external ID with the ID exists this is a no-op.
     *
     * @param extId external ID that should be deleted
     * @return the builder
     */
    public Builder deleteExternalId(ExternalId extId) {
      return deleteExternalIds(ImmutableSet.of(extId));
    }

    /**
     * Delete external IDs for the account.
     *
     * <p>The account IDs of the external IDs must match the account ID of the account that is
     * updated.
     *
     * <p>For non-existing external IDs this is a no-op.
     *
     * @param extIds external IDs that should be deleted
     * @return the builder
     */
    public Builder deleteExternalIds(Collection<ExternalId> extIds) {
      deletedExternalIdsBuilder().addAll(extIds);
      return this;
    }

    /**
     * Replaces an external ID.
     *
     * @param extIdToDelete external ID that should be deleted
     * @param extIdToAdd external ID that should be added
     * @return the builder
     */
    public Builder replaceExternalId(ExternalId extIdToDelete, ExternalId extIdToAdd) {
      return replaceExternalIds(ImmutableSet.of(extIdToDelete), ImmutableSet.of(extIdToAdd));
    }

    /**
     * Replaces an external IDs.
     *
     * @param extIdsToDelete external IDs that should be deleted
     * @param extIdsToAdd external IDs that should be added
     * @return the builder
     */
    public Builder replaceExternalIds(
        Collection<ExternalId> extIdsToDelete, Collection<ExternalId> extIdsToAdd) {
      return deleteExternalIds(extIdsToDelete).addExternalIds(extIdsToAdd);
    }

    /**
     * Builds the account update.
     *
     * @return the account update
     */
    public abstract InternalAccountUpdate build();

    /**
     * Wrapper for {@link Builder} that converts {@code null} string arguments to empty strings for
     * all setter methods. This allows us to treat setter invocations with a {@code null} string
     * argument as signal to unset the corresponding field. E.g. for a builder method {@code
     * setX(String)} the following semantics apply:
     *
     * <ul>
     *   <li>Method is not invoked: X stays unchanged, X is stored as {@code Optional.empty()}.
     *   <li>Argument is a non-empty string Y: X is updated to the Y, X is stored as {@code
     *       Optional.of(Y)}.
     *   <li>Argument is an empty string: X is unset, X is stored as {@code Optional.of("")}
     *   <li>Argument is {@code null}: X is unset, X is stored as {@code Optional.of("")} (since the
     *       wrapper converts {@code null} to an empty string)
     * </ul>
     *
     * Without the wrapper calling {@code setX(null)} would fail with a {@link
     * NullPointerException}. Hence all callers would need to take care to call {@link
     * Strings#nullToEmpty(String)} for all string arguments and likely it would be forgotten in
     * some places.
     *
     * <p>This means the stored values are interpreted like this:
     *
     * <ul>
     *   <li>{@code Optional.empty()}: property stays unchanged
     *   <li>{@code Optional.of(<non-empty-string>)}: property is updated
     *   <li>{@code Optional.of("")}: property is unset
     * </ul>
     *
     * This wrapper forwards all method invocations to the wrapped {@link Builder} instance that was
     * created by AutoValue. For methods that return the AutoValue {@link Builder} instance the
     * return value is replaced with the wrapper instance so that all chained calls go through the
     * wrapper.
     */
    private static class WrapperThatConvertsNullStringArgsToEmptyStrings extends Builder {
      private final Builder delegate;

      private WrapperThatConvertsNullStringArgsToEmptyStrings(Builder delegate) {
        this.delegate = delegate;
      }

      @Override
      public Builder setFullName(String fullName) {
        delegate.setFullName(Strings.nullToEmpty(fullName));
        return this;
      }

      @Override
      public Builder setPreferredEmail(String preferredEmail) {
        delegate.setPreferredEmail(Strings.nullToEmpty(preferredEmail));
        return this;
      }

      @Override
      public Builder setActive(boolean active) {
        delegate.setActive(active);
        return this;
      }

      @Override
      public Builder setStatus(String status) {
        delegate.setStatus(Strings.nullToEmpty(status));
        return this;
      }

      @Override
      public InternalAccountUpdate build() {
        return delegate.build();
      }

      @Override
      ImmutableSet.Builder<ExternalId> createdExternalIdsBuilder() {
        return delegate.createdExternalIdsBuilder();
      }

      @Override
      public Builder addExternalIds(Collection<ExternalId> extIds) {
        delegate.addExternalIds(extIds);
        return this;
      }

      @Override
      ImmutableSet.Builder<ExternalId> updatedExternalIdsBuilder() {
        return delegate.updatedExternalIdsBuilder();
      }

      @Override
      public Builder updateExternalIds(Collection<ExternalId> extIds) {
        delegate.updateExternalIds(extIds);
        return this;
      }

      @Override
      ImmutableSet.Builder<ExternalId> deletedExternalIdsBuilder() {
        return delegate.deletedExternalIdsBuilder();
      }

      @Override
      public Builder deleteExternalIds(Collection<ExternalId> extIds) {
        delegate.deleteExternalIds(extIds);
        return this;
      }
    }
  }
}
