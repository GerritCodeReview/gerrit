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
import com.google.gwtorm.client.StringKey;


public final class AtomicEntry {

  public static class Id extends StringKey<Change.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Change.Id superChangeId;

    @Column(id = 2)
    protected String sourceSha1;

    protected Id() {
      superChangeId = new Change.Id();
    }

    public Id(final Change.Id superChangeId, final String sourceSha1) {
      this.superChangeId = superChangeId;
      this.sourceSha1 = sourceSha1;
    }

    @Override
    public Change.Id getParentKey() {
      return superChangeId;
    }

    @Override
    public String get() {
      return sourceSha1;
    }

    @Override
    protected void set(String newValue) {
      sourceSha1 = newValue;
    }
  }

  public AtomicEntry() {
  }

  public AtomicEntry(AtomicEntry.Id id) {
    this.id = id;
  }

  @Column(id = 1, name = Column.NONE)
  protected Id id;

  @Column(id = 2, notNull = false)
  protected Change.Id sourceChangeId;

  public Id getId() {
    return id;
  }

  public void setId(Id id) {
    this.id = id;
  }

  public Change.Id getSourceChangeId() {
    return sourceChangeId;
  }

  public void setSourceChangeId(Change.Id sourceChangeId) {
    this.sourceChangeId = sourceChangeId;
  }

  @Override
  public boolean equals(Object b) {
    if (b == null || b.getClass() != getClass()) {
      return false;
    }

    return getId().equals(((AtomicEntry) b).getId());
  }
}
