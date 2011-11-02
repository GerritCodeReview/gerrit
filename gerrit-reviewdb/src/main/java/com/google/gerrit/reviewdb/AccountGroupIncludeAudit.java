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

/** Inclusion of an {@link AccountGroup} in another {@link AccountGroup}. */
public final class AccountGroupIncludeAudit {
  public static class Key extends CompoundKey<AccountGroup.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected AccountGroup.Id groupId;

    @Column(id = 2)
    protected AccountGroup.Id includeId;

    @Column(id = 3)
    protected Timestamp addedOn;

    protected Key() {
      groupId = new AccountGroup.Id();
      includeId = new AccountGroup.Id();
    }

    public Key(final AccountGroup.Id g, final AccountGroup.Id i, final Timestamp t) {
      groupId = g;
      includeId = i;
      addedOn = t;
    }

    @Override
    public AccountGroup.Id getParentKey() {
      return groupId;
    }

    public AccountGroup.Id getIncludedId() {
      return includeId;
    }

    public Timestamp getAddedOn() {
      return addedOn;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {includeId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected Account.Id addedBy;

  @Column(id = 3, notNull = false)
  protected Account.Id removedBy;

  @Column(id = 4, notNull = false)
  protected Timestamp removedOn;

  protected AccountGroupIncludeAudit() {
  }

  public AccountGroupIncludeAudit(final AccountGroupInclude m,
      final Account.Id adder) {
    final AccountGroup.Id group = m.getGroupId();
    final AccountGroup.Id include = m.getIncludeId();
    key = new AccountGroupIncludeAudit.Key(group, include, now());
    addedBy = adder;
  }

  public AccountGroupIncludeAudit.Key getKey() {
    return key;
  }

  public boolean isActive() {
    return removedOn == null;
  }

  public void removed(final Account.Id deleter) {
    removedBy = deleter;
    removedOn = now();
  }

  private static Timestamp now() {
    return new Timestamp(System.currentTimeMillis());
  }
}