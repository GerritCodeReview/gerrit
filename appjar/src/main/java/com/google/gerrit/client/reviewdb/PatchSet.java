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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;

/** A single revision of a {@link Change}. */
public final class PatchSet {
  public static class Id extends IntKey<Change.Id> {
    @Column
    protected Change.Id changeId;

    @Column
    protected int patchSetId;

    protected Id() {
      changeId = new Change.Id();
    }

    public Id(final Change.Id change, final int id) {
      this.changeId = change;
      this.patchSetId = id;
    }

    @Override
    public Change.Id getParentKey() {
      return changeId;
    }

    @Override
    public int get() {
      return patchSetId;
    }
  }

  @Column(name = Column.NONE)
  protected Id key;

  @Column(notNull = false)
  protected RevId revision;

  protected PatchSet() {
  }

  public PatchSet(final PatchSet.Id k) {
    key = k;
  }

  public PatchSet.Id getKey() {
    return key;
  }

  public int getId() {
    return key.get();
  }

  public RevId getRevision() {
    return revision;
  }

  public void setRevision(final RevId i) {
    revision = i;
  }
}
