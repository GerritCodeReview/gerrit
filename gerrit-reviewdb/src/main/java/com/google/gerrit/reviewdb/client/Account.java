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

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;

import java.sql.Timestamp;

/**
 * Information about a single user.
 * <p>
 * A user may have multiple identities they can use to login to Gerrit (see
 * {@link AccountExternalId}), but in such cases they always map back to a
 * single Account entity.
 *<p>
 * Entities "owned" by an Account (that is, their primary key contains the
 * {@link Account.Id} key as part of their key structure):
 * <ul>
 * <li>{@link AccountAgreement}: any record of the user's acceptance of a
 * predefined {@link ContributorAgreement}. Multiple records indicate
 * potentially multiple agreements, especially if agreements must be retired and
 * replaced with new agreements.</li>
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
 * <li>{@link AccountDiffPreference}: user's preferences for rendering side-to-side
 * and unified diff</li>
 *
 * </ul>
 */
public final class Account {
  public static enum FieldName {
    FULL_NAME, USER_NAME, REGISTER_NEW_EMAIL;
  }

  public static final String USER_NAME_PATTERN_FIRST = "[a-zA-Z]";
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

  /** When did the user last give us contact information? Null if never. */
  @Column(id = 5, notNull = false)
  protected Timestamp contactFiledOn;

  /** This user's preferences */
  @Column(id = 6, name = Column.NONE)
  protected AccountGeneralPreferences generalPreferences;

  /** Is this user active */
  @Column(id = 7)
  protected boolean inactive;

  /** <i>computed</i> the username selected from the identities. */
  protected String userName;

  protected Account() {
  }

  /**
   * Create a new account.
   *
   * @param newId unique id, see
   *        {@link com.google.gerrit.reviewdb.server.ReviewDb#nextAccountId()}.
   */
  public Account(final Account.Id newId) {
    accountId = newId;
    registeredOn = new Timestamp(System.currentTimeMillis());

    generalPreferences = new AccountGeneralPreferences();
    generalPreferences.resetToDefaults();
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
      fullName = name;
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

  public AccountGeneralPreferences getGeneralPreferences() {
    return generalPreferences;
  }

  public void setGeneralPreferences(final AccountGeneralPreferences p) {
    generalPreferences = p;
  }

  public boolean isContactFiled() {
    return contactFiledOn != null;
  }

  public Timestamp getContactFiledOn() {
    return contactFiledOn;
  }

  public void setContactFiled() {
    contactFiledOn = new Timestamp(System.currentTimeMillis());
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
}
