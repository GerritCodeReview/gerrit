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
 * Abstract key type using a single string value.
 *
 * <p>Applications should subclass this type to create their own entity-specific key classes.
 *
 * @param <P> the parent key type. Use {@link Key} if no parent key is needed.
 */
@SuppressWarnings("serial")
public abstract class StringKey<P extends Key<?>>
    implements Key<P>, Serializable, Comparable<StringKey<?>> {
  /** @return name of the entity instance. */
  public abstract String get();

  /** @param newValue the new value of this key. */
  protected abstract void set(String newValue);

  /** @return the parent key instance; null if this is a root level key. */
  @Override
  public P getParentKey() {
    return null;
  }

  @Override
  public int hashCode() {
    int hc = get() != null ? get().hashCode() : 0;
    if (getParentKey() != null) {
      hc *= 31;
      hc += getParentKey().hashCode();
    }
    return hc;
  }

  @Override
  public boolean equals(final Object b) {
    if (b == null || get() == null || b.getClass() != getClass()) {
      return false;
    }

    final StringKey<P> q = cast(b);
    return get().equals(q.get()) && KeyUtil.eq(getParentKey(), q.getParentKey());
  }

  @Override
  public int compareTo(final StringKey<?> other) {
    return get().compareTo(other.get());
  }

  @Override
  public String toString() {
    final StringBuffer r = new StringBuffer();
    if (getParentKey() != null) {
      r.append(getParentKey().toString());
      r.append(',');
    }
    r.append(KeyUtil.encode(get()));
    return r.toString();
  }

  @Override
  public void fromString(final String in) {
    set(KeyUtil.parseFromString(getParentKey(), in));
  }

  @SuppressWarnings("unchecked")
  private static <A extends Key<?>> StringKey<A> cast(final Object b) {
    return (StringKey<A>) b;
  }
}
