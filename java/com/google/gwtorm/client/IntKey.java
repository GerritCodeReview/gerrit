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
 * Abstract key type using a single integer value.
 *
 * <p>Applications should subclass this type to create their own entity-specific key classes.
 *
 * @param <P> the parent key type. Use {@link Key} if no parent key is needed.
 */
@SuppressWarnings("serial")
public abstract class IntKey<P extends Key<?>> implements Key<P>, Serializable {
  /** @return id of the entity instance. */
  public abstract int get();

  /** @param newValue the new value of this key. */
  protected abstract void set(int newValue);

  /** @return the parent key instance; null if this is a root level key. */
  @Override
  public P getParentKey() {
    return null;
  }

  @Override
  public int hashCode() {
    int hc = get();
    if (getParentKey() != null) {
      hc *= 31;
      hc += getParentKey().hashCode();
    }
    return hc;
  }

  @Override
  public boolean equals(final Object b) {
    if (b == null || b.getClass() != getClass()) {
      return false;
    }

    final IntKey<P> q = cast(b);
    return get() == q.get() && KeyUtil.eq(getParentKey(), q.getParentKey());
  }

  @Override
  public String toString() {
    final StringBuffer r = new StringBuffer();
    if (getParentKey() != null) {
      r.append(getParentKey().toString());
      r.append(',');
    }
    r.append(get());
    return r.toString();
  }

  @Override
  public void fromString(final String in) {
    set(Integer.parseInt(KeyUtil.parseFromString(getParentKey(), in)));
  }

  @SuppressWarnings("unchecked")
  private static <A extends Key<?>> IntKey<A> cast(final Object b) {
    return (IntKey<A>) b;
  }
}
