// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import com.google.common.collect.ImmutableList;
import java.util.function.Function;

/** Builder to simplify specifying multiple parents for a change. */
public class MultipleParentBuilder<T> {
  private final Function<ImmutableList<TestCommitIdentifier>, T> parentsToBuilderAdder;
  private final ImmutableList.Builder<TestCommitIdentifier> parents;

  public MultipleParentBuilder(
      Function<ImmutableList<TestCommitIdentifier>, T> parentsToBuilderAdder,
      TestCommitIdentifier firstParent) {
    this.parentsToBuilderAdder = parentsToBuilderAdder;
    parents = ImmutableList.builder();
    parents.add(firstParent);
  }

  /** Adds an intermediate parent. */
  public ParentBuilder<MultipleParentBuilder<T>> followedBy() {
    return new ParentBuilder<>(
        parent -> {
          parents.add(parent);
          return this;
        });
  }

  /** Adds the last parent. */
  public ParentBuilder<T> and() {
    return new ParentBuilder<>(
        (parent) -> parentsToBuilderAdder.apply(parents.add(parent).build()));
  }
}
