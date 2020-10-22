//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.entities;

import com.google.auto.value.AutoValue;

/**
 * A modified region between 2 versions of the same content. This is the Gerrit entity class
 * corresponding to {@link org.eclipse.jgit.diff.Edit}.
 */
@AutoValue
public abstract class Edit {
  public static Edit create(int beginA, int endA, int beginB, int endB) {
    return new AutoValue_Edit(beginA, endA, beginB, endB);
  }

  public static Edit fromJGitEdit(org.eclipse.jgit.diff.Edit jgitEdit) {
    return create(
        jgitEdit.getBeginA(), jgitEdit.getEndA(), jgitEdit.getBeginB(), jgitEdit.getEndB());
  }

  /** Start of a region in sequence A. */
  public abstract int beginA();

  /** End of a region in sequence A. */
  public abstract int endA();

  /** Start of a region in sequence B. */
  public abstract int beginB();

  /** End of a region in sequence B. */
  public abstract int endB();
}
