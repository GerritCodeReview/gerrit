// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.git.testing.PushResultSubject.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.Arrays;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Before;
import org.junit.Test;

public class PushPermissionsIT extends AbstractDaemonTest {
  @Before
  public void setUp() throws Exception {
    // Remove all push-related permissions, so they can be added back individually by test methods.
    try (MetaDataUpdate md = metaDataUpdateFactory.create(allProjects)) {
      ProjectConfig cfg = ProjectConfig.read(md);
      removeAllBranchPermissions(cfg, Permission.PUSH);
      removeAllBranchPermissions(cfg, Permission.CREATE);
      removeAllBranchPermissions(cfg, Permission.DELETE);
      removeAllBranchPermissions(cfg, Permission.PUSH_MERGE);
      saveProjectConfig(allProjects, cfg);
    }
  }

  @Test
  public void noDirectPushPermissions() throws Exception {
    testRepo.branch("HEAD").commit().create();
    PushResult r = push("HEAD:refs/heads/master");
    assertThat(r).onlyRef("refs/heads/master").isRejected("prohibited by Gerrit: ref update access denied");
    assertThat(r)
        .hasMessages(
            "Branch refs/heads/master:",
            "You are not allowed to perform this operation.",
            "To push into this reference you need 'Push' rights.",
            "User: admin",
            "Please read the documentation and contact an administrator",
            "if you feel the configuration is incorrect");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  private static void removeAllBranchPermissions(ProjectConfig cfg, String permission) {
    cfg.getAccessSections()
        .stream()
        .filter(s -> s.getName().startsWith("refs/heads/") || s.getName().equals("refs/*"))
        .forEach(s -> s.removePermission(permission));
  }

  private PushResult push(String... refSpecs) throws Exception {
    Iterable<PushResult> results =
        testRepo
            .git()
            .push()
            .setRemote("origin")
            .setRefSpecs(Arrays.stream(refSpecs).map(RefSpec::new).collect(toList()))
            .call();
    assertWithMessage("expected 1 PushResult").that(results).hasSize(1);
    return results.iterator().next();
  }
}
