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

/** An approval (or negative approval) on a patch set. */
public final class PatchSetLabel {
  public static class Key extends CompoundKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column(id = 2)
    protected Account.Id accountId;

    @Column(id = 3)
    protected AccessCategory.Id labelId;

    protected Key() {
      patchSetId = new PatchSet.Id();
      accountId = new Account.Id();
      labelId = new AccessCategory.Id();
    }

    public Key(final PatchSet.Id ps, final Account.Id a,
        final AccessCategory.Id l) {
      this.patchSetId = ps;
      this.accountId = a;
      this.labelId = l;
    }

    @Override
    public PatchSet.Id getParentKey() {
      return patchSetId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {accountId, labelId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 3)
  protected Timestamp granted;

  /** <i>Cached copy of Change.open.</i> */
  @Column(id = 4)
  protected boolean changeOpen;

  /** <i>Cached copy of Change.sortKey</i>; only if {@link #changeOpen} = false */
  @Column(id = 5, length = 16, notNull = false)
  protected String changeSortKey;

  protected PatchSetLabel() {
  }

  public PatchSetLabel(final PatchSetLabel.Key k, final short v) {
    key = k;
    changeOpen = true;
    setGranted();
  }

  public PatchSetLabel(final PatchSet.Id psId, final PatchSetLabel src) {
    key =
        new PatchSetLabel.Key(psId, src.getAccountId(), src.getLabelId());
    changeOpen = true;
    granted = src.granted;
  }

  public PatchSetLabel.Key getKey() {
    return key;
  }

  public PatchSet.Id getPatchSetId() {
    return key.patchSetId;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  public AccessCategory.Id getLabelId() {
    return key.labelId;
  }

  public Timestamp getGranted() {
    return granted;
  }

  public void setGranted() {
    granted = new Timestamp(System.currentTimeMillis());
  }

  public void cache(final Change c) {
    changeOpen = c.open;
    changeSortKey = c.sortKey;
  }
}
