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

import com.google.gerrit.client.workflow.CategoryFunction;
import com.google.gerrit.client.workflow.MaxWithBlock;
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

  /** Id of the special "Push Annotated Tag" action (and category). */
  public static final ApprovalCategory.Id PUSH_TAG =
      new ApprovalCategory.Id("pTAG");

  /** Id of the special "Push Branch" action (and category). */
  public static final ApprovalCategory.Id PUSH_HEAD =
      new ApprovalCategory.Id("pHD");
  public static final short PUSH_HEAD_UPDATE = 1;
  public static final short PUSH_HEAD_CREATE = 2;
  public static final short PUSH_HEAD_REPLACE = 3;

  public static class Id extends StringKey<Key<?>> {
    @Column(length = 4)
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
  @Column
  protected Id categoryId;

  /** Unique name for this category, shown in the web interface to users. */
  @Column(length = 20)
  protected String name;

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
  @Column
  protected short position;

  /** Identity of the function used to aggregate the category's value. */
  @Column
  protected String functionName;

  protected ApprovalCategory() {
  }

  public ApprovalCategory(final ApprovalCategory.Id id, final String name) {
    this.categoryId = id;
    this.name = name;
    this.functionName = MaxWithBlock.NAME;
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

  public short getPosition() {
    return position;
  }

  public void setPosition(final short p) {
    position = p;
  }

  public boolean isAction() {
    return position < 0;
  }

  public String getFunctionName() {
    return functionName;
  }

  public void setFunctionName(final String name) {
    functionName = name;
  }

  public CategoryFunction getFunction() {
    return CategoryFunction.forName(functionName);
  }
}
