// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.AI_REVIEW;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.schema.NoteDbSchemaVersion;
import com.google.gerrit.server.schema.Schema_187;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@Sandboxed
public class Schema_187IT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private NoteDbSchemaVersion.Arguments args;

  private final TestUpdateUI testUpdateUI = new TestUpdateUI();

  @Test
  public void grantsAiReviewOnRefsHeadsWhenMissing() throws Exception {
    // AbstractDaemonTest bootstraps All-Projects with the seed already present;
    // remove it so the migration has work to do (mirrors an upgrade from before
    // the permission existed).
    projectOperations
        .allProjectsForUpdate()
        .remove(permissionKey(AI_REVIEW).ref("refs/heads/*"))
        .update();
    assertThat(aiReviewRule()).isEmpty();

    RevCommit oldHead = configHead();
    runMigration();

    assertThat(configHead()).isNotEqualTo(oldHead);
    assertThat(aiReviewRule()).containsExactly("group Registered Users");
  }

  @Test
  public void skipsWhenAiReviewRuleAlreadyExists() throws Exception {
    // The bootstrap seed is present by default; the migration must not overwrite
    // an existing rule (idempotent, admin customizations preserved).
    assertThat(aiReviewRule()).isNotEmpty();

    RevCommit oldHead = configHead();
    runMigration();

    assertThat(configHead()).isEqualTo(oldHead);
  }

  private void runMigration() throws Exception {
    new Schema_187().upgrade(args, testUpdateUI);
  }

  private RevCommit configHead() {
    return projectOperations.project(allProjects).getHead(RefNames.REFS_CONFIG);
  }

  private List<String> aiReviewRule() {
    Config cfg = projectOperations.project(allProjects).getConfig();
    return Arrays.asList(cfg.getStringList("access", "refs/heads/*", AI_REVIEW));
  }
}
