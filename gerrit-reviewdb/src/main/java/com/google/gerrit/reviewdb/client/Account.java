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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS;

import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;

import java.sql.Timestamp;

/**
 * Information about a single user.
 * <p>
 * A user may have multiple identities they can use to login to Gerrit (see
 * {@link AccountExternalId}), but in such cases they always map back to a
 * single Account entity.
 * <p>
 * Entities "owned" by an Account (that is, their primary key contains the
 * {@link Account.Id} key as part of their key structure):
 * <ul>
 *
 * <li>{@link AccountExternalId}: OpenID identities and email addresses known to
 * be registered to this user. Multiple records can exist when the user has more
 * than one public identity, such as a work and a personal email address.</li>
 *
 * <li>{@link AccountGroupMember}: membership of the user in a specific human
 * managed {@link AccountGroup}. Multiple records can exist when the user is a
 * member of more than one group.</li>
 *
 * <li>{@link AccountProjectWatch}: user's email settings related to a specific
 * {@link Project}. One record per project the user is interested in tracking.</li>
 *
 * <li>{@link AccountSshKey}: user's public SSH keys, for authentication through
 * the internal SSH daemon. One record per SSH key uploaded by the user, keys
 * are checked in random order until a match is found.</li>
 *
 * <li>{@link StarredChange}: user has starred the change, tracking
 * notifications of updates on that change, or just book-marking it for faster
 * future reference. One record per starred change.</li>
 *
 * <li>{@link DiffPreferencesInfo}: user's preferences for rendering side-to-side
 * and unified diff</li>
 *
 * </ul>
 */
public final class Account {
  public static enum FieldName {
    FULL_NAME, USER_NAME, REGISTER_NEW_EMAIL
  }

  public static final String USER_NAME_PATTERN_FIRST = "[a-zA-Z0-9]";
  public static final String USER_NAME_PATTERN_REST = "[a-zA-Z0-9._-]";
  public static final String USER_NAME_PATTERN_LAST = "[a-zA-Z0-9]";

  /** Regular expression that {@link #userName} must match. */
  public static final String USER_NAME_PATTERN = "^" + //
      "(" + //
      USER_NAME_PATTERN_FIRST + //
      USER_NAME_PATTERN_REST + "*" + //
      USER_NAME_PATTERN_LAST + //
      "|" + //
      USER_NAME_PATTERN_FIRST + //
      ")" + //
      "$";

  /** Key local to Gerrit to identify a user. */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }

    /** Parse an Account.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }

    public static Id fromRef(String name) {
      if (name == null) {
        return null;
      }
      if (name.startsWith(REFS_USERS)) {
        return fromRefPart(name.substring(REFS_USERS.length()));
      }
      return null;
    }

    /**
     * Parse an Account.Id out of a part of a ref-name.
     *
     * @param name  a ref name with the following syntax: {@code "34/1234..."}.
     *              We assume that the caller has trimmed any prefix.
     */
    public static Id fromRefPart(String name) {
      if (name == null) {
        return null;
      }

      String[] parts = name.split("/");
      int n = parts.length;
      if (n < 2) {
        return null;
      }

      // Last 2 digits.
      int le;
      for (le = 0; le < parts[0].length(); le++) {
        if (!Character.isDigit(parts[0].charAt(le))) {
          return null;
        }
      }
      if (le != 2) {
        return null;
      }

      // Full ID.
      int ie;
      for (ie = 0; ie < parts[1].length(); ie++) {
        if (!Character.isDigit(parts[1].charAt(ie))) {
          if (ie == 0) {
            return null;
          } else {
            break;
          }
        }
      }

      int shard = Integer.parseInt(parts[0]);
      int id = Integer.parseInt(parts[1].substring(0, ie));

      if (id % 100 != shard) {
        return null;
      }
      return new Account.Id(id);
    }
  }

  @Column(id = 1)
  protected Id accountId;

  /** Date and time the user registered with the review server. */
  @Column(id = 2)
  protected Timestamp registeredOn;

  /** Full name of the user ("Given-name Surname" style). */
  @Column(id = 3, notNull = false)
  protected String fullName;

  /** Email address the user prefers to be contacted through. */
  @Column(id = 4, notNull = false)
  protected String preferredEmail;

  // DELETED: id = 5 (contactFiledOn)

  // DELETED: id = 6 (generalPreferences)

  /** Is this user active */
  @Column(id = 7)
  protected boolean inactive;

  /** <i>computed</i> the username selected from the identities. */
  protected String userName;

  /** <i>stored in git, used for caching</i> the user's preferences. */
  private AccountGeneralPreferencesInfo generalPreferences;

  protected Account() {
  }

  /**
   * Create a new account.
   *
   * @param newId unique id, see
   *        {@link com.google.gerrit.reviewdb.server.ReviewDb#nextAccountId()}.
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
  public void setFullName(final String name) {
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
  public void setPreferredEmail(final String addr) {
    preferredEmail = addr;
  }

  /** Get the date and time the user first registered. */
  public Timestamp getRegisteredOn() {
    return registeredOn;
  }

  public AccountGeneralPreferencesInfo getGeneralPreferencesInfo() {
    return generalPreferences;
  }

  public void setGeneralPreferences(AccountGeneralPreferencesInfo p) {
    generalPreferences = p;
  }

  public boolean isActive() {
    return ! inactive;
  }

  public void setActive(boolean active) {
    inactive = ! active;
  }

  /** @return the computed user name for this account */
  public String getUserName() {
    return userName;
  }

  /** Update the computed user name property. */
  public void setUserName(final String userName) {
    this.userName = userName;
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
