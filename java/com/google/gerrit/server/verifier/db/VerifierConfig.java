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

package com.google.gerrit.server.verifier.db;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

/** A representation of a verifier in NoteDb. */
public class VerifierConfig extends VersionedMetaData {
  @VisibleForTesting public static final String VERIFIER_CONFIG_FILE = "verifier.config";

  /**
   * Creates a {@code VerifierConfig} for a new verifier from the {@code VerifierCreation}
   * blueprint. Further, optional properties can be specified by setting an {@code VerifierUpdate}
   * via {@link #setVerifierUpdate(VerifierUpdate)} on the returned {@code VerifierConfig}.
   *
   * <p><strong>Note: </strong>The returned {@code VerifierConfig} has to be committed via {@link
   * #commit(MetaDataUpdate)} in order to create the verifier for real.
   *
   * @param projectName the name of the project which holds the NoteDb commits for verifiers
   * @param repository the repository which holds the NoteDb commits for verifiers
   * @param verifierCreation an {@code VerifierCreation} specifying all properties which are
   *     required for a new verifier
   * @return a {@code VerifierConfig} for a verifier creation
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if a verifier with the same UUID already exists but can't be
   *     read due to an invalid format
   * @throws OrmDuplicateKeyException if a verifier with the same UUID already exists
   */
  public static VerifierConfig createForNewVerifier(
      Project.NameKey projectName, Repository repository, VerifierCreation verifierCreation)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    VerifierConfig verifierConfig = new VerifierConfig(verifierCreation.getVerifierUuid());
    verifierConfig.load(projectName, repository);
    verifierConfig.setVerifierCreation(verifierCreation);
    return verifierConfig;
  }

  /**
   * Creates a {@code VerifierConfig} for an existing verifier.
   *
   * <p>The verifier is automatically loaded within this method and can be accessed via {@link
   * #getLoadedVerifier()}.
   *
   * <p>It's safe to call this method for non-existing verifiers. In that case, {@link
   * #getLoadedVerifier()} won't return any verifier. Thus, the existence of a verifier can be
   * easily tested.
   *
   * <p>The verifier represented by the returned {@code VerifierConfig} can be updated by setting an
   * {@code VerifierUpdate} via {@link #setVerifierUpdate(VerifierUpdate)} and committing the {@code
   * VerifierConfig} via {@link #commit(MetaDataUpdate)}.
   *
   * @param projectName the name of the project which holds the NoteDb commits for verifiers
   * @param repository the repository which holds the NoteDb commits for verifiers
   * @param verifierUuid the UUID of the verifier
   * @return a {@code VerifierConfig} for the verifier with the specified UUID
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if the verifier exists but can't be read due to an invalid
   *     format
   */
  public static VerifierConfig loadForVerifier(
      Project.NameKey projectName, Repository repository, String verifierUuid)
      throws IOException, ConfigInvalidException {
    VerifierConfig verifierConfig = new VerifierConfig(verifierUuid);
    verifierConfig.load(projectName, repository);
    return verifierConfig;
  }

  private final String verifierUuid;
  private final String ref;

  private Optional<Verifier> loadedVerifier = Optional.empty();
  private Optional<VerifierCreation> verifierCreation = Optional.empty();
  private Optional<VerifierUpdate> verifierUpdate = Optional.empty();
  private boolean isLoaded = false;

  private VerifierConfig(String verifierUuid) {
    this.verifierUuid = requireNonNull(verifierUuid);
    this.ref = RefNames.refsVerifiers(verifierUuid);
  }

  /**
   * Returns the verifier loaded from NoteDb.
   *
   * <p>If not any NoteDb commits exist for the verifier represented by this {@code VerifierConfig},
   * no verifier is returned.
   *
   * <p>After {@link #commit(MetaDataUpdate)} was called on this {@code VerifierConfig}, this method
   * returns a verifier which is in line with the latest NoteDb commit for this verifier. So, after
   * creating a {@code VerifierConfig} for a new verifier and committing it, this method can be used
   * to retrieve a representation of the created verifier. The same holds for the representation of
   * an updated verifier.
   *
   * @return the loaded verifier, or an empty {@code Optional} if the verifier doesn't exist
   */
  public Optional<Verifier> getLoadedVerifier() {
    checkLoaded();
    return loadedVerifier;
  }

  /**
   * Specifies how the current verifier should be updated.
   *
   * <p>If the verifier is newly created, the {@code VerifierUpdate} can be used to specify optional
   * properties.
   *
   * <p><strong>Note: </strong>This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code VerifierConfig}.
   *
   * @param verifierUpdate an {@code VerifierUpdate} outlining the modifications which should be
   *     applied
   */
  public void setVerifierUpdate(VerifierUpdate verifierUpdate) {
    this.verifierUpdate = Optional.of(verifierUpdate);
  }

  private void setVerifierCreation(VerifierCreation verifierCreation)
      throws OrmDuplicateKeyException {
    checkLoaded();
    if (loadedVerifier.isPresent()) {
      throw new OrmDuplicateKeyException(String.format("Verifier %s already exists", verifierUuid));
    }

    this.verifierCreation = Optional.of(verifierCreation);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      rw.reset();
      rw.markStart(revision);
      rw.sort(RevSort.REVERSE);
      RevCommit earliestCommit = rw.next();
      Timestamp createdOn = new Timestamp(earliestCommit.getCommitTime() * 1000L);

      Config config = readConfig(VERIFIER_CONFIG_FILE);
      loadedVerifier =
          Optional.of(createFrom(verifierUuid, config, createdOn, revision.toObjectId()));
    }

    isLoaded = true;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();
    if (!verifierCreation.isPresent() && !verifierUpdate.isPresent()) {
      // Verifier was neither created nor changed. -> A new commit isn't necessary.
      return false;
    }

    // Commit timestamps are internally truncated to seconds. To return the correct 'createdOn' time
    // for new verifiers, we explicitly need to truncate the timestamp here.
    Timestamp commitTimestamp =
        TimeUtil.truncateToSecond(
            verifierUpdate.flatMap(VerifierUpdate::getUpdatedOn).orElseGet(TimeUtil::nowTs));
    commit.setAuthor(new PersonIdent(commit.getAuthor(), commitTimestamp));
    commit.setCommitter(new PersonIdent(commit.getCommitter(), commitTimestamp));

    Verifier updatedVerifier = updateVerifier(commitTimestamp);

    String commitMessage = createCommitMessage(loadedVerifier, updatedVerifier);
    commit.setMessage(commitMessage);

    loadedVerifier = Optional.of(updatedVerifier);
    verifierCreation = Optional.empty();
    verifierUpdate = Optional.empty();

    return true;
  }

  private void checkLoaded() {
    checkState(isLoaded, "Verifier %s not loaded yet", verifierUuid);
  }

  private Verifier updateVerifier(Timestamp commitTimestamp)
      throws IOException, ConfigInvalidException {
    Config config = updateGroupProperties();

    Timestamp createdOn = loadedVerifier.map(Verifier::getCreatedOn).orElse(commitTimestamp);

    return createFrom(verifierUuid, config, createdOn, null);
  }

  private Config updateGroupProperties() throws IOException, ConfigInvalidException {
    Config config = readConfig(VERIFIER_CONFIG_FILE);
    verifierCreation.ifPresent(
        internalGroupCreation ->
            Arrays.stream(VerifierConfigEntry.values())
                .forEach(configEntry -> configEntry.initNewConfig(config, internalGroupCreation)));
    verifierUpdate.ifPresent(
        internalGroupUpdate ->
            Arrays.stream(VerifierConfigEntry.values())
                .forEach(
                    configEntry -> configEntry.updateConfigValue(config, internalGroupUpdate)));
    saveConfig(VERIFIER_CONFIG_FILE, config);
    return config;
  }

  private static Verifier createFrom(
      String verifierUuid, Config config, Timestamp createdOn, @Nullable ObjectId refState)
      throws ConfigInvalidException {
    Verifier.Builder verifier = Verifier.builder(verifierUuid);
    for (VerifierConfigEntry configEntry : VerifierConfigEntry.values()) {
      configEntry.readFromConfig(verifierUuid, verifier, config);
    }
    verifier.setCreatedOn(createdOn);
    if (refState != null) {
      verifier.setRefState(refState);
    }
    return verifier.build();
  }

  private String createCommitMessage(
      Optional<Verifier> originalVerifier, Verifier updatedVerifier) {
    String summaryLine = originalVerifier.isPresent() ? "Update verifier" : "Create verifier";
    Optional<String> footerForRename = getFooterForRename(originalVerifier, updatedVerifier);
    if (footerForRename.isPresent()) {
      return summaryLine + "\n\n" + footerForRename;
    }
    return summaryLine;
  }

  private Optional<String> getFooterForRename(
      Optional<Verifier> originalVerifier, Verifier updatedVerifier) {
    if (!originalVerifier.isPresent()) {
      return Optional.empty();
    }

    String originalName = originalVerifier.get().getName();
    String newName = updatedVerifier.getName();
    if (originalName.equals(newName)) {
      return Optional.empty();
    }
    return Optional.of("Rename from " + originalName + " to " + newName);
  }
}
