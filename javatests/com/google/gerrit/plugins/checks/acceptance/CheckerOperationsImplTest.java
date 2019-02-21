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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Joiner;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperationsImpl;
import com.google.gerrit.plugins.checks.acceptance.testsuite.TestChecker;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.api.CheckerInput;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckerConfig;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import com.google.gerrit.reviewdb.client.Project;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

public class CheckerOperationsImplTest extends AbstractCheckersTest {
  // Use specific subclass instead of depending on the interface field from the base class.
  private CheckerOperationsImpl checkerOperations;

  @Before
  public void setUp() {
    checkerOperations = plugin.getSysInjector().getInstance(CheckerOperationsImpl.class);
  }

  @Test
  public void checkerCanBeCreatedWithoutSpecifyingAnyParameters() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    CheckerInfo foundChecker = getCheckerFromServer(checkerUuid);
    assertThat(foundChecker.uuid).isEqualTo(checkerUuid);
    assertThat(foundChecker.name).isNotEmpty();
    assertThat(foundChecker.description).isNull();
    assertThat(foundChecker.createdOn).isNotNull();
  }

  @Test
  public void twoCheckersWithoutAnyParametersDoNotClash() throws Exception {
    String checkerUuid1 = checkerOperations.newChecker().create();
    String checkerUuid2 = checkerOperations.newChecker().create();

    TestChecker checker1 = checkerOperations.checker(checkerUuid1).get();
    TestChecker checker2 = checkerOperations.checker(checkerUuid2).get();
    assertThat(checker1.uuid()).isNotEqualTo(checker2.uuid());
  }

  @Test
  public void checkerCreatedByTestApiCanBeRetrievedViaOfficialApi() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    CheckerInfo foundChecker = getCheckerFromServer(checkerUuid);
    assertThat(foundChecker.uuid).isEqualTo(checkerUuid);
  }

  @Test
  public void specifiedNameIsRespectedForCheckerCreation() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("XYZ-123-this-name-must-be-unique").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.name).isEqualTo("XYZ-123-this-name-must-be-unique");
  }

  @Test
  public void specifiedDescriptionIsRespectedForCheckerCreation() throws Exception {
    String checkerUuid = checkerOperations.newChecker().description("A simple checker.").create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.description).isEqualTo("A simple checker.");
  }

  @Test
  public void requestingNoDescriptionIsPossibleForCheckerCreation() throws Exception {
    String checkerUuid = checkerOperations.newChecker().clearDescription().create();

    CheckerInfo checker = getCheckerFromServer(checkerUuid);
    assertThat(checker.description).isNull();
  }

  @Test
  public void existingCheckerCanBeCheckedForExistence() throws Exception {
    String checkerUuid = createCheckerInServer(createArbitraryCheckerInput());

    boolean exists = checkerOperations.checker(checkerUuid).exists();

    assertThat(exists).isTrue();
  }

  @Test
  public void notExistingCheckerCanBeCheckedForExistence() throws Exception {
    String notExistingCheckerUuid = "not-existing-checker";

    boolean exists = checkerOperations.checker(notExistingCheckerUuid).exists();

    assertThat(exists).isFalse();
  }

  @Test
  public void retrievingNotExistingCheckerFails() throws Exception {
    String notExistingCheckerUuid = "not-existing-checker";

    exception.expect(IllegalStateException.class);
    checkerOperations.checker(notExistingCheckerUuid).get();
  }

  @Test
  public void checkerNotCreatedByTestApiCanBeRetrieved() throws Exception {
    CheckerInput input = createArbitraryCheckerInput();
    input.name = "unique checker not created via test API";
    String checkerUuid = createCheckerInServer(input);

    TestChecker foundChecker = checkerOperations.checker(checkerUuid).get();

    assertThat(foundChecker.uuid()).isEqualTo(checkerUuid);
    assertThat(foundChecker.name()).isEqualTo("unique checker not created via test API");
  }

  @Test
  public void uuidOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();

    String foundCheckerUuid = checkerOperations.checker(checkerUuid).get().uuid();

    assertThat(foundCheckerUuid).isEqualTo(checkerUuid);
  }

  @Test
  public void nameOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().name("ABC-789-this-name-must-be-unique").create();

    String checkerName = checkerOperations.checker(checkerUuid).get().name();

    assertThat(checkerName).isEqualTo("ABC-789-this-name-must-be-unique");
  }

  @Test
  public void descriptionOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid =
        checkerOperations
            .newChecker()
            .description("This is a very detailed description of this checker.")
            .create();

    Optional<String> description = checkerOperations.checker(checkerUuid).get().description();

    assertThat(description).hasValue("This is a very detailed description of this checker.");
  }

  @Test
  public void emptyDescriptionOfExistingCheckerCanBeRetrieved() throws Exception {
    String checkerUuid = checkerOperations.newChecker().clearDescription().create();

    Optional<String> description = checkerOperations.checker(checkerUuid).get().description();

    assertThat(description).isEmpty();
  }

  @Test
  public void createdOnOfExistingCheckerCanBeRetrieved() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    Timestamp createdOn = checkerOperations.checker(checker.uuid).get().createdOn();

    assertThat(createdOn).isEqualTo(checker.createdOn);
  }

  @Test
  public void updateWithoutAnyParametersIsANoop() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();
    TestChecker originalChecker = checkerOperations.checker(checkerUuid).get();

    checkerOperations.checker(checkerUuid).forUpdate().update();

    TestChecker updatedChecker = checkerOperations.checker(checkerUuid).get();
    assertThat(updatedChecker).isEqualTo(originalChecker);
  }

  @Test
  public void updateWritesToInternalCheckerSystem() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().description("original description").create();

    checkerOperations.checker(checkerUuid).forUpdate().description("updated description").update();

    String currentDescription = getCheckerFromServer(checkerUuid).description;
    assertThat(currentDescription).isEqualTo("updated description");
  }

  @Test
  public void nameCanBeUpdated() throws Exception {
    String checkerUuid = checkerOperations.newChecker().name("original name").create();

    checkerOperations.checker(checkerUuid).forUpdate().name("updated name").update();

    String currentName = checkerOperations.checker(checkerUuid).get().name();
    assertThat(currentName).isEqualTo("updated name");
  }

  @Test
  public void descriptionCanBeUpdated() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().description("original description").create();

    checkerOperations.checker(checkerUuid).forUpdate().description("updated description").update();

    Optional<String> currentDescription =
        checkerOperations.checker(checkerUuid).get().description();
    assertThat(currentDescription).hasValue("updated description");
  }

  @Test
  public void descriptionCanBeCleared() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().description("original description").create();

    checkerOperations.checker(checkerUuid).forUpdate().clearDescription().update();

    Optional<String> currentDescription =
        checkerOperations.checker(checkerUuid).get().description();
    assertThat(currentDescription).isEmpty();
  }

  @Test
  public void statusCanBeUpdated() throws Exception {
    String checkerUuid =
        checkerOperations.newChecker().description("original description").create();
    assertThat(checkerOperations.checker(checkerUuid).asInfo().status)
        .isEqualTo(CheckerStatus.ENABLED);

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();
    assertThat(checkerOperations.checker(checkerUuid).asInfo().status)
        .isEqualTo(CheckerStatus.DISABLED);

    checkerOperations.checker(checkerUuid).forUpdate().enable().update();
    assertThat(checkerOperations.checker(checkerUuid).asInfo().status)
        .isEqualTo(CheckerStatus.ENABLED);
  }

  @Test
  public void getCommit() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    RevCommit commit = checkerOperations.checker(checker.uuid).commit();
    assertThat(commit).isEqualTo(readCheckerCommitSha1(checker.uuid));
  }

  private ObjectId readCheckerCommitSha1(String checkerUuid) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjects)) {
      return repo.exactRef(CheckerRef.refsCheckers(checkerUuid)).getObjectId();
    }
  }

  @Test
  public void getConfigText() throws Exception {
    CheckerInfo checker = checkersApi.create(createArbitraryCheckerInput()).get();

    String configText = checkerOperations.checker(checker.uuid).configText();
    assertThat(configText).isEqualTo(readCheckerConfigFile(checker.uuid));
  }

  private String readCheckerConfigFile(String checkerUuid) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjects);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref checkerRef = repo.exactRef(CheckerRef.refsCheckers(checkerUuid));
      RevCommit commit = rw.parseCommit(checkerRef.getObjectId());
      try (TreeWalk tw =
          TreeWalk.forPath(or, CheckerConfig.CHECKER_CONFIG_FILE, commit.getTree())) {
        return new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8);
      }
    }
  }

  @Test
  public void asInfo() throws Exception {
    String checkerUuid =
        checkerOperations
            .newChecker()
            .name("my-checker")
            .description("A description.")
            .url("http://example.com/my-checker")
            .create();
    TestChecker checker = checkerOperations.checker(checkerUuid).get();
    CheckerInfo checkerInfo = checkerOperations.checker(checkerUuid).asInfo();
    assertThat(checkerInfo.uuid).isEqualTo(checker.uuid());
    assertThat(checkerInfo.name).isEqualTo(checker.name());
    assertThat(checkerInfo.description).isEqualTo(checker.description().get());
    assertThat(checkerInfo.url).isEqualTo(checker.url().get());
    assertThat(checkerInfo.createdOn).isEqualTo(checker.createdOn());
    assertThat(checkerInfo.updatedOn).isEqualTo(checker.updatedOn());
  }

  @Test
  public void getCheckersOfRepository() throws Exception {
    String checkerUuid1 = CheckerUuid.make("my-checker1");
    String checkerUuid2 = CheckerUuid.make("my-checker2");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      new TestRepository<>(repo)
          .branch(CheckerRef.REFS_META_CHECKERS)
          .commit()
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(project).getName(),
              Joiner.on('\n').join(checkerUuid1, checkerUuid2))
          .create();
    }

    assertThat(checkerOperations.checkersOf(project)).containsExactly(checkerUuid1, checkerUuid2);
  }

  @Test
  public void getCheckersOfRepositoryWithoutCheckers() throws Exception {
    assertThat(checkerOperations.checkersOf(project)).isEmpty();
  }

  @Test
  public void getCheckersOfNonExistingRepositor() throws Exception {
    assertThat(checkerOperations.checkersOf(new Project.NameKey("non-existing"))).isEmpty();
  }

  @Test
  public void getSha1sOfRepositoriesWithCheckers() throws Exception {
    String checkerUuid1 = CheckerUuid.make("my-checker1");
    String checkerUuid2 = CheckerUuid.make("my-checker2");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      new TestRepository<>(repo)
          .branch(CheckerRef.REFS_META_CHECKERS)
          .commit()
          .add(CheckersByRepositoryNotes.computeRepositorySha1(project).getName(), checkerUuid1)
          .add(CheckersByRepositoryNotes.computeRepositorySha1(allProjects).getName(), checkerUuid2)
          .create();
    }

    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(
            CheckersByRepositoryNotes.computeRepositorySha1(project),
            CheckersByRepositoryNotes.computeRepositorySha1(allProjects));
  }

  private CheckerInput createArbitraryCheckerInput() {
    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = name("test-checker");
    checkerInput.repository = allProjects.get();
    return checkerInput;
  }

  private CheckerInfo getCheckerFromServer(String checkerUuid) throws RestApiException {
    return checkersApi.id(checkerUuid).get();
  }

  private String createCheckerInServer(CheckerInput input) throws RestApiException {
    CheckerInfo checker = checkersApi.create(input).get();
    return checker.uuid;
  }
}
