// Copyright (C) 2010 The Android Open Source Project
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

/** Project subscribing to a Branch of a submodule */
public final class Subscription {
  /** Subscription key */
  public static class Key extends CompoundKey<Branch.NameKey> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Branch.NameKey target;

    @Column(id = 2)
    protected Branch.NameKey source;

    protected Key() {
      target = new Branch.NameKey();
      source = new Branch.NameKey();
    }

    protected Key(final Branch.NameKey t, final Branch.NameKey s) {
      target = t;
      source = s;
    }

    @Override
    public Branch.NameKey getParentKey() {
      return target;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {source};
    }

  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  protected Subscription() {
  }

  public Subscription(final Branch.NameKey newTarget,
      final Branch.NameKey newSource) {
    key = new Key(newTarget, newSource);
  }

  @Override
  public String toString() {
    return key.target.getParentKey().get() + " " + key.target.get() + ", "
        + key.source.getParentKey().get() + " " + key.source.get();
  }

  public Branch.NameKey getSubscriber() {
    return key.target;
  }
}
