/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.DeleteFileModification;
import com.google.gerrit.server.edit.tree.RenameFileModification;
import com.google.gerrit.server.edit.tree.TreeModification;
import java.util.function.Consumer;

/** Builder to simplify file content specification. */
public class FileContentBuilder<T> {
  private final T builder;
  private final String filePath;
  private final Consumer<TreeModification> modificationToBuilderAdder;

  FileContentBuilder(
      T builder, String filePath, Consumer<TreeModification> modificationToBuilderAdder) {
    checkNotNull(Strings.emptyToNull(filePath), "File path must not be null or empty.");
    this.builder = builder;
    this.filePath = filePath;
    this.modificationToBuilderAdder = modificationToBuilderAdder;
  }

  /** Content of the file. Must not be empty. */
  public T content(String content) {
    checkNotNull(
        Strings.emptyToNull(content),
        "Empty file content is not supported. Adjust test API if necessary.");
    modificationToBuilderAdder.accept(
        new ChangeFileContentModification(filePath, RawInputUtil.create(content)));
    return builder;
  }

  public T delete() {
    modificationToBuilderAdder.accept(new DeleteFileModification(filePath));
    return builder;
  }

  public T renameTo(String newFilePath) {
    modificationToBuilderAdder.accept(new RenameFileModification(filePath, newFilePath));
    return builder;
  }
}
