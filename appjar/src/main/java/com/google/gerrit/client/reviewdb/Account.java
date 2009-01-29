// Copyright 2008 Google Inc.
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

/** Preferences and information about a single user. */
public final class Account {
  /** Default number of lines of context. */
  public static final short DEFAULT_CONTEXT = 10;

  /** Typical valid choices for the default context setting. */
  public static final short[] CONTEXT_CHOICES = {3, 10, 25, 50, 75, 100};

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

  /** Default number of lines of context when viewing a patch. */
  @Column
  protected short defaultContext;

  /** Should the site header be displayed when logged in ? */
  @Column
  protected boolean showSiteHeader;

  /** Non-Internet based contact details for the account's owner. */
  @Column(notNull = false)
  protected ContactInformation contact;

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
    defaultContext = DEFAULT_CONTEXT;
    showSiteHeader = true;
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

  /** Get the default number of lines of context when viewing a patch. */
  public short getDefaultContext() {
    return defaultContext;
  }

  /** Set the number of lines of context when viewing a patch. */
  public void setDefaultContext(final short s) {
    defaultContext = s;
  }

  public boolean isShowSiteHeader() {
    return showSiteHeader;
  }

  public void setShowSiteHeader(final boolean b) {
    showSiteHeader = b;
  }

  public ContactInformation getContactInformation() {
    return contact;
  }

  public void setContactInformation(final ContactInformation i) {
    contact = i;
  }
}
