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

  /** Deletes the file. */
  public T delete() {
    modificationToBuilderAdder.accept(new DeleteFileModification(filePath));
    return builder;
  }

  /**
   * Renames the file while keeping its content.
   *
   * <p>If you want to both rename the file and adjust its content, delete the old path via {@link
   * #delete()} and provide the desired content for the new path via {@link #content(String)}. If
   * you use that approach, make sure to use a new content which is similar enough to the old (at
   * least 60% line similarity) as otherwise Gerrit/Git won't identify it as a rename.
   *
   * <p>To create copied files, you need to go even one step further. Also rename the file you copy
   * at the same time (-> delete old path + add two paths with the old content)! If you also want to
   * adjust the content of the copy, you need to also slightly modify the content of the renamed
   * file. Adjust the content of the copy slightly more if you want to control which file ends up as
   * copy and which as rename (but keep the 60% line similarity threshold in mind).
   *
   * @param newFilePath new path of the file
   */
  public T renameTo(String newFilePath) {
    modificationToBuilderAdder.accept(new RenameFileModification(filePath, newFilePath));
    return builder;
  }
}
