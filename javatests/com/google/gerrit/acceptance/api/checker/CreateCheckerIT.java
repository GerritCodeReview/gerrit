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

package com.google.gerrit.acceptance.api.checker;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.checker.CheckerOperations;
import com.google.gerrit.acceptance.testsuite.checker.TestChecker;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.extensions.api.checkers.CheckerInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.checker.db.CheckerConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class CreateCheckerIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private CheckerOperations checkerOperations;

  @Test
  public void createChecker() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    CheckerInfo info = gApi.checkers().create(input).get();
    assertThat(info.uuid).isNotNull();
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isNotNull();

    assertCheckerRef(info.uuid, "[checker]\n\tname = " + input.name + "\n");
  }

  @Test
  public void createCheckerWithDescription() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.description = "some description";
    CheckerInfo info = gApi.checkers().create(input).get();
    assertThat(info.description).isEqualTo(input.description);

    assertCheckerRef(
        info.uuid,
        "[checker]\n"
            + "\tname = "
            + input.name
            + "\n"
            + "\tdescription = "
            + input.description
            + "\n");
  }

  @Test
  public void createCheckerNameIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = " my-checker ";
    CheckerInfo info = gApi.checkers().create(input).get();
    assertThat(info.name).isEqualTo("my-checker");

    assertCheckerRef(info.uuid, "[checker]\n\tname = my-checker\n");
  }

  @Test
  public void createCheckerDescriptionIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    input.description = " some description ";
    CheckerInfo info = gApi.checkers().create(input).get();
    assertThat(info.description).isEqualTo("some description");

    assertCheckerRef(
        info.uuid, "[checker]\n\tname = " + input.name + "\n\tdescription = some description\n");
  }

  @Test
  public void createCheckersWithSameName() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";
    CheckerInfo info1 = gApi.checkers().create(input).get();
    assertThat(info1.name).isEqualTo(input.name);

    CheckerInfo info2 = gApi.checkers().create(input).get();
    assertThat(info2.name).isEqualTo(input.name);

    assertThat(info2.uuid).isNotEqualTo(info1.uuid);
  }

  @Test
  public void createCheckerWithoutNameFails() throws Exception {
    CheckerInput input = new CheckerInput();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.checkers().create(input);
  }

  @Test
  public void createCheckerWithEmptyNameFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.checkers().create(input);
  }

  @Test
  public void createCheckerWithEmptyNameAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.checkers().create(input);
  }

  @Test
  public void createCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    CheckerInput input = new CheckerInput();
    input.name = "my-checker";

    exception.expect(AuthException.class);
    exception.expectMessage("administrate checkers not permitted");
    gApi.checkers().create(input);
  }

  private void assertCheckerRef(String checkerUuid, String expectedCheckerConfig) throws Exception {
    try (Repository repo = repoManager.openRepository(allProjects);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(RefNames.refsCheckers(checkerUuid));
      assertThat(ref).isNotNull();
      RevCommit c = rw.parseCommit(ref.getObjectId());

      TestChecker checker = checkerOperations.checker(checkerUuid).get();
      long timestampDiffMs = Math.abs(c.getCommitTime() * 1000L - checker.createdOn().getTime());
      assertThat(timestampDiffMs).isAtMost(SECONDS.toMillis(1));

      // Check the 'checker.config' file.
      try (TreeWalk tw = TreeWalk.forPath(or, CheckerConfig.CHECKER_CONFIG_FILE, c.getTree())) {
        assertThat(tw).isNotNull();

        // Parse as Config to ensure it's a valid config file.
        Config cfg = new Config();
        cfg.fromText(new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8));

        // Verify that the content is as expected.
        assertThat(cfg.toText()).isEqualTo(expectedCheckerConfig);
      }
    }
  }
}
