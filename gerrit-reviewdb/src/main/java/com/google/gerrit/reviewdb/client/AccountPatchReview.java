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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;

/** An entity that keeps track of what user reviewed what patches. */
public final class AccountPatchReview {

  public static class Key extends CompoundKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Account.Id accountId;

    @Column(id = 2, name = Column.NONE)
    protected Patch.Key patchKey;

    protected Key() {
      accountId = new Account.Id();
      patchKey = new Patch.Key();
    }

    public Key(final Patch.Key p, final Account.Id a) {
      patchKey = p;
      accountId = a;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    public Patch.Key getPatchKey() {
      return patchKey;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {patchKey};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected AccountPatchReview.Key key;

  protected AccountPatchReview() {}

  public AccountPatchReview(final Patch.Key k, final Account.Id a) {
    key = new AccountPatchReview.Key(k, a);
  }

  public AccountPatchReview.Key getKey() {
    return key;
  }
}
