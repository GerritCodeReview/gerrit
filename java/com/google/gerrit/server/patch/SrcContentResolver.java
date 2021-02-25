// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/** Resolver of the source content of a specific file */
public class SrcContentResolver {

  /**
   * Return the source content of a specific file.
   *
   * @param db Git repository.
   * @param id Git Object ID of the file blob.
   * @param fileMode File mode of the underlying file as recognized by Git.
   * @return byte[] source content of the underlying file if the {@code id} is of type blob, or a
   *     textual representation of the file if it is a git submodule.
   * @throws IOException the object ID does not exist in the repository or cannot be accessed.
   */
  public static byte[] getSourceContent(Repository db, ObjectId id, FileMode fileMode)
      throws IOException {
    if (fileMode.getObjectType() == Constants.OBJ_BLOB) {
      return Text.asByteArray(db.open(id, Constants.OBJ_BLOB));
    }
    if (fileMode.getObjectType() == Constants.OBJ_COMMIT) {
      String strContent = "Subproject commit " + ObjectId.toString(id);
      return strContent.getBytes(UTF_8);
    }
    return Text.NO_BYTES;
  }
}
