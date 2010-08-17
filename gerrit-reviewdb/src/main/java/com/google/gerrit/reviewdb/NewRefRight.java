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

/** Grant to use an {@link ApprovalCategory} in the scope of a git ref. */
public final class NewRefRight {
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
    protected AccessCategory.Id categoryId;

    @Column(id = 4)
    protected AccountGroup.Id groupId;

    protected Key() {
      projectName = new Project.NameKey();
      refPattern = new RefPattern();
      categoryId = new AccessCategory.Id();
      groupId = new AccountGroup.Id();
    }

    public Key(final Project.NameKey projectName, final RefPattern refPattern,
        final AccessCategory.Id categoryId, final AccountGroup.Id groupId) {
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

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {refPattern, categoryId,
          groupId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2, notNull = false)
  protected String labels;

  protected NewRefRight() {
  }

  public NewRefRight(NewRefRight.Key key, String labels) {
    this.key = key;
    this.labels = labels;
  }

  public NewRefRight.Key getKey() {
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

  public AccessCategory.Id getAccessCategoryId() {
    return key.categoryId;
  }

  public AccountGroup.Id getAccountGroupId() {
    return key.groupId;
  }

  public String getLabels() {
    return labels;
  }

  public void setLabels(String labels) {
    this.labels = labels;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append("{group :");
    s.append(getAccountGroupId().get());
    s.append(", proj :");
    s.append(getProjectNameKey().get());
    s.append(", cat :");
    s.append(getAccessCategoryId().get());
    s.append(", pattern :");
    s.append(getRefPatternForDisplay());
    s.append("}");
    return s.toString();
  }

  @Override
  public int hashCode() {
    return getKey().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof NewRefRight) {
      NewRefRight a = this;
      NewRefRight b = (NewRefRight) o;
      return a.getKey().equals(b.getKey());
    }
    return false;
  }
}
