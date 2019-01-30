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

package com.google.gerrit.acceptance.api.verifier;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.api.verifiers.VerifierInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.gerrit.server.verifier.db.VerifierConfig;
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
public class CreateVerifierIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Verifiers verifiers;

  @Test
  public void createVerifier() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.uuid).isNotNull();
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isNotNull();

    assertVerifierRef(info.uuid, "[verifier]\n\tname = " + input.name + "\n");
  }

  @Test
  public void createVerifierWithDescription() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.description = "some description";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.description).isEqualTo(input.description);

    assertVerifierRef(
        info.uuid,
        "[verifier]\n"
            + "\tname = "
            + input.name
            + "\n"
            + "\tdescription = "
            + input.description
            + "\n");
  }

  @Test
  public void createVerifierNameIsTrimmed() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = " my-verifier ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.name).isEqualTo("my-verifier");

    assertVerifierRef(info.uuid, "[verifier]\n\tname = my-verifier\n");
  }

  @Test
  public void createVerifierDescriptionIsTrimmed() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    input.description = " some description ";
    VerifierInfo info = gApi.verifiers().create(input).get();
    assertThat(info.description).isEqualTo("some description");

    assertVerifierRef(
        info.uuid, "[verifier]\n\tname = " + input.name + "\n\tdescription = some description\n");
  }

  @Test
  public void createVerifiersWithSameName() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";
    VerifierInfo info1 = gApi.verifiers().create(input).get();
    assertThat(info1.name).isEqualTo(input.name);

    VerifierInfo info2 = gApi.verifiers().create(input).get();
    assertThat(info2.name).isEqualTo(input.name);

    assertThat(info2.uuid).isNotEqualTo(info1.uuid);
  }

  @Test
  public void createVerifierWithoutNameFails() throws Exception {
    VerifierInput input = new VerifierInput();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.verifiers().create(input);
  }

  @Test
  public void createVerifierWithEmptyNameFails() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = "";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.verifiers().create(input);
  }

  @Test
  public void createVerifierWithEmptyNameAfterTrimFails() throws Exception {
    VerifierInput input = new VerifierInput();
    input.name = " ";

    exception.expect(BadRequestException.class);
    exception.expectMessage("name is required");
    gApi.verifiers().create(input);
  }

  @Test
  public void createVerifierWithoutAdministrateVerifiersCapabilityFails() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    VerifierInput input = new VerifierInput();
    input.name = "my-verifier";

    exception.expect(AuthException.class);
    exception.expectMessage("administrate verifiers not permitted");
    gApi.verifiers().create(input);
  }

  private void assertVerifierRef(String verifierUuid, String expectedVerifierConfig)
      throws Exception {
    try (Repository repo = repoManager.openRepository(allProjects);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(RefNames.refsVerifiers(verifierUuid));
      assertThat(ref).isNotNull();
      RevCommit c = rw.parseCommit(ref.getObjectId());

      // TODO(ekempin): Use test API to get verifier.
      Verifier verifier = verifiers.getVerifier(verifierUuid).get();
      long timestampDiffMs =
          Math.abs(c.getCommitTime() * 1000L - verifier.getCreatedOn().getTime());
      assertThat(timestampDiffMs).isAtMost(SECONDS.toMillis(1));

      // Check the 'verifier.config' file.
      try (TreeWalk tw = TreeWalk.forPath(or, VerifierConfig.VERIFIER_CONFIG_FILE, c.getTree())) {
        assertThat(tw).isNotNull();

        // Parse as Config to ensure it's a valid config file.
        Config cfg = new Config();
        cfg.fromText(new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8));

        // Verify that the content is as expected.
        assertThat(cfg.toText()).isEqualTo(expectedVerifierConfig);
      }
    }
  }
}
