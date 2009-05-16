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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;

/** Grant to use an {@link ApprovalCategory} in the scope of a {@link Project}. */
public final class ProjectRight {
  /** Project.Id meaning "any and all projects on this server". */
  public static final Project.Id WILD_PROJECT = new Project.Id(0);

  public static class Key extends CompoundKey<Project.Id> {
    private static final long serialVersionUID = 1L;

    @Column
    protected Project.Id projectId;

    @Column
    protected ApprovalCategory.Id categoryId;

    @Column
    protected AccountGroup.Id groupId;

    protected Key() {
      projectId = new Project.Id();
      categoryId = new ApprovalCategory.Id();
      groupId = new AccountGroup.Id();
    }

    public Key(final Project.Id p, final ApprovalCategory.Id a,
        final AccountGroup.Id g) {
      projectId = p;
      categoryId = a;
      groupId = g;
    }

    @Override
    public Project.Id getParentKey() {
      return projectId;
    }

    public Project.Id getProjectId() {
      return projectId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {categoryId, groupId};
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  @Column
  protected short minValue;

  @Column
  protected short maxValue;

  protected ProjectRight() {
  }

  public ProjectRight(final ProjectRight.Key k) {
    key = k;
  }

  public ProjectRight.Key getKey() {
    return key;
  }

  public Project.Id getProjectId() {
    return key.projectId;
  }

  public ApprovalCategory.Id getApprovalCategoryId() {
    return key.categoryId;
  }

  public AccountGroup.Id getAccountGroupId() {
    return key.groupId;
  }

  public short getMinValue() {
    return minValue;
  }

  public void setMinValue(final short m) {
    minValue = m;
  }

  public short getMaxValue() {
    return maxValue;
  }

  public void setMaxValue(final short m) {
    maxValue = m;
  }
}
