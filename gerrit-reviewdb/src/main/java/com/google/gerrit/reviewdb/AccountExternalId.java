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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Association of an external account identifier to a local {@link Account}. */
public final class AccountExternalId {
  public static final String SCHEME_GERRIT = "gerrit:";
  public static final String SCHEME_UUID = "uuid:";
  public static final String SCHEME_MAILTO = "mailto:";
  public static final String LEGACY_GAE = "Google Account ";

  public static class Key extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column
    protected String externalId;

    protected Key() {
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

  @Column(name = Column.NONE)
  protected Key key;

  @Column
  protected Account.Id accountId;

  @Column(notNull = false)
  protected String emailAddress;

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

  public String getSchemeRest(final String scheme) {
    return isScheme(scheme) ? getExternalId().substring(scheme.length()) : null;
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
