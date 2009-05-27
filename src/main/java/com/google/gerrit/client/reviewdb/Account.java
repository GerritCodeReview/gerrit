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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;

import java.sql.Timestamp;
import java.util.List;

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
 * </ul>
 */
public final class Account {
  /**
   * Locate exactly one account matching the name or name/email string.
   * 
   * @param db open database handle to use for the query.
   * @param nameOrEmail a string of the format
   *        "Full Name &lt;email@example&gt;", or just the preferred email
   *        address ("email@example"), or a full name.
   * @return the single account that matches; null if no account matches or
   *         there are multiple candidates.
   */
  public static Account find(final ReviewDb db, final String nameOrEmail)
      throws OrmException {
    final int lt = nameOrEmail.indexOf('<');
    final int gt = nameOrEmail.indexOf('>');
    if (lt >= 0 && gt > lt) {
      final String email = nameOrEmail.substring(lt + 1, gt);
      return one(db.accounts().byPreferredEmail(email));
    }

    if (nameOrEmail.contains("@")) {
      return one(db.accounts().byPreferredEmail(nameOrEmail));
    }

    return one(db.accounts().suggestByFullName(nameOrEmail, nameOrEmail, 2));
  }

  private static Account one(final ResultSet<Account> rs) {
    final List<Account> r = rs.toList();
    return r.size() == 1 ? r.get(0) : null;
  }

  /** Key local to Gerrit to identify a user. */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column
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

  @Column
  protected Id accountId;

  /** Date and time the user registered with the review server. */
  @Column
  protected Timestamp registeredOn;

  /** Full name of the user ("Given-name Surname" style). */
  @Column(notNull = false)
  protected String fullName;

  /** Email address the user prefers to be contacted through. */
  @Column(notNull = false)
  protected String preferredEmail;

  /**
   * Username to authenticate as through SSH connections.
   * <p>
   * Note that this property tracks {@link #preferredEmail}.
   */
  @Column(notNull = false)
  protected String sshUserName;

  /** When did the user last give us contact information? Null if never. */
  @Column(notNull = false)
  protected Timestamp contactFiledOn;

  /** This user's preferences */
  @Column(name = Column.NONE)
  protected AccountGeneralPreferences generalPreferences;

  protected Account() {
  }

  /**
   * Create a new account.
   * 
   * @param newId unique id, see {@link ReviewDb#nextAccountId()}.
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
    fullName = name;
  }

  /** Email address the user prefers to be contacted through. */
  public String getPreferredEmail() {
    return preferredEmail;
  }

  /** Set the email address the user prefers to be contacted through. */
  public void setPreferredEmail(final String addr) {
    if (addr != null && addr.contains("@")) {
      sshUserName = addr.substring(0, addr.indexOf('@')).toLowerCase();
    } else {
      sshUserName = null;
    }
    preferredEmail = addr;
  }

  /** Get the name the user logins as through SSH. */
  public String getSshUserName() {
    return sshUserName;
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
}
