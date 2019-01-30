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

import com.google.common.base.Strings;
import com.google.gerrit.server.verifier.Verifier;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * A basic property of a verifier.
 *
 * <p>Each property knows how to read and write its value from/to a JGit {@link Config} file.
 *
 * <p><strong>Warning: </strong>This class is a low-level API for properties of verifiers in NoteDb.
 * It may only be used by {@link VerifierConfig}. Other classes should use {@link VerifierUpdate} to
 * modify the properties of a verifier.
 */
enum VerifierConfigEntry {
  /**
   * The name of a verifier. This property is equivalent to {@link Verifier#getName()}.
   *
   * <p>This is a mandatory property.
   */
  NAME("name") {
    @Override
    void readFromConfig(String verifierUuid, Verifier.Builder verifier, Config config)
        throws ConfigInvalidException {
      String name = config.getString(SECTION_NAME, null, super.keyName);
      // An empty name is invalid in NoteDb; VerifierConfig will refuse to store it
      if (name == null) {
        throw new ConfigInvalidException(
            String.format("name of verifier %s not set", verifierUuid));
      }
      verifier.setName(name);
    }

    @Override
    void initNewConfig(Config config, VerifierCreation verifierCreation) {
      String name = verifierCreation.getName();
      config.setString(SECTION_NAME, null, super.keyName, name);
    }

    @Override
    void updateConfigValue(Config config, VerifierUpdate verifierUpdate) {
      verifierUpdate
          .getName()
          .ifPresent(name -> config.setString(SECTION_NAME, null, super.keyName, name));
    }
  },
  /**
   * The description of a verifier. This property is equivalent to {@link
   * Verifier#getDescription()}.
   *
   * <p>It defaults to {@code null} if not set.
   */
  DESCRIPTION("description") {
    @Override
    void readFromConfig(String verifierUuid, Verifier.Builder verifier, Config config) {
      String description = config.getString(SECTION_NAME, null, super.keyName);
      if (!Strings.isNullOrEmpty(description)) {
        verifier.setDescription(description);
      }
    }

    @Override
    void initNewConfig(Config config, VerifierCreation verifierCreation) {
      config.setString(SECTION_NAME, null, super.keyName, null);
    }

    @Override
    void updateConfigValue(Config config, VerifierUpdate verifierUpdate) {
      verifierUpdate
          .getDescription()
          .ifPresent(
              description ->
                  config.setString(
                      SECTION_NAME, null, super.keyName, Strings.emptyToNull(description)));
    }
  };

  private static final String SECTION_NAME = "verifier";

  private final String keyName;

  VerifierConfigEntry(String keyName) {
    this.keyName = keyName;
  }

  /**
   * Reads the corresponding property of this {@code VerifierConfigEntry} from the given {@code
   * Config}. The read value is written to the corresponding property of {@code Verifier.Builder}.
   *
   * @param verifierUuid the UUID of the verifier (necessary for helpful error messages)
   * @param verifier the {@code Verifier.Builder} whose property value should be set
   * @param config the {@code Config} from which the value of the property should be read
   * @throws ConfigInvalidException if the property has an unexpected value
   */
  abstract void readFromConfig(String verifierUuid, Verifier.Builder verifier, Config config)
      throws ConfigInvalidException;

  /**
   * Initializes the corresponding property of this {@code VerifierConfigEntry} in the given {@code
   * Config}.
   *
   * <p>If the specified {@code VerifierCreation} has an entry for the property, that value is used.
   * If not, the default value for the property is set. In any case, an existing entry for the
   * property in the {@code Config} will be overwritten.
   *
   * @param config a new {@code Config}, typically without an entry for the property
   * @param verifierCreation an {@code VerifierCreation} detailing the initial value of mandatory
   *     verifier properties
   */
  abstract void initNewConfig(Config config, VerifierCreation verifierCreation);

  /**
   * Updates the corresponding property of this {@code VerifierConfigEntry} in the given {@code
   * Config} if the {@code VerifierUpdate} mentions a modification.
   *
   * <p>This call is a no-op if the {@code VerifierUpdate} doesn't contain a modification for the
   * property.
   *
   * @param config a {@code Config} for which the property should be updated
   * @param verifierUpdate an {@code VerifierUpdate} detailing the modifications on a verifier
   */
  abstract void updateConfigValue(Config config, VerifierUpdate verifierUpdate);
}
