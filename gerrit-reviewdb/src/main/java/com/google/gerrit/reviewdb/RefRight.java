// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gwtorm.client.StringKey;

import java.util.Comparator;

/** Grant to use an {@link ApprovalCategory} in the scope of a git ref. */
public final class RefRight {
  /** Pattern that matches all references in a project. */
  public static final String ALL = "refs/*";

  /** Prefix that triggers a regular expression pattern. */
  public static final String REGEX_PREFIX = "^";

  public static class RefPattern extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String pattern;

    protected RefPattern() {
    }

    public RefPattern(final String pattern) {
      this.pattern = pattern;
    }

    @Override
    public String get() {
      return pattern;
    }

    @Override
    protected void set(String pattern) {
      this.pattern = pattern;
    }
  }

  public static class Key extends CompoundKey<Project.NameKey> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Project.NameKey projectName;

    @Column(id = 2)
    protected RefPattern refPattern;

    @Column(id = 3)
    protected ApprovalCategory.Id categoryId;

    @Column(id = 4)
    protected AccountGroup.Id groupId;

    protected Key() {
      projectName = new Project.NameKey();
      refPattern = new RefPattern();
      categoryId = new ApprovalCategory.Id();
      groupId = new AccountGroup.Id();
    }

    public Key(final Project.NameKey projectName, final RefPattern refPattern,
        final ApprovalCategory.Id categoryId, final AccountGroup.Id groupId) {
      this.projectName = projectName;
      this.refPattern = refPattern;
      this.categoryId = categoryId;
      this.groupId = groupId;
    }

    @Override
    public Project.NameKey getParentKey() {
      return projectName;
    }

    public Project.NameKey getProjectNameKey() {
      return projectName;
    }

    public String getRefPattern() {
      return refPattern.get();
    }

    public void setGroupId(AccountGroup.Id groupId) {
      this.groupId = groupId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {refPattern, categoryId,
          groupId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected short minValue;

  @Column(id = 3)
  protected short maxValue;

  protected transient AccountGroup.UUID groupUUID;

  protected RefRight() {
  }

  public RefRight(RefRight.Key key) {
    this.key = key;
  }

  public RefRight(final RefRight refRight, final AccountGroup.UUID groupId) {
    this(new RefRight.Key(refRight.getKey().projectName,
        refRight.getKey().refPattern, refRight.getKey().categoryId, null));
    setMinValue(refRight.getMinValue());
    setMaxValue(refRight.getMaxValue());
    setAccountGroupUUID(groupId);
  }

  public RefRight.Key getKey() {
    return key;
  }

  public String getRefPattern() {
    if (isExclusive()) {
      return key.refPattern.get().substring(1);
    }
    return key.refPattern.get();
  }

  public String getRefPatternForDisplay() {
    return key.refPattern.get();
  }

  public Project.NameKey getProjectNameKey() {
    return getKey().getProjectNameKey();
  }

  public boolean isExclusive() {
    return key.refPattern.get().startsWith("-");
  }

  public ApprovalCategory.Id getApprovalCategoryId() {
    return key.categoryId;
  }

  public AccountGroup.Id getAccountGroupId() {
    return key.groupId;
  }

  public AccountGroup.UUID getAccountGroupUUID() {
    return groupUUID;
  }

  public void setAccountGroupUUID(AccountGroup.UUID uuid) {
    groupUUID = uuid;
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

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append("{group :");
    s.append(getAccountGroupId().get());
    s.append(", proj :");
    s.append(getProjectNameKey().get());
    s.append(", cat :");
    s.append(getApprovalCategoryId().get());
    s.append(", pattern :");
    s.append(getRefPatternForDisplay());
    s.append(", min :");
    s.append(getMinValue());
    s.append(", max :");
    s.append(getMaxValue());
    s.append("}");
    return s.toString();
  }

  @Override
  public int hashCode() {
    return getKey().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof RefRight) {
      RefRight a = this;
      RefRight b = (RefRight) o;
      return a.getKey().equals(b.getKey())
          && a.getMinValue() == b.getMinValue()
          && a.getMaxValue() == b.getMaxValue();
    }
    return false;
  }

  public static final Comparator<RefRight> REF_PATTERN_ORDER =
      new Comparator<RefRight>() {

    @Override
    public int compare(RefRight a, RefRight b) {
      int aLength = a.getRefPattern().length();
      int bLength = b.getRefPattern().length();
      if (bLength == aLength) {
        ApprovalCategory.Id aCat = a.getApprovalCategoryId();
        ApprovalCategory.Id bCat = b.getApprovalCategoryId();
        if (aCat.get().equals(bCat.get())) {
          return a.getRefPattern().compareTo(b.getRefPattern());
        }
        return a.getApprovalCategoryId().get()
            .compareTo(b.getApprovalCategoryId().get());
      }
      return bLength - aLength;
    }
  };
}
