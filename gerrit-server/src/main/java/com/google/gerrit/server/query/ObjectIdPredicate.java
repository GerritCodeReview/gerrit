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

package com.google.gerrit.server.query;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;


/** Predicate for a field of {@link ObjectId}. */
public abstract class ObjectIdPredicate<T, C> extends OperatorPredicate<T, C> {
  private final AbbreviatedObjectId id;

  public ObjectIdPredicate(final String name, final AbbreviatedObjectId id) {
    super(name, id.name());
    this.id = id;
  }

  public boolean isComplete() {
    return id.isComplete();
  }

  public AbbreviatedObjectId abbreviated() {
    return id;
  }

  public ObjectId full() {
    return id.toObjectId();
  }

  @Override
  public int hashCode() {
    return getOperator().hashCode() * 31 + id.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof ObjectIdPredicate) {
      final ObjectIdPredicate<?, ?> p = (ObjectIdPredicate<?, ?>) other;
      return getOperator().equals(p.getOperator()) && id.equals(p.id);
    }
    return false;
  }

  @Override
  public String toString() {
    return getOperator() + ":" + id.name();
  }
}
