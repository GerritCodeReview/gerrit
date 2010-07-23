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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.Key;
import com.google.gwtorm.client.StringKey;

/** Types of approvals that can be associated with a {@link Change}. */
public final class ApprovalCategory {
  /** Id of the special "Submit" action (and category). */
  public static final ApprovalCategory.Id SUBMIT =
      new ApprovalCategory.Id("SUBM");

  /** Id of the special "Read" action (and category). */
  public static final ApprovalCategory.Id READ =
      new ApprovalCategory.Id("READ");

  /** Id of the special "Own" category; manages a project. */
  public static final ApprovalCategory.Id OWN = new ApprovalCategory.Id("OWN");

  /** Id of the special "Push Annotated Tag" action (and category). */
  public static final ApprovalCategory.Id PUSH_TAG =
      new ApprovalCategory.Id("pTAG");
  public static final short PUSH_TAG_SIGNED = 1;
  public static final short PUSH_TAG_ANNOTATED = 2;

  /** Id of the special "Push Branch" action (and category). */
  public static final ApprovalCategory.Id PUSH_HEAD =
      new ApprovalCategory.Id("pHD");
  public static final short PUSH_HEAD_UPDATE = 1;
  public static final short PUSH_HEAD_CREATE = 2;
  public static final short PUSH_HEAD_REPLACE = 3;

  /** Id of the special "Forge Identity" category. */
  public static final ApprovalCategory.Id FORGE_IDENTITY =
      new ApprovalCategory.Id("FORG");
  public static final short FORGE_AUTHOR = 1;
  public static final short FORGE_COMMITTER = 2;
  public static final short FORGE_SERVER = 3;

  /** Id of the special "Create Project" category **/
  public static final ApprovalCategory.Id CREATE_PROJECT =
    new ApprovalCategory.Id("CPRJ");

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

    /** True if the right can be assigned on the wild project. */
    public boolean canBeOnWildProject() {
      if (OWN.equals(this)) {
        return false;
      } else {
        return true;
      }
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

  /**
   * Order of this category within the Approvals table when presented.
   * <p>
   * If < 0 (e.g. -1) this category is not shown in the Approvals table but is
   * instead considered to be an action that the user might be able to perform,
   * e.g. "Submit".
   * <p>
   * If >= 0 this category is shown in the Approvals table, sorted along with
   * its siblings by <code>position, name</code>.
   */
  @Column(id = 4)
  protected short position;

  /** Identity of the function used to aggregate the category's value. */
  @Column(id = 5)
  protected String functionName;

  /** If set, the minimum score is copied during patch set replacement. */
  @Column(id = 6)
  protected boolean copyMinScore;

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

  public boolean isAction() {
    return position < 0;
  }

  public boolean isRange() {
    return !isAction();
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
