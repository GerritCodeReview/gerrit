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

/**
 * Generic type for an entity key.
 *
 * <p>Although not required, entities should make their primary key type implement this interface,
 * permitting traversal up through the containment hierarchy of the entity keys.
 *
 * @param <P> type of the parent key. If no parent, use {@link Key} itself.
 */
public interface Key<P extends Key<?>> {
  /**
   * Get the parent key instance.
   *
   * @return the parent key; null if this entity key is a root-level key.
   */
  public P getParentKey();

  @Override
  public int hashCode();

  @Override
  public boolean equals(Object o);

  /** @return the key, encoded in a string format . */
  @Override
  public String toString();

  /** Reset this key instance to represent the data in the supplied string. */
  public void fromString(String in);
}
