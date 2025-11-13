// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.entities;

import static com.google.gerrit.entities.RefNames.REFS_DRAFT_COMMENTS;
import static com.google.gerrit.entities.RefNames.REFS_STARRED_CHANGES;
import static com.google.gerrit.entities.RefNames.REFS_USERS;

import com.google.auto.value.AutoBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.ConvertibleToProto;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import java.time.Instant;
import java.util.Optional;

/**
 * Information about a single user.
 *
 * <p>A user may have multiple identities they can use to login to Gerrit (see ExternalId), but in
 * such cases they always map back to a single Account entity.
 *
 * <p>Entities "owned" by an Account (that is, their primary key contains the {@link Account.Id} key
 * as part of their key structure):
 *
 * <ul>
 *   <li>ExternalId: OpenID identities and email addresses known to be registered to this user.
 *       Multiple records can exist when the user has more than one public identity, such as a work
 *       and a personal email address.
 *   <li>AccountSshKey: user's public SSH keys, for authentication through the internal SSH daemon.
 *       One record per SSH key uploaded by the user, keys are checked in random order until a match
 *       is found.
 *   <li>{@link DiffPreferencesInfo}: user's preferences for rendering side-to-side and unified diff
 * </ul>
 *
 * @param id The unique identifier of the account.
 * @param registeredOn The date and time the user registered with the review server.
 * @param fullName The full name of the user ("Given-name Surname" style).
 * @param displayName An optional display name of the user to be shown in the UI.
 * @param preferredEmail The email address the user prefers to be contacted through.
 * @param inactive Is this user inactive? This is used to avoid showing some users (eg. former
 *     employees) in auto-suggest.
 * @param status The user-settable status of this account (e.g. busy, OOO, available)
 * @param metaId The ID of the user branch from which the account was read.
 * @param uniqueTag A unique tag which identifies the current version of the account. It can be any
 *     non-empty string. For open-source gerrit it is the same as metaId. The value can be null only
 *     during account updating/creation.
 */
