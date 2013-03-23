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
import com.google.gwtorm.client.CompoundKey;
import com.google.gwtorm.client.StringKey;

import java.sql.Timestamp;

/** An approval (or negative approval) on a patch set. */
public final class PatchSetApproval {
  public static class LabelId extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    public static final LabelId SUBMIT = new LabelId("SUBM");

    @Column(id = 1)
    protected String id;

    protected LabelId() {
    }

    public LabelId(final String n) {
      id = n;
    }

    @Override
    public String get() {
      return id;
    }

    @Override
    protected void set(String newValue) {
      id = newValue;
    }

    @Override
    public int hashCode() {
      return get().hashCode();
    }

    @Override
    public boolean equals(Object b) {
      if (b instanceof LabelId) {
        return get().equals(((LabelId) b).get());
      }
      return false;
    }
  }

  public static class Key extends CompoundKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column(id = 2)
    protected Account.Id accountId;

    @Column(id = 3)
    protected LabelId categoryId;

    protected Key() {
      patchSetId = new PatchSet.Id();
      accountId = new Account.Id();
      categoryId = new LabelId();
    }

    public Key(final PatchSet.Id ps, final Account.Id a,
        final LabelId c) {
      this.patchSetId = ps;
      this.accountId = a;
      this.categoryId = c;
    }

    @Override
    public PatchSet.Id getParentKey() {
      return patchSetId;
    }

    public Account.Id getAccountId() {
      return accountId;
    }

    public LabelId getLabelId() {
      return categoryId;
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

  /** <i>Cached copy of Change.open.</i> */
  @Column(id = 4)
  protected boolean changeOpen;

  /** <i>Cached copy of Change.sortKey</i>; only if {@link #changeOpen} = false */
  @Column(id = 5, length = 16, notNull = false)
  protected String changeSortKey;

  protected PatchSetApproval() {
  }

  public PatchSetApproval(final PatchSetApproval.Key k, final short v) {
    key = k;
    changeOpen = true;
    setValue(v);
    setGranted();
  }

  public PatchSetApproval(final PatchSet.Id psId, final PatchSetApproval src) {
    key =
        new PatchSetApproval.Key(psId, src.getAccountId(), src.getLabelId());
    changeOpen = true;
    value = src.getValue();
    granted = src.granted;
  }

  public PatchSetApproval.Key getKey() {
    return key;
  }

  public PatchSet.Id getPatchSetId() {
    return key.patchSetId;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  public LabelId getLabelId() {
    return key.categoryId;
  }

  public short getValue() {
    return value;
  }

  public void setValue(final short v) {
    value = v;
  }

  public Timestamp getGranted() {
    return granted;
  }

  public void setGranted() {
    granted = new Timestamp(System.currentTimeMillis());
  }

  public void setGranted(Timestamp ts) {
    granted = ts;
  }

  public void cache(final Change c) {
    changeOpen = c.open;
    changeSortKey = c.sortKey;
  }

  public String getLabel() {
    return getLabelId().get();
  }

  public boolean isSubmit() {
    return LabelId.SUBMIT.get().equals(getLabel());
  }

  @Override
  public String toString() {
    return new StringBuilder().append('[').append(key).append(": ")
        .append(value).append(']').toString();
  }
}
