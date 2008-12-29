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
import com.google.gwtorm.client.CompoundKey;

/** {@link AccountGroup} as owner/manager of a project. */
public final class ProjectLeadGroup {
  public static class Key extends CompoundKey<Project.NameKey> {
    @Column
    protected Project.NameKey projectName;

    @Column
    protected AccountGroup.Id groupId;

    protected Key() {
      projectName = new Project.NameKey();
      groupId = new AccountGroup.Id();
    }

    public Key(final Project.NameKey p, final AccountGroup.Id a) {
      projectName = p;
      groupId = a;
    }

    @Override
    public Project.NameKey getParentKey() {
      return projectName;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {groupId};
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  protected ProjectLeadGroup() {
  }

  public ProjectLeadGroup(final ProjectLeadGroup.Key k) {
    key = k;
  }
}
