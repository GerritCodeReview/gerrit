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
import com.google.gwtorm.client.StringKey;

/** Association of an external account identifier to a local {@link Account}. */
public final class AccountExternalId {
  /**
   * Scheme used for {@link AuthType#LDAP}, {@link AuthType#HTTP},
   * {@link AuthType#HTTP_LDAP}, and {@link AuthType#LDAP_BIND} usernames.
   * <p>
   * The name {@code gerrit:} was a very poor choice.
   */
  public static final String SCHEME_GERRIT = "gerrit:";

  /** Scheme used for INTERNAL authentication */
  public static final String SCHEME_INTERNAL = "internal:";

  /** Scheme used for randomly created identities constructed by a UUID. */
  public static final String SCHEME_UUID = "uuid:";

  /** Scheme used to represent only an email address. */
  public static final String SCHEME_MAILTO = "mailto:";

  /** Scheme for the username used to authenticate an account, e.g. over SSH. */
  public static final String SCHEME_USERNAME = "username:";

  /** Very old scheme from Gerrit Code Review 1.x imports. */
  public static final String LEGACY_GAE = "Google Account ";

  public static class Key extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String externalId;

    protected Key() {
    }

    public Key(String scheme, final String identity) {
      if (!scheme.endsWith(":")) {
        scheme += ":";
      }
      externalId = scheme + identity;
    }

    public Key(final String e) {
      externalId = e;
    }

    @Override
    public String get() {
      return externalId;
    }

    @Override
    protected void set(String newValue) {
      externalId = newValue;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected Account.Id accountId;

  @Column(id = 3, notNull = false)
  protected String emailAddress;

  @Column(id = 4, notNull = false)
  protected String password;

  /** <i>computed value</i> is this identity trusted by the site administrator? */
  protected boolean trusted;

  /** <i>computed value</i> can this identity be removed from the account? */
  protected boolean canDelete;

  protected AccountExternalId() {
  }

  /**
   * Create a new binding to an external identity.
   *
   * @param who the account this binds to.
   * @param k the binding key.
   */
  public AccountExternalId(final Account.Id who, final AccountExternalId.Key k) {
    accountId = who;
    key = k;
  }

  public AccountExternalId.Key getKey() {
    return key;
  }

  /** Get local id of this account, to link with in other entities */
  public Account.Id getAccountId() {
    return accountId;
  }

  public String getExternalId() {
    return key.externalId;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(final String e) {
    emailAddress = e;
  }

  public boolean isScheme(final String scheme) {
    final String id = getExternalId();
    return id != null && id.startsWith(scheme);
  }

  public String getSchemeRest() {
    String id = getExternalId();
    int c = id.indexOf(':');
    return 0 < c ? id.substring(c + 1) : null;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String p) {
    password = p;
  }

  public boolean isTrusted() {
    return trusted;
  }

  public void setTrusted(final boolean t) {
    trusted = t;
  }

  public boolean canDelete() {
    return canDelete;
  }

  public void setCanDelete(final boolean t) {
    canDelete = t;
  }
}
