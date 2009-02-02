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
import com.google.gwtorm.client.StringKey;

import java.sql.Timestamp;

/** Association of an external account identifier to a local {@link Account}. */
public final class AccountExternalId {
  public static class Key extends StringKey<Account.Id> {
    @Column
    protected Account.Id accountId;

    @Column
    protected String externalId;

    protected Key() {
      accountId = new Account.Id();
    }

    public Key(final Account.Id a, final String e) {
      accountId = a;
      externalId = e;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
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

  @Column(notNull = false)
  protected String emailAddress;

  @Column(notNull = false)
  protected Timestamp lastUsedOn;

  protected AccountExternalId() {
  }

  /**
   * Create a new binding to an external identity.
   * 
   * @param k the binding key.
   */
  public AccountExternalId(final AccountExternalId.Key k) {
    key = k;
  }

  public AccountExternalId.Key getKey() {
    return key;
  }

  /** Get local id of this account, to link with in other entities */
  public Account.Id getAccountId() {
    return key.accountId;
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

  public Timestamp getLastUsedOn() {
    return lastUsedOn;
  }

  public void setLastUsedOn() {
    lastUsedOn = new Timestamp(System.currentTimeMillis());
  }
}
