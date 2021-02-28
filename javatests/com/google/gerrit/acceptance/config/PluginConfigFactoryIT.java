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

package com.google.gerrit.acceptance.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class PluginConfigFactoryIT extends AbstractDaemonTest {
  private static final String PLUGIN_NAME = "test-plugin";

  @Inject private PluginConfigFactory pluginConfigFactory;

  @Test
  public void emptySubSectionsCanBeRead() throws Exception {
    updatePluginConfig(project, "[section \"subsection\"]");
    Config cfg = pluginConfigFactory.getProjectPluginConfigWithInheritance(project, PLUGIN_NAME);
    assertThat(cfg.getSubsections("section")).containsExactly("subsection");
  }

  private void updatePluginConfig(Project.NameKey project, String pluginConfig) throws Exception {
    try (TestRepository<Repository> testRepo =
        new TestRepository<>(repoManager.openRepository(project))) {
      Ref ref = testRepo.getRepository().exactRef(RefNames.REFS_CONFIG);
      RevCommit head = testRepo.getRevWalk().parseCommit(ref.getObjectId());
      testRepo.update(
          RefNames.REFS_CONFIG,
          testRepo
              .commit()
              .parent(head)
              .message("Configure plugin")
              .add(PLUGIN_NAME + ".config", pluginConfig));
    }
    projectCache.evict(project);
  }
}
