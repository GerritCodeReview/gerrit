// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.entities.Project.NameKey;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.rules.TestRule;

/**
 * Test rule required to run {@link AbstractDaemonTest}.
 *
 * <p>The Google internal implementation uses own infrastructure instead of the {@link
 * GerritServer}.
 */
@UsedAt(Project.GOOGLE)
public interface DaemonTestRule extends TestRule {
  TestConfigRule configRule();

  ServerTestRule server();

  TimeSettingsTestRule timeSettingsRule();

  TestRepository<InMemoryRepository> cloneProject(NameKey p, TestAccount testAccount)
      throws Exception;

  String name(String name);
}
