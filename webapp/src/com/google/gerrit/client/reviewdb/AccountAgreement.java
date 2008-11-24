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

import java.sql.Timestamp;

/** Electronic acceptance of a {@link ContributorAgreement} by {@link Account} */
public final class AccountAgreement {
  public static final String STATUS_NEW = "NEW";
  public static final String STATUS_VERIFIED = "VERIFIED";
  public static final String STATUS_REJECTED = "REJECTED";

  public static class Key implements com.google.gwtorm.client.Key<Account.Id> {
    @Column
    protected Account.Id accountId;

    @Column
    protected ContributorAgreement.Id claId;

    protected Key() {
      accountId = new Account.Id();
      claId = new ContributorAgreement.Id();
    }

    public Key(final Account.Id account, final ContributorAgreement.Id cla) {
      this.accountId = account;
      this.claId = cla;
    }

    public Account.Id getParentKey() {
      return accountId;
    }

    @Override
    public int hashCode() {
      return accountId.hashCode() * 31 + claId.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      return o instanceof Key && ((Key) o).accountId.equals(accountId)
          && ((Key) o).claId.equals(claId);
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  @Column
  protected Timestamp acceptedOn;

  @Column(length = 8)
  protected String status;

  @Column(notNull = false)
  protected Account.Id reviewedBy;

  @Column(notNull = false)
  protected Timestamp reviewedOn;

  protected AccountAgreement() {
  }

  public AccountAgreement(final AccountAgreement.Key k) {
    key = k;
    acceptedOn = new Timestamp(System.currentTimeMillis());
    status = STATUS_NEW;
  }

  public Timestamp getAcceptedOn() {
    return acceptedOn;
  }

  public String getStatus() {
    return status;
  }

  public Timestamp getReviewedOn() {
    return reviewedOn;
  }

  public Account.Id getReviewedBy() {
    return reviewedBy;
  }

  public void review(final String newStatus, final Account.Id by) {
    status = newStatus;
    reviewedBy = by;
    reviewedOn = new Timestamp(System.currentTimeMillis());
  }
}
