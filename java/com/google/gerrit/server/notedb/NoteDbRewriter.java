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

package com.google.gerrit.server.notedb;

import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevWalk;

public interface NoteDbRewriter {

  /** Gets the name of the target ref which will be rewritten. */
  String getRefName();

  /**
   * Rewrites the commit history.
   *
   * @param revWalk a {@code RevWalk} instance.
   * @param inserter a {@code ObjectInserter} instance.
   * @param currTip the {@code ObjectId} of the ref's tip commit.
   * @return the {@code ObjectId} of the ref's new tip commit.
   */
  ObjectId rewriteCommitHistory(RevWalk revWalk, ObjectInserter inserter, ObjectId currTip)
      throws IOException, ConfigInvalidException, StorageException;
}
