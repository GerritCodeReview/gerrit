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
import static com.google.common.truth.Truth.assert_;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.verifier.VerifierUuid;
import com.google.gerrit.server.verifier.db.VerifierConfig;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
@Sandboxed
public class ListVerifiersIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private VerifierOperations verifierOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("verifier", "api", "enabled", true);
    return cfg;
  }

  @Test
  public void listAll() throws Exception {
    String verifierUuid1 =
        verifierOperations.newVerifier().name("verifier-with-name-only").create();
    String verifierUuid2 =
        verifierOperations
            .newVerifier()
            .name("verifier-with-description")
            .description("A description.")
            .create();
    String verifierUuid3 =
        verifierOperations
            .newVerifier()
            .name("verifier-with-url")
            .url("http://example.com/my-verifier")
            .create();
    List<VerifierInfo> expectedVerifierInfos =
        ImmutableList.of(verifierUuid1, verifierUuid2, verifierUuid3)
            .stream()
            .sorted()
            .map(uuid -> verifierOperations.verifier(uuid).asInfo())
            .collect(toList());

    List<VerifierInfo> allVerifiers = gApi.verifiers().all();
    assertThat(allVerifiers).isEqualTo(expectedVerifierInfos);
  }

  @Test
  public void listWithoutAdministrateVerifiersCapabilityFails() throws Exception {
    verifierOperations.newVerifier().name("my-verifier").create();

    requestScopeOperations.setApiUser(user.getId());

    try {
      gApi.verifiers().all();
      assert_().fail("expected AuthException");
    } catch (AuthException e) {
      assertThat(e.getMessage()).isEqualTo("administrate verifiers not permitted");
    }
  }

  @Test
  public void listIgnoresInvalidVerifiers() throws Exception {
    String verifierUuid = verifierOperations.newVerifier().name("verifier-with-name-only").create();
    createInvalidVerifier();

    List<VerifierInfo> allVerifiers = gApi.verifiers().all();
    assertThat(allVerifiers).containsExactly(verifierOperations.verifier(verifierUuid).asInfo());
  }

  private void createInvalidVerifier() throws Exception {
    try (Repository repo = repoManager.openRepository(allProjects)) {
      new TestRepository<>(repo)
          .branch(RefNames.refsVerifiers(VerifierUuid.make("my-verifier")))
          .commit()
          .add(VerifierConfig.VERIFIER_CONFIG_FILE, "invalid-config")
          .create();
    }
  }
}
