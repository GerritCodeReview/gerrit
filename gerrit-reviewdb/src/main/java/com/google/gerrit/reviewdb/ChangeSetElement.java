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
import com.google.gwtorm.client.CompoundKey;

/** {@link Change} belonging to an {@link ChangeSet}. */
public final class ChangeSetElement {
  public static class Key extends CompoundKey<Change.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Change.Id changeId;

    @Column(id = 2)
    protected ChangeSet.Id changeSetId;

    protected Key() {
      changeId = new Change.Id();
      changeSetId = new ChangeSet.Id();
    }

    public Key(final Change.Id cId, final ChangeSet.Id csId) {
      changeId = cId;
      changeSetId = csId;
    }

    @Override
    public Change.Id getParentKey() {
      return changeId;
    }

    public ChangeSet.Id getChangeSetId() {
      return changeSetId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {changeSetId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected int position;

  protected ChangeSetElement() {
  }

  public ChangeSetElement(final ChangeSetElement.Key k, int pos) {
    key = k;
    position = pos;
  }

  public ChangeSetElement.Key getKey() {
    return key;
  }

  public Change.Id getChangeId() {
    return key.changeId;
  }

  public ChangeSet.Id getChangeSetId() {
    return key.changeSetId;
  }

  public int getPosition() {
    return position;
  }
}