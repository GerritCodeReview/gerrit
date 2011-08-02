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

/**
 * Defining a project/branch subscription to a project/branch project.
 * <p>
 * This means a class instance represents a repo/branch subscription to a
 * project/branch (the subscriber).
 * <p>
 * A subscriber operates a submodule in defined path.
 */
public final class SubmoduleSubscription {
  /** Subscription key */
  public static class Key extends CompoundKey<Branch.NameKey> {
    private static final long serialVersionUID = 1L;

    /**
     * Indicates the Subscriber, the project owner of the gitlinks to the
     * subscriptions.
     */
    @Column(id = 1)
    protected Branch.NameKey subscriber;

    /**
     * Indicates the Subscription, the project the subscriber's gitlink is
     * pointed to.
     */
    @Column(id = 2)
    protected Branch.NameKey subscription;

    protected Key() {
      subscriber = new Branch.NameKey();
      subscription = new Branch.NameKey();
    }

    protected Key(final Branch.NameKey t, final Branch.NameKey s) {
      subscriber = t;
      subscription = s;
    }

    @Override
    public Branch.NameKey getParentKey() {
      return subscriber;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {subscription};
    }

  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected String path;

  protected SubmoduleSubscription() {
  }

  public SubmoduleSubscription(final Branch.NameKey newTarget,
      final Branch.NameKey newSource, final String newPath) {
    key = new Key(newTarget, newSource);
    path = newPath;
  }

  @Override
  public String toString() {
    return key.subscriber.getParentKey().get() + " " + key.subscriber.get()
        + ", " + key.subscription.getParentKey().get() + " "
        + key.subscription.get() + ", " + path;
  }

  public Branch.NameKey getSubscriber() {
    return key.subscriber;
  }

  public Branch.NameKey getSubscription() {
    return key.subscription;
  }

  public String getPath() {
    return path;
  }

  public boolean equals(Object o) {
    if (o instanceof SubmoduleSubscription) {
      return key.equals(((SubmoduleSubscription) o).key)
          && path.equals(((SubmoduleSubscription) o).path);
    }
    return false;
  }
}
