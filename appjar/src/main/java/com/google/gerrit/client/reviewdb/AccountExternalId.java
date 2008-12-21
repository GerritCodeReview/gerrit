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

/** Association of an external account identifier to a local {@link Account}. */
public final class AccountExternalId {
  public static class Key implements com.google.gwtorm.client.Key<Account.Id> {
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

    public Account.Id getParentKey() {
      return accountId;
    }

    @Override
    public int hashCode() {
      return accountId.hashCode() * 31 + externalId.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      return o instanceof Key && ((Key) o).accountId.equals(accountId)
          && ((Key) o).externalId.equals(externalId);
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

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

  /** Get local id of this account, to link with in other entities */
  public Account.Id getAccountId() {
    return key.accountId;
  }
}