public record Account(
    Id id,
    Instant registeredOn,
    @Nullable String fullName,
    @Nullable String displayName,
    @Nullable String preferredEmail,
    boolean inactive,
    @Nullable String status,
    @Nullable String metaId,
    @Nullable String uniqueTag) {

  /** Placeholder for indicating an account-id that does not correspond to any local account */
  public static final Id UNKNOWN_ACCOUNT_ID = id(0);

  public static Id id(int id) {
    return new Id(id);
  }

  /** Key local to Gerrit to identify a user. */
  @ConvertibleToProto
  public record Id(int id) implements Comparable<Id> {
    /** Parse an Account.Id out of a string representation. */
    public static Optional<Id> tryParse(String str) {
      return Optional.ofNullable(Ints.tryParse(str)).map(Account::id);
    }

    @Nullable
    public static Id fromRef(String name) {
      if (name == null) {
        return null;
      }
      if (name.startsWith(REFS_USERS)) {
        return fromRefPart(name.substring(REFS_USERS.length()));
      } else if (name.startsWith(REFS_DRAFT_COMMENTS)) {
        return parseAfterShardedRefPart(name.substring(REFS_DRAFT_COMMENTS.length()));
      } else if (name.startsWith(REFS_STARRED_CHANGES)) {
        return parseAfterShardedRefPart(name.substring(REFS_STARRED_CHANGES.length()));
      }
      return null;
    }

    /**
     * Parse an Account.Id out of a part of a ref-name.
     *
     * @param name a ref name with the following syntax: {@code "34/1234..."}. We assume that the
     *     caller has trimmed any prefix.
     */
    @Nullable
    public static Id fromRefPart(String name) {
      Integer id = RefNames.parseShardedRefPart(name);
      return id != null ? Account.id(id) : null;
    }

    @Nullable
    public static Id parseAfterShardedRefPart(String name) {
      Integer id = RefNames.parseAfterShardedRefPart(name);
      return id != null ? Account.id(id) : null;
    }

    /**
     * Parse an Account.Id out of the last part of a ref name.
     *
     * <p>The input is a ref name of the form {@code ".../1234"}, where the suffix is a non-sharded
     * account ID. Ref names using a sharded ID should use {@link #fromRefPart(String)} instead for
     * greater safety.
     *
     * @param name ref name
     * @return account ID, or null if not numeric.
     */
    @Nullable
    public static Id fromRefSuffix(String name) {
      Integer id = RefNames.parseRefSuffix(name);
      return id != null ? Account.id(id) : null;
    }

    public int get() {
      return id();
    }

    @Override
    public int compareTo(Id o) {
      return Integer.compare(id(), o.id());
    }

    @Override
    public String toString() {
      return Integer.toString(get());
    }
  }

  /**
   * Create a new account.
   *
   * @param newId unique id, see Sequences#nextAccountId().
   * @param registeredOn when the account was registered.
   */
  public static Account.Builder builder(Account.Id newId, Instant registeredOn) {
    return new AutoBuilder_Account_Builder()
        .setInactive(false)
        .setId(newId)
        .setRegisteredOn(registeredOn);
  }

  /**
   * Formats an account name.
   *
   * <p>The return value goes into NoteDb commits and audit logs, so it should not be changed.
   *
   * <p>This method deliberately does not use {@code Anonymous Coward} because it can be changed
   * using a {@code gerrit.config} option which is a problem for NoteDb commits that still refer to
   * a previously defined value.
   *
   * @return the fullname, if present, otherwise the preferred email, if present, as a last resort a
   *     generic string containing the accountId.
   */
  public String getName() {
    if (fullName() != null) {
      return fullName();
    }
    if (preferredEmail() != null) {
      return preferredEmail();
    }
    return getName(id());
  }

  public static String getName(Account.Id accountId) {
    return "GerritAccount #" + accountId.get();
  }

  /**
   * Get the name and email address.
   *
   * <p>Example output:
   *
   * <ul>
   *   <li>{@code A U. Thor <author@example.com>}: full populated
   *   <li>{@code A U. Thor (12)}: missing email address
   *   <li>{@code Anonymous Coward <author@example.com>}: missing name
   *   <li>{@code Anonymous Coward (12)}: missing name and email address
   * </ul>
   */
  public String getNameEmail(String anonymousCowardName) {
    String name = fullName() != null ? fullName() : anonymousCowardName;
    StringBuilder b = new StringBuilder();
    b.append(name);
    if (preferredEmail() != null) {
      b.append(" <");
      b.append(preferredEmail());
      b.append(">");
    } else {
      b.append(" (");
      b.append(id().get());
      b.append(")");
    }
    return b.toString();
  }

  public boolean isActive() {
    return !inactive();
  }

  public Builder toBuilder() {
    return new AutoBuilder_Account_Builder(this);
  }

  @AutoBuilder
  public abstract static class Builder {
    public abstract Id id();

    abstract Builder setId(Id id);

    public abstract Instant registeredOn();

    abstract Builder setRegisteredOn(Instant registeredOn);

    @Nullable
    public abstract String fullName();

    public abstract Builder setFullName(String fullName);

    @Nullable
    public abstract String displayName();

    public abstract Builder setDisplayName(String displayName);

    @Nullable
    public abstract String preferredEmail();

    public abstract Builder setPreferredEmail(String preferredEmail);

    public abstract boolean inactive();

    public abstract Builder setInactive(boolean inactive);

    @CanIgnoreReturnValue
    public Builder setActive(boolean active) {
      return setInactive(!active);
    }

    @Nullable
    public abstract String status();

    public abstract Builder setStatus(String status);

    @Nullable
    public abstract String metaId();

    public abstract Builder setMetaId(@Nullable String metaId);

    @Nullable
    public abstract String uniqueTag();

    public abstract Builder setUniqueTag(@Nullable String uniqueTag);

    public abstract Account build();
  }

  @Override
  public String toString() {
    return getName();
  }

  public String debugString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id())
        .add("registeredOn", registeredOn())
        .add("fullName", fullName())
        .add("displayName", displayName())
        .add("preferredEmail", preferredEmail())
        .add("inactive", inactive())
        .add("status", status())
        .add("metaId", metaId())
        .add("uniqueTag", uniqueTag())
        .toString();
  }
}
