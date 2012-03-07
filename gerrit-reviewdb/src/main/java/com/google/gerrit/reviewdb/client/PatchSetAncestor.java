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
import com.google.gwtorm.client.IntKey;

/** Ancestors of a {@link PatchSet} that the PatchSet depends upon. */
public final class PatchSetAncestor {
  public static class Id extends IntKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column(id = 2)
    protected int position;

    protected Id() {
      patchSetId = new PatchSet.Id();
    }

    public Id(final PatchSet.Id psId, final int pos) {
      this.patchSetId = psId;
      this.position = pos;
    }

    @Override
    public PatchSet.Id getParentKey() {
      return patchSetId;
    }

    @Override
    public int get() {
      return position;
    }

    @Override
    protected void set(int newValue) {
      position = newValue;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Id key;

  @Column(id = 2)
  protected RevId ancestorRevision;

  protected PatchSetAncestor() {
  }

  public PatchSetAncestor(final PatchSetAncestor.Id k) {
    key = k;
  }

  public PatchSetAncestor.Id getId() {
    return key;
  }

  public PatchSet.Id getPatchSet() {
    return key.patchSetId;
  }

  public int getPosition() {
    return key.position;
  }

  public RevId getAncestorRevision() {
    return ancestorRevision;
  }

  public void setAncestorRevision(final RevId id) {
    ancestorRevision = id;
  }
}
