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

/** Ancestors of a {@link PatchSet} that the PatchSet depends upon. */
public final class PatchSetAncestor {
  public static class Key extends IntKey<PatchSet.Id> {
    @Column(name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column
    protected int position;

    protected Key() {
      patchSetId = new PatchSet.Id();
    }

    public Key(final PatchSet.Id psId, final int pos) {
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
  }

  @Column(name = Column.NONE)
  protected Key key;

  @Column(length = 40)
  protected String ancestorRevision;

  protected PatchSetAncestor() {
  }

  public PatchSetAncestor(final PatchSetAncestor.Key k, final String rev) {
    key = k;
    ancestorRevision = rev;
  }

  public PatchSetAncestor.Key getKey() {
    return key;
  }

  public PatchSet.Id getPatchSet() {
    return key.patchSetId;
  }

  public int getPosition() {
    return key.position;
  }

  public String getAncestorRevision() {
    return ancestorRevision;
  }
}
