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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.RawInput;

/** Content to be added to a file (new or existing) via change edit. */
public class FileContentInput {
  @DefaultInput public RawInput content;

  /**
   * The file content as a base-64 encoded data URI.
   *
   * <p>If no content is provided, an empty is created or if an existing file is updated the file
   * content is removed so that the file becomes empty.
   *
   * <p>The content must be a SHA1 if the file mode is {@code 160000} (gitlink).
   */
  public String binary_content;

  /**
   * The file mode in octal format.
   *
   * <p>Supported values are {@code 100644} (regular file), {@code 100755} (executable file) and
   * {@code 160000} (gitlink).
   *
   * <p>If unset, new files are created with file mode {@code 100644} (regular file) and for
   * existing files the existing file mode is kept.
   */
  public Integer fileMode;
}
