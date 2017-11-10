// Copyright (C) 2017 The Android Open Source Project
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

/** Type of formatting for this patch. */
public enum PatchType implements CodedEnum {
  /**
   * A textual difference between two versions.
   *
   * <p>A UNIFIED patch can be rendered in multiple ways. Most commonly, it is rendered as a side by
   * side display using two columns, left column for the old version, right column for the new
   * version. A UNIFIED patch can also be formatted in a number of standard "patch script" styles,
   * but typically is formatted in the POSIX standard unified diff format.
   *
   * <p>Usually Gerrit renders a UNIFIED patch in a PatchScreen.SideBySide view, presenting the file
   * in two columns. If the user chooses, a PatchScreen.Unified is also a valid display method.
   */
  UNIFIED('U'),

  /**
   * Difference of two (or more) binary contents.
   *
   * <p>A BINARY patch cannot be viewed in a text display, as it represents a change in binary
   * content at the associated path, for example, an image file has been replaced with a different
   * image.
   *
   * <p>Gerrit can only render a BINARY file in a PatchScreen.Unified view, as the only information
   * it can display is the old and new file content hashes.
   */
  BINARY('B');

  private final char code;

  PatchType(char c) {
    code = c;
  }

  @Override
  public char getCode() {
    return code;
  }

  public static PatchType forCode(char c) {
    for (PatchType s : PatchType.values()) {
      if (s.code == c) {
        return s;
      }
    }
    return null;
  }
}
