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

/** A revision identifier for a file or a change. */
public final class RevId {
  public static final int LEN = 40;

  @Column(id = 1, length = LEN)
  protected String id;

  protected RevId() {}

  public RevId(String str) {
    id = str;
  }

  /** @return the value of this revision id. */
  public String get() {
    return id;
  }

  /** @return true if this revision id has all required digits. */
  public boolean isComplete() {
    return get().length() == LEN;
  }

  /**
   * @return if {@link #isComplete()}, {@code this}; otherwise a new RevId with 'z' appended on the
   *     end.
   */
  public RevId max() {
    if (isComplete()) {
      return this;
    }

    final StringBuilder revEnd = new StringBuilder(get().length() + 1);
    revEnd.append(get());
    revEnd.append('z');
    return new RevId(revEnd.toString());
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof RevId) && id.equals(((RevId) o).id);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + id + "}";
  }

  public boolean matches(String str) {
    return id.startsWith(str.toLowerCase());
  }
}
