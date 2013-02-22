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
import com.google.gwtorm.client.Key;
import com.google.gwtorm.client.StringKey;

/** Types of approvals that can be associated with a {@link Change}. */
public final class ApprovalCategory {
  /** Id of the special "Submit" action (and category). */
  public static final ApprovalCategory.Id SUBMIT = //
      new ApprovalCategory.Id("SUBM");
  public static final ApprovalCategory.Id CRVW = //
      new ApprovalCategory.Id("CRVW");
  public static final ApprovalCategory.Id VRIF = //
      new ApprovalCategory.Id("VRIF");

  public static class Id extends StringKey<Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = 4)
    protected String id;

    protected Id() {
    }

    public Id(final String a) {
      id = a;
    }

    @Override
    public String get() {
      return id;
    }

    @Override
    protected void set(String newValue) {
      id = newValue;
    }
  }

  /** Internal short unique identifier for this category. */
  @Column(id = 1)
  protected Id categoryId;

  /** Unique name for this category, shown in the web interface to users. */
  @Column(id = 2, length = 20)
  protected String name;

  /** Abbreviated form of {@link #name} for display in very wide tables. */
  @Column(id = 3, length = 4, notNull = false)
  protected String abbreviatedName;

  /** Order of this category within the Approvals table when presented. */
  @Column(id = 4)
  protected short position;

  /** Identity of the function used to aggregate the category's value. */
  @Column(id = 5)
  protected String functionName;

  /** If set, the minimum score is copied during patch set replacement. */
  @Column(id = 6)
  protected boolean copyMinScore;

  /** Computed name derived from {@link #name}. */
  protected String labelName;

  protected ApprovalCategory() {
  }

  public ApprovalCategory(final ApprovalCategory.Id id, final String name) {
    this.categoryId = id;
    this.name = name;
    this.functionName = "MaxWithBlock";
  }

  public ApprovalCategory.Id getId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String n) {
    name = n;
    labelName = null;
  }

  /** Clean version of {@link #getName()}, e.g. "Code Review" is "Code-Review". */
  public String getLabelName() {
    if (labelName == null) {
      StringBuilder r = new StringBuilder();
      for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (('0' <= c && c <= '9') //
            || ('a' <= c && c <= 'z') //
            || ('A' <= c && c <= 'Z') //
            || (c == '-')) {
          r.append(c);
        } else if (c == ' ') {
          r.append('-');
        }
      }
      labelName = r.toString();
    }
    return labelName;
  }

  public String getAbbreviatedName() {
    return abbreviatedName;
  }

  public void setAbbreviatedName(final String n) {
    abbreviatedName = n;
  }

  public short getPosition() {
    return position;
  }

  public void setPosition(final short p) {
    position = p;
  }

  public String getFunctionName() {
    return functionName;
  }

  public void setFunctionName(final String name) {
    functionName = name;
  }

  public boolean isCopyMinScore() {
    return copyMinScore;
  }

  public void setCopyMinScore(final boolean copy) {
    copyMinScore = copy;
  }
}
