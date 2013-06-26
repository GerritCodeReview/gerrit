// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.server.query.Predicate;

public interface IndexRewrite {
  /** Instance indicating secondary index is disabled. */
  public static final IndexRewrite DISABLED = new IndexRewrite() {
    @Override
    public Predicate<ChangeData> rewrite(Predicate<ChangeData> in) {
      return in;
    }
  };

  /**
   * Rewrite a predicate to push as much boolean logic as possible into the
   * secondary index query system.
   *
   * @param in predicate to rewrite.
   * @return a predicate with some subtrees replaced with predicates that are
   *     also sources that query the index directly.
   */
  public Predicate<ChangeData> rewrite(Predicate<ChangeData> in);
}
