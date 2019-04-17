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

package com.google.gerrit.reviewdb.client;

import static com.google.gerrit.reviewdb.client.RefNames.REFS_DRAFT_COMMENTS;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_STARRED_CHANGES;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS;

import com.google.auto.value.AutoValue;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import java.sql.Timestamp;
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
 *   <li>{@link AccountGroupMember}: membership of the user in a specific human managed {@link
 *       AccountGroup}. Multiple records can exist when the user is a member of more than one group.
 *   <li>AccountSshKey: user's public SSH keys, for authentication through the internal SSH daemon.
 *       One record per SSH key uploaded by the user, keys are checked in random order until a match
 *       is found.
 *   <li>{@link DiffPreferencesInfo}: user's preferences for rendering side-to-side and unified diff
 * </ul>
 */
public final class Account {
  public static Id id(int id) {
    return new AutoValue_Account_Id(id);
  }

  /** Key local to Gerrit to identify a user. */
  @AutoValue
  public abstract static class Id implements Comparable<Id> {
    /** Parse an Account.Id out of a string representation. */
    public static Optional<Id> tryParse(String str) {
      return Optional.ofNullable(Ints.tryParse(str)).map(Account::id);
    }

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
    public static Id fromRefPart(String name) {
      Integer id = RefNames.parseShardedRefPart(name);
      return id != null ? Account.id(id) : null;
    }

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
    public static Id fromRefSuffix(String name) {
      Integer id = RefNames.parseRefSuffix(name);
      return id != null ? Account.id(id) : null;
    }

    abstract int id();

    public int get() {
      return id();
    }

    @Override
    public int compareTo(Id o) {
      return Integer.compare(id(), o.id());
    }
  }

  private Id accountId;

  /** Date and time the user registered with the review server. */
  private Timestamp registeredOn;

  /** Full name of the user ("Given-name Surname" style). */
  private String fullName;

  /** Email address the user prefers to be contacted through. */
  private String preferredEmail;

  /**
   * Is this user inactive? This is used to avoid showing some users (eg. former employees) in
   * auto-suggest.
   */
  private boolean inactive;

  /** The user-settable status of this account (e.g. busy, OOO, available) */
  private String status;

  /** ID of the user branch from which the account was read. */
  private String metaId;

  protected Account() {}

  /**
   * Create a new account.
   *
   * @param newId unique id, see {@link com.google.gerrit.server.notedb.Sequences#nextAccountId()}.
   * @param registeredOn when the account was registered.
   */
  public Account(Account.Id newId, Timestamp registeredOn) {
    this.accountId = newId;
    this.registeredOn = registeredOn;
  }

  /** Get local id of this account, to link with in other entities */
  public Account.Id getId() {
    return accountId;
  }

  /** Get the full name of the user ("Given-name Surname" style). */
  public String getFullName() {
    return fullName;
  }

  /** Set the full name of the user ("Given-name Surname" style). */
  public void setFullName(String name) {
    if (name != null && !name.trim().isEmpty()) {
      fullName = name.trim();
    } else {
      fullName = null;
    }
  }

  /** Email address the user prefers to be contacted through. */
  public String getPreferredEmail() {
    return preferredEmail;
  }

  /** Set the email address the user prefers to be contacted through. */
  public void setPreferredEmail(String addr) {
    preferredEmail = addr;
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
    if (fullName != null) {
      return fullName;
    }
    if (preferredEmail != null) {
      return preferredEmail;
    }
    return getName(accountId);
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
   *   <li>{@code A U. Thor &lt;author@example.com&gt;}: full populated
   *   <li>{@code A U. Thor (12)}: missing email address
   *   <li>{@code Anonymous Coward &lt;author@example.com&gt;}: missing name
   *   <li>{@code Anonymous Coward (12)}: missing name and email address
   * </ul>
   */
  public String getNameEmail(String anonymousCowardName) {
    String name = fullName != null ? fullName : anonymousCowardName;
    StringBuilder b = new StringBuilder();
    b.append(name);
    if (preferredEmail != null) {
      b.append(" <");
      b.append(preferredEmail);
      b.append(">");
    } else {
      b.append(" (");
      b.append(accountId.get());
      b.append(")");
    }
    return b.toString();
  }

  /** Get the date and time the user first registered. */
  public Timestamp getRegisteredOn() {
    return registeredOn;
  }

  public String getMetaId() {
    return metaId;
  }

  public void setMetaId(String metaId) {
    this.metaId = metaId;
  }

  public boolean isActive() {
    return !inactive;
  }

  public void setActive(boolean active) {
    inactive = !active;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Account && ((Account) o).getId().equals(getId());
  }

  @Override
  public int hashCode() {
    return getId().get();
  }
}
