// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.index.query.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertAbout;
import static java.util.Objects.requireNonNull;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.index.query.QueryParser;
import org.antlr.runtime.tree.Tree;

public class TreeSubject extends Subject {
  public static TreeSubject assertThat(Tree actual) {
    return assertAbout(TreeSubject::new).that(actual);
  }

  private final Tree tree;

  private TreeSubject(FailureMetadata failureMetadata, Tree tree) {
    super(failureMetadata, tree);
    this.tree = tree;
  }

  public void hasType(int expectedType) {
    isNotNull();
    check("getType()").that(typeName(tree.getType())).isEqualTo(typeName(expectedType));
  }

  public void hasText(String expectedText) {
    requireNonNull(expectedText);
    isNotNull();
    check("getText()").that(tree.getText()).isEqualTo(expectedText);
  }

  public void hasNoChildren() {
    isNotNull();
    check("getChildCount()").that(tree.getChildCount()).isEqualTo(0);
  }

  public void hasChildCount(int expectedChildCount) {
    checkArgument(
        expectedChildCount > 0, "expected child count must be positive: %s", expectedChildCount);
    isNotNull();
    check("getChildCount()").that(tree.getChildCount()).isEqualTo(expectedChildCount);
  }

  public TreeSubject child(int childIndex) {
    isNotNull();
    return check("getChild(%s)", childIndex)
        .about(TreeSubject::new)
        .that(tree.getChild(childIndex));
  }

  private static String typeName(int type) {
    checkArgument(
        type >= 0 && type < QueryParser.tokenNames.length,
        "invalid token type %s, max is %s",
        type,
        QueryParser.tokenNames.length - 1);
    return QueryParser.tokenNames[type];
  }
}
