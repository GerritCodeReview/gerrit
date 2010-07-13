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
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;

import java.util.Comparator;

/** Grant to use an {@link AccessCategory} in the scope of a git ref. */
public final class NewRefRight {
  /** Pattern that matches all references in a project. */
  public static final String ALL = "refs/*";

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

  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }
  }

  public static class AlternateKey extends CompoundKey<Project.NameKey> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Project.NameKey projectName;

    @Column(id = 2)
    protected RefPattern refPattern;

    @Column(id = 3)
    protected AccessCategory.Id categoryId;

    @Column(id = 4)
    protected AccountGroup.Id groupId;

    protected AlternateKey() {
      projectName = new Project.NameKey();
      refPattern = new RefPattern();
      categoryId = new AccessCategory.Id();
      groupId = new AccountGroup.Id();
    }

    public AlternateKey(final Project.NameKey projectName, final RefPattern refPattern,
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
  protected Id id;

  @Column(id = 2, name = Column.NONE)
  protected AlternateKey alternateKey;

  protected NewRefRight() {
  }

  public NewRefRight(NewRefRight.Id id, NewRefRight.AlternateKey alternateKey) {
    this.id = id;
    this.alternateKey = alternateKey;
  }

  public NewRefRight.Id getId() {
    return id;
  }

  public NewRefRight.AlternateKey getAlternateKey() {
    return alternateKey;
  }

  public String getRefPattern() {
    if (isExclusive()) {
      return alternateKey.refPattern.get().substring(1);
    }
    return alternateKey.refPattern.get();
  }

  public String getRefPatternForDisplay() {
    return alternateKey.refPattern.get();
  }

  public Project.NameKey getProjectNameKey() {
    return getAlternateKey().getProjectNameKey();
  }

  public boolean isExclusive() {
    return alternateKey.refPattern.get().startsWith("-");
  }

  public AccessCategory.Id getAccessCategoryId() {
    return alternateKey.categoryId;
  }

  public AccountGroup.Id getAccountGroupId() {
    return alternateKey.groupId;
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
    return getAlternateKey().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof NewRefRight) {
      NewRefRight a = this;
      NewRefRight b = (NewRefRight) o;
      return a.getAlternateKey().equals(b.getAlternateKey());
    }
    return false;
  }

  public static final Comparator<NewRefRight> REF_PATTERN_ORDER =
      new Comparator<NewRefRight>() {

        @Override
        public int compare(NewRefRight a, NewRefRight b) {
          int aLength = a.getRefPattern().length();
          int bLength = b.getRefPattern().length();
          if (bLength == aLength) {
            AccessCategory.Id aCat = a.getAccessCategoryId();
            AccessCategory.Id bCat = b.getAccessCategoryId();
            if (aCat.get().equals(bCat.get())) {
              return a.getRefPattern().compareTo(b.getRefPattern());
            }
            return a.getAccessCategoryId().get().compareTo(
                b.getAccessCategoryId().get());
          }
          return bLength - aLength;
        }
      };
}
