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

package com.google.gerrit.plugins.checks.acceptance.testsuite;

import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.reviewdb.client.RevId;
import java.util.Map;
import org.eclipse.jgit.revwalk.RevCommit;

// TODO(gerrit-team): Add Java doc
public interface CheckOperations {

  PerCheckOperations check(TestCheckKey key);

  TestCheckUpdate.Builder newChecker(TestCheckKey key);

  //  ImmutableSet<String> checkersOf(Project.NameKey repositoryName) throws IOException;

  interface PerCheckOperations {

    boolean exists() throws Exception;

    TestCheck get() throws Exception;

    RevCommit commit() throws Exception;

    Map<RevId, String> notesAsText() throws Exception;

    CheckInfo asInfo() throws Exception;

    TestCheckUpdate.Builder forUpdate();
  }
}
