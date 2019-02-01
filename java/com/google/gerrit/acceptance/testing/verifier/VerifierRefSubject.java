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

package com.google.gerrit.acceptance.testing.verifier;

import static com.google.common.truth.Truth.assertAbout;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.acceptance.testsuite.verifier.TestVerifier;
import com.google.gerrit.acceptance.testsuite.verifier.VerifierOperations;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.verifier.db.VerifierConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class VerifierRefSubject extends Subject<VerifierRefSubject, String> {
  public interface Factory {
    VerifierRefSubject create(FailureMetadata metadata, String actualVerifierUuid);
  }

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final VerifierOperations verifierOperations;

  @Inject
  VerifierRefSubject(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      VerifierOperations verifierOperations,
      @Assisted FailureMetadata metadata,
      @Assisted String actualVerifierUuid) {
    super(metadata, actualVerifierUuid);
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.verifierOperations = verifierOperations;
  }

  public static VerifierRefSubject assertThat(Factory factory, String verifierUuid) {
    return assertAbout(
            new Subject.Factory<VerifierRefSubject, String>() {
              @Override
              public VerifierRefSubject createSubject(FailureMetadata metadata, String actual) {
                return factory.create(metadata, actual);
              }
            })
        .that(verifierUuid);
  }

  public StringSubject configText() throws IOException, ConfigInvalidException {
    return Truth.assertThat(getVerifierConfig()).named("verifierConfig");
  }

  private String getVerifierConfig() throws IOException, ConfigInvalidException {
    isNotNull();
    String verifierUuid = actual();

    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      String verifierRef = RefNames.refsVerifiers(verifierUuid);
      Ref ref = repo.exactRef(verifierRef);
      Truth.assertThat(ref).named(verifierRef).isNotNull();
      RevCommit c = rw.parseCommit(ref.getObjectId());

      TestVerifier verifier = verifierOperations.verifier(verifierUuid).get();
      long timestampDiffMs = Math.abs(c.getCommitTime() * 1000L - verifier.createdOn().getTime());
      Truth.assertThat(timestampDiffMs).isAtMost(SECONDS.toMillis(1));

      // Check the 'verifier.config' file.
      try (TreeWalk tw = TreeWalk.forPath(or, VerifierConfig.VERIFIER_CONFIG_FILE, c.getTree())) {
        Truth.assertThat(tw).named("tree in %s", verifierRef).isNotNull();

        // Parse as Config to ensure it's a valid config file.
        Config cfg = new Config();
        cfg.fromText(new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8));

        return cfg.toText();
      }
    }
  }
}
