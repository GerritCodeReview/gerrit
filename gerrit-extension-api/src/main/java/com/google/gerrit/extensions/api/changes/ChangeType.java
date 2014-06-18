// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.common.CodedEnum;

/** Type of modification made to the file path. */
public enum ChangeType implements CodedEnum {
  /** Path is being created/introduced by this patch. */
  ADDED('A'),

  /** Path already exists, and has updated content. */
  MODIFIED('M'),

  /** Path existed, but is being removed by this patch. */
  DELETED('D'),

  /** Path existed at {@link Patch#getSourceFileName()} but was moved. */
  RENAMED('R'),

  /** Path was copied from {@link Patch#getSourceFileName()}. */
  COPIED('C'),

  /** Sufficient amount of content changed to claim the file was rewritten. */
  REWRITE('W');

  private final char code;

  private ChangeType(final char c) {
    code = c;
  }

  public char getCode() {
    return code;
  }

  public boolean matches(String s) {
    return s != null && s.length() == 1 && s.charAt(0) == code;
  }

  public static ChangeType forCode(final char c) {
    for (final ChangeType s : ChangeType.values()) {
      if (s.code == c) {
        return s;
      }
    }
    return null;
  }
}