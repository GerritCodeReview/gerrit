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
// limitations under the License

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;
import com.google.gwtorm.client.StringKey;

/**
 * It indicates the merge strategy to use for a refs pattern when submitting a change.
 */
public final class RefMergeStrategy {

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

    protected Key() {
      projectName = new Project.NameKey();
      refPattern = new RefPattern();
    }

    public Key(final Project.NameKey projectName, final RefPattern refPattern) {
      this.projectName = projectName;
      this.refPattern = refPattern;
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
      return new com.google.gwtorm.client.Key<?>[] {refPattern};
    }
  }

  public static enum SubmitType {
    FAST_FORWARD_ONLY('F'),

    MERGE_IF_NECESSARY('M'),

    MERGE_ALWAYS('A'),

    CHERRY_PICK('C');

    private final char code;

    private SubmitType(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static SubmitType forCode(final char c) {
      for (final SubmitType s : SubmitType.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected char submitType;

  protected RefMergeStrategy() {
  }

  public RefMergeStrategy(RefMergeStrategy.Key key) {
    this.key = key;
  }

  public RefMergeStrategy.Key getKey() {
    return key;
  }

  public String getRefPattern() {
    return key.refPattern.get();
  }

  public Project.NameKey getProjectNameKey() {
    return getKey().getProjectNameKey();
  }

  public SubmitType getSubmitType() {
    return SubmitType.forCode(submitType);
  }

  public void setSubmitType(final SubmitType type) {
    submitType = type.getCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof RefMergeStrategy) {
      RefMergeStrategy a = this;
      RefMergeStrategy b = (RefMergeStrategy) o;
      return a.getKey().equals(b.getKey());
    }
    return false;
  }

}
