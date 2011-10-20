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
import com.google.gwtorm.client.CompoundKey;

import java.sql.Timestamp;

/** Electronic acceptance of a {@link ContributorAgreement} by {@link Account} */
public final class AccountAgreement implements AbstractAgreement {
  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Account.Id accountId;

    @Column(id = 2)
    protected ContributorAgreement.Id claId;

    protected Key() {
      accountId = new Account.Id();
      claId = new ContributorAgreement.Id();
    }

    public Key(final Account.Id account, final ContributorAgreement.Id cla) {
      this.accountId = account;
      this.claId = cla;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    public ContributorAgreement.Id getContributorAgreementId() {
      return claId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {claId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected Timestamp acceptedOn;

  @Column(id = 3)
  protected char status;

  @Column(id = 4, notNull = false)
  protected Account.Id reviewedBy;

  @Column(id = 5, notNull = false)
  protected Timestamp reviewedOn;

  @Column(id = 6, notNull = false, length = Integer.MAX_VALUE)
  protected String reviewComments;

  protected AccountAgreement() {
  }

  public AccountAgreement(final AccountAgreement.Key k) {
    key = k;
    acceptedOn = new Timestamp(System.currentTimeMillis());
    status = Status.NEW.getCode();
  }

  public AccountAgreement.Key getKey() {
    return key;
  }

  public ContributorAgreement.Id getAgreementId() {
    return key.claId;
  }

  public Timestamp getAcceptedOn() {
    return acceptedOn;
  }

  public Status getStatus() {
    return Status.forCode(status);
  }

  public Timestamp getReviewedOn() {
    return reviewedOn;
  }

  public Account.Id getReviewedBy() {
    return reviewedBy;
  }

  public String getReviewComments() {
    return reviewComments;
  }

  public void setReviewComments(final String s) {
    reviewComments = s;
  }

  public void review(final Status newStatus, final Account.Id by) {
    status = newStatus.getCode();
    reviewedBy = by;
    reviewedOn = new Timestamp(System.currentTimeMillis());
  }
}
