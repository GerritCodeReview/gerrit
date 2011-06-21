// Copyright (C) 2011 The Android Open Source Project
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

/** An approval (or negative approval) on a change set. */
public final class ChangeSetApproval extends SetApproval<ChangeSet.Id> {
  public static class Key extends CompoundKey<ChangeSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected ChangeSet.Id changeSetId;

    @Column(id = 2)
    protected Account.Id accountId;

    @Column(id = 3)
    protected ApprovalCategory.Id categoryId;

    protected Key() {
      changeSetId = new ChangeSet.Id();
      accountId = new Account.Id();
      categoryId = new ApprovalCategory.Id();
    }

    public Key(final ChangeSet.Id cs, final Account.Id a,
        final ApprovalCategory.Id c) {
      this.changeSetId = cs;
      this.accountId = a;
      this.categoryId = c;
    }

    @Override
    public ChangeSet.Id getParentKey() {
      return changeSetId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {accountId, categoryId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  /**
   * Value assigned by the user.
   * <p>
   * The precise meaning of "value" is up to each category.
   * <p>
   * In general:
   * <ul>
   * <li><b>&lt; 0:</b> The approval is rejected/revoked.</li>
   * <li><b>= 0:</b> No indication either way is provided.</li>
   * <li><b>&gt; 0:</b> The approval is approved/positive.</li>
   * </ul>
   * and in the negative and positive direction a magnitude can be assumed.The
   * further from 0 the more assertive the approval.
   */
  @Column(id = 2)
  protected short value;

  @Column(id = 3)
  protected Timestamp granted;

  /** <i>Cached copy of Topic.open.</i> */
  @Column(id = 4)
  protected boolean topicOpen;

  /** <i>Cached copy of Topic.sortKey</i>; only if {@link #topicOpen} = false */
  @Column(id = 5, length = 16, notNull = false)
  protected String topicSortKey;

  protected ChangeSetApproval() {
  }

  public ChangeSetApproval(final ChangeSetApproval.Key k, final short v) {
    key = k;
    topicOpen = true;
    setValue(v);
    setGranted();
  }

  public ChangeSetApproval(final ChangeSet.Id csId, final ChangeSetApproval src) {
    key =
        new ChangeSetApproval.Key(csId, src.getAccountId(), src.getCategoryId());
    topicOpen = true;
    value = src.getValue();
    granted = src.granted;
  }

  public ChangeSetApproval.Key getKey() {
    return key;
  }

  public ChangeSet.Id getChangeSetId() {
    return key.changeSetId;
  }

  @Override
  public Account.Id getAccountId() {
    return key.accountId;
  }

  @Override
  public ApprovalCategory.Id getCategoryId() {
    return key.categoryId;
  }

  @Override
  public short getValue() {
    return value;
  }

  @Override
  public void setValue(final short v) {
    value = v;
  }

  public Timestamp getGranted() {
    return granted;
  }

  public void setGranted() {
    granted = new Timestamp(System.currentTimeMillis());
  }

  public void cache(final Topic t) {
    topicOpen = t.open;
    topicSortKey = t.sortKey;
  }

  @Override
  public ChangeSet.Id getSetId() {
    return getChangeSetId();
  }
}
