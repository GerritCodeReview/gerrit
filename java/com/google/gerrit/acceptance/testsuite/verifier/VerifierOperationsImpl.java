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

package com.google.gerrit.acceptance.testsuite.verifier;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.acceptance.testsuite.verifier.TestVerifierUpdate.Builder;
import com.google.gerrit.common.errors.NoSuchVerifierException;
import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierCreation;
import com.google.gerrit.server.verifier.VerifierJson;
import com.google.gerrit.server.verifier.VerifierUpdate;
import com.google.gerrit.server.verifier.VerifierUuid;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gerrit.server.verifier.db.VerifierConfig;
import com.google.gerrit.server.verifier.db.VerifiersByRepositoryNotes;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * The implementation of {@code VerifierOperations}.
 *
 * <p>There is only one implementation of {@code VerifierOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class VerifierOperationsImpl implements VerifierOperations {
  private final Verifiers verifiers;
  private final VerifiersUpdate verifiersUpdate;
  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final VerifierJson verifierJson;

  @Inject
  public VerifierOperationsImpl(
      Verifiers verifiers,
      @ServerInitiated VerifiersUpdate verifiersUpdate,
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      VerifierJson verifierJson) {
    this.verifiers = verifiers;
    this.verifiersUpdate = verifiersUpdate;
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.verifierJson = verifierJson;
  }

  @Override
  public PerVerifierOperations verifier(String verifierUuid) {
    return new PerVerifierOperationsImpl(verifierUuid);
  }

  @Override
  public TestVerifierCreation.Builder newVerifier() {
    return TestVerifierCreation.builder(this::createNewVerifier);
  }

  private String createNewVerifier(TestVerifierCreation testVerifierCreation)
      throws OrmDuplicateKeyException, ConfigInvalidException, IOException {
    VerifierCreation verifierCreation = toVerifierCreation(testVerifierCreation);
    VerifierUpdate verifierUpdate = toVerifierUpdate(testVerifierCreation);
    Verifier verifier = verifiersUpdate.createVerifier(verifierCreation, verifierUpdate);
    return verifier.getUuid();
  }

  private VerifierCreation toVerifierCreation(TestVerifierCreation verifierCreation) {
    String verifierUuid = VerifierUuid.make("test-verifier");
    String verifierName = verifierCreation.name().orElse("verifier-with-uuid-" + verifierUuid);
    Project.NameKey repository = verifierCreation.repository().orElse(allProjectsName);
    return VerifierCreation.builder()
        .setVerifierUuid(verifierUuid)
        .setName(verifierName)
        .setRepository(repository)
        .build();
  }

  private static VerifierUpdate toVerifierUpdate(TestVerifierCreation verifierCreation) {
    VerifierUpdate.Builder builder = VerifierUpdate.builder();
    verifierCreation.name().ifPresent(builder::setName);
    verifierCreation.description().ifPresent(builder::setDescription);
    verifierCreation.url().ifPresent(builder::setUrl);
    verifierCreation.repository().ifPresent(builder::setRepository);
    return builder.build();
  }

  @Override
  public ImmutableSet<String> verifiersOf(Project.NameKey repositoryName) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(RefNames.REFS_META_VERIFIERS);
      if (ref == null) {
        return ImmutableSet.of();
      }

      RevCommit c = rw.parseCommit(ref.getObjectId());
      try (TreeWalk tw =
          TreeWalk.forPath(
              or,
              VerifiersByRepositoryNotes.computeRepositorySha1(repositoryName).getName(),
              c.getTree())) {
        if (tw == null) {
          return ImmutableSet.of();
        }

        return ImmutableSet.copyOf(
            Splitter.on('\n')
                .splitToList(new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8)));
      }
    }
  }

  @Override
  public ImmutableSet<ObjectId> sha1sOfRepositoriesWithVerifiers() throws IOException {
    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(RefNames.REFS_META_VERIFIERS);
      if (ref == null) {
        return ImmutableSet.of();
      }

      return Streams.stream(NoteMap.read(or, rw.parseCommit(ref.getObjectId())))
          .map(ObjectId::copy)
          .collect(toImmutableSet());
    }
  }

  private class PerVerifierOperationsImpl implements PerVerifierOperations {
    private final String verifierUuid;

    PerVerifierOperationsImpl(String verifierUuid) {
      this.verifierUuid = verifierUuid;
    }

    @Override
    public boolean exists() {
      return getVerifier(verifierUuid).isPresent();
    }

    @Override
    public TestVerifier get() {
      Optional<Verifier> verifier = getVerifier(verifierUuid);
      checkState(verifier.isPresent(), "Tried to get non-existing test verifier");
      return toTestVerifier(verifier.get());
    }

    private Optional<Verifier> getVerifier(String verifierUuid) {
      try {
        return verifiers.getVerifier(verifierUuid);
      } catch (IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }

    private TestVerifier toTestVerifier(Verifier verifier) {
      return TestVerifier.builder()
          .uuid(verifier.getUuid())
          .name(verifier.getName())
          .description(verifier.getDescription())
          .url(verifier.getUrl())
          .repository(verifier.getRepository())
          .createdOn(verifier.getCreatedOn())
          .updatedOn(verifier.getUpdatedOn())
          .refState(verifier.getRefState())
          .build();
    }

    @Override
    public RevCommit commit() throws IOException {
      Optional<Verifier> verifier = getVerifier(verifierUuid);
      checkState(verifier.isPresent(), "Tried to get commit for a non-existing test verifier");

      try (Repository repo = repoManager.openRepository(allProjectsName);
          RevWalk rw = new RevWalk(repo)) {
        return rw.parseCommit(verifier.get().getRefState());
      }
    }

    @Override
    public String configText() throws IOException, ConfigInvalidException {
      Optional<Verifier> verifier = getVerifier(verifierUuid);
      checkState(verifier.isPresent(), "Tried to get config text for a non-existing test verifier");

      try (Repository repo = repoManager.openRepository(allProjectsName);
          RevWalk rw = new RevWalk(repo);
          ObjectReader or = repo.newObjectReader()) {
        // Parse as Config to ensure it's a valid config file.
        return new BlobBasedConfig(
                null, repo, verifier.get().getRefState(), VerifierConfig.VERIFIER_CONFIG_FILE)
            .toText();
      }
    }

    @Override
    public VerifierInfo asInfo() {
      Optional<Verifier> verifier = getVerifier(verifierUuid);
      checkState(verifier.isPresent(), "Tried to get a non-existing test verifier as VerifierInfo");
      return verifierJson.format(verifier.get());
    }

    public Builder forUpdate() {
      return TestVerifierUpdate.builder(this::updateVerifier);
    }

    private void updateVerifier(TestVerifierUpdate testVerifierUpdate)
        throws NoSuchVerifierException, ConfigInvalidException, IOException {
      VerifierUpdate verifierUpdate = toVerifierUpdate(testVerifierUpdate);
      verifiersUpdate.updateVerifier(verifierUuid, verifierUpdate);
    }

    private VerifierUpdate toVerifierUpdate(TestVerifierUpdate verifierUpdate) {
      VerifierUpdate.Builder builder = VerifierUpdate.builder();
      verifierUpdate.name().ifPresent(builder::setName);
      verifierUpdate.description().ifPresent(builder::setDescription);
      verifierUpdate.url().ifPresent(builder::setUrl);
      verifierUpdate.repository().ifPresent(builder::setRepository);
      return builder.build();
    }
  }
}
