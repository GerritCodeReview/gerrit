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

package com.google.gerrit.acceptance.testsuite.checker;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.acceptance.testsuite.checker.TestCheckerUpdate.Builder;
import com.google.gerrit.common.errors.NoSuchCheckerException;
import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.checker.Checker;
import com.google.gerrit.server.checker.CheckerCreation;
import com.google.gerrit.server.checker.CheckerJson;
import com.google.gerrit.server.checker.CheckerUpdate;
import com.google.gerrit.server.checker.CheckerUuid;
import com.google.gerrit.server.checker.Checkers;
import com.google.gerrit.server.checker.CheckersUpdate;
import com.google.gerrit.server.checker.db.CheckerConfig;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * The implementation of {@code CheckerOperations}.
 *
 * <p>There is only one implementation of {@code CheckerOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
public class CheckerOperationsImpl implements CheckerOperations {
  private final Checkers checkers;
  private final CheckersUpdate checkersUpdate;
  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final CheckerJson checkerJson;

  @Inject
  public CheckerOperationsImpl(
      Checkers checkers,
      @ServerInitiated CheckersUpdate checkersUpdate,
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      CheckerJson checkerJson) {
    this.checkers = checkers;
    this.checkersUpdate = checkersUpdate;
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.checkerJson = checkerJson;
  }

  @Override
  public PerCheckerOperations checker(String checkerUuid) {
    return new PerCheckerOperationsImpl(checkerUuid);
  }

  @Override
  public TestCheckerCreation.Builder newChecker() {
    return TestCheckerCreation.builder(this::createNewChecker);
  }

  private String createNewChecker(TestCheckerCreation testCheckerCreation)
      throws OrmDuplicateKeyException, ConfigInvalidException, IOException {
    CheckerCreation checkerCreation = toCheckerCreation(testCheckerCreation);
    CheckerUpdate checkerUpdate = toCheckerUpdate(testCheckerCreation);
    Checker checker = checkersUpdate.createChecker(checkerCreation, checkerUpdate);
    return checker.getUuid();
  }

  private CheckerCreation toCheckerCreation(TestCheckerCreation checkerCreation) {
    String checkerUuid = CheckerUuid.make("test-checker");
    String checkerName = checkerCreation.name().orElse("checker-with-uuid-" + checkerUuid);
    return CheckerCreation.builder().setCheckerUuid(checkerUuid).setName(checkerName).build();
  }

  private static CheckerUpdate toCheckerUpdate(TestCheckerCreation checkerCreation) {
    CheckerUpdate.Builder builder = CheckerUpdate.builder();
    checkerCreation.name().ifPresent(builder::setName);
    checkerCreation.description().ifPresent(builder::setDescription);
    checkerCreation.url().ifPresent(builder::setUrl);
    return builder.build();
  }

  private class PerCheckerOperationsImpl implements PerCheckerOperations {
    private final String checkerUuid;

    PerCheckerOperationsImpl(String checkerUuid) {
      this.checkerUuid = checkerUuid;
    }

    @Override
    public boolean exists() {
      return getChecker(checkerUuid).isPresent();
    }

    @Override
    public TestChecker get() {
      Optional<Checker> checker = getChecker(checkerUuid);
      checkState(checker.isPresent(), "Tried to get non-existing test checker");
      return toTestChecker(checker.get());
    }

    private Optional<Checker> getChecker(String checkerUuid) {
      try {
        return checkers.getChecker(checkerUuid);
      } catch (IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }

    private TestChecker toTestChecker(Checker checker) {
      return TestChecker.builder()
          .uuid(checker.getUuid())
          .name(checker.getName())
          .description(checker.getDescription())
          .url(checker.getUrl())
          .createdOn(checker.getCreatedOn())
          .updatedOn(checker.getUpdatedOn())
          .refState(checker.getRefState())
          .build();
    }

    @Override
    public RevCommit commit() throws IOException {
      Optional<Checker> checker = getChecker(checkerUuid);
      checkState(checker.isPresent(), "Tried to get commit for a non-existing test checker");

      try (Repository repo = repoManager.openRepository(allProjectsName);
          RevWalk rw = new RevWalk(repo)) {
        return rw.parseCommit(checker.get().getRefState());
      }
    }

    @Override
    public String configText() throws IOException, ConfigInvalidException {
      Optional<Checker> checker = getChecker(checkerUuid);
      checkState(checker.isPresent(), "Tried to get config text for a non-existing test checker");

      try (Repository repo = repoManager.openRepository(allProjectsName);
          RevWalk rw = new RevWalk(repo);
          ObjectReader or = repo.newObjectReader()) {
        // Parse as Config to ensure it's a valid config file.
        return new BlobBasedConfig(
                null, repo, checker.get().getRefState(), CheckerConfig.CHECKER_CONFIG_FILE)
            .toText();
      }
    }

    @Override
    public CheckerInfo asInfo() {
      Optional<Checker> checker = getChecker(checkerUuid);
      checkState(checker.isPresent(), "Tried to get a non-existing test checker as CheckerInfo");
      return checkerJson.format(checker.get());
    }

    public Builder forUpdate() {
      return TestCheckerUpdate.builder(this::updateChecker);
    }

    private void updateChecker(TestCheckerUpdate testCheckerUpdate)
        throws NoSuchCheckerException, ConfigInvalidException, IOException {
      CheckerUpdate checkerUpdate = toCheckerUpdate(testCheckerUpdate);
      checkersUpdate.updateChecker(checkerUuid, checkerUpdate);
    }

    private CheckerUpdate toCheckerUpdate(TestCheckerUpdate checkerUpdate) {
      CheckerUpdate.Builder builder = CheckerUpdate.builder();
      checkerUpdate.name().ifPresent(builder::setName);
      checkerUpdate.description().ifPresent(builder::setDescription);
      checkerUpdate.url().ifPresent(builder::setUrl);
      return builder.build();
    }
  }
}
