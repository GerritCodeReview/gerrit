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

package com.google.gwtorm.client;

import java.io.Serializable;

/**
 * Abstract key type composed of other keys.
 *
 * <p>Applications should subclass this type to create their own entity-specific key classes.
 *
 * @param <P> the parent key type. Use {@link Key} if no parent key is needed.
 */
@SuppressWarnings("serial")
public abstract class CompoundKey<P extends Key<?>> implements Key<P>, Serializable {
  /** @return the member key components, minus the parent key. */
  public abstract Key<?>[] members();

  /** @return the parent key instance; null if this is a root level key. */
  @Override
  public P getParentKey() {
    return null;
  }

  @Override
  public int hashCode() {
    int hc = 0;
    if (getParentKey() != null) {
      hc = getParentKey().hashCode();
    }
    for (final Key<?> k : members()) {
      hc *= 31;
      hc += k.hashCode();
    }
    return hc;
  }

  @Override
  public boolean equals(final Object b) {
    if (b == null || b.getClass() != getClass()) {
      return false;
    }

    final CompoundKey<P> q = cast(b);
    if (getParentKey() != null && !getParentKey().equals(q.getParentKey())) {
      return false;
    }

    final Key<?>[] aMembers = members();
    final Key<?>[] bMembers = q.members();
    if (aMembers.length != bMembers.length) {
      return false;
    }
    for (int i = 0; i < aMembers.length; i++) {
      if (!aMembers[i].equals(bMembers[i])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    final StringBuffer r = new StringBuffer();
    boolean first = true;
    if (getParentKey() != null) {
      r.append(KeyUtil.encode(getParentKey().toString()));
      first = false;
    }
    for (final Key<?> k : members()) {
      if (!first) {
        r.append(',');
      }
      r.append(KeyUtil.encode(k.toString()));
      first = false;
    }
    return r.toString();
  }

  @Override
  public void fromString(final String in) {
    final String[] parts = in.split(",");
    int p = 0;
    if (getParentKey() != null) {
      getParentKey().fromString(KeyUtil.decode(parts[p++]));
    }
    for (final Key<?> k : members()) {
      k.fromString(KeyUtil.decode(parts[p++]));
    }
  }

  @SuppressWarnings("unchecked")
  private static <A extends Key<?>> CompoundKey<A> cast(final Object b) {
    return (CompoundKey<A>) b;
  }
}
