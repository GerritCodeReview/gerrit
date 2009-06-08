// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gwtorm.client.CompoundKey;

import java.sql.Timestamp;

/**
 * Acceptance of a {@link ContributorAgreement} by an {@link AccountGroup}.
 */
public final class AccountGroupAgreement implements AbstractAgreement {
  public static class Key extends CompoundKey<AccountGroup.Id> {
    private static final long serialVersionUID = 1L;

    @Column
    protected AccountGroup.Id groupId;

    @Column
    protected ContributorAgreement.Id claId;

    protected Key() {
      groupId = new AccountGroup.Id();
      claId = new ContributorAgreement.Id();
    }

    public Key(final AccountGroup.Id group, final ContributorAgreement.Id cla) {
      this.groupId = group;
      this.claId = cla;
    }

    @Override
    public AccountGroup.Id getParentKey() {
      return groupId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {claId};
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  @Column
  protected Timestamp acceptedOn;

  @Column
  protected char status;

  @Column(notNull = false)
  protected Account.Id reviewedBy;

  @Column(notNull = false)
  protected Timestamp reviewedOn;

  @Column(notNull = false, length = Integer.MAX_VALUE)
  protected String reviewComments;

  protected AccountGroupAgreement() {
  }

  public AccountGroupAgreement(final AccountGroupAgreement.Key k) {
    key = k;
    acceptedOn = new Timestamp(System.currentTimeMillis());
    status = Status.NEW.getCode();
  }

  public AccountGroupAgreement.Key getKey() {
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
}
