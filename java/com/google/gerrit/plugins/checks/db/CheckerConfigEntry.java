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

package com.google.gerrit.plugins.checks.db;

import com.google.common.base.Strings;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerCreation;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/**
 * A basic property of a checker.
 *
 * <p>Each property knows how to read and write its value from/to a JGit {@link Config} file.
 */
enum CheckerConfigEntry {
  /**
   * The name of a checker. This property is equivalent to {@link Checker#getName()}.
   *
   * <p>This is a mandatory property.
   */
  NAME("name") {
    @Override
    void readFromConfig(String checkerUuid, Checker.Builder checker, Config config)
        throws ConfigInvalidException {
      String name = config.getString(SECTION_NAME, null, super.keyName);
      // An empty name is invalid in NoteDb; CheckerConfig will refuse to store it
      if (name == null) {
        throw new ConfigInvalidException(String.format("name of checker %s not set", checkerUuid));
      }
      checker.setName(name);
    }

    @Override
    void initNewConfig(Config config, CheckerCreation checkerCreation) {
      String name = checkerCreation.getName();
      config.setString(SECTION_NAME, null, super.keyName, name);
    }

    @Override
    void updateConfigValue(Config config, CheckerUpdate checkerUpdate) {
      checkerUpdate
          .getName()
          .ifPresent(name -> config.setString(SECTION_NAME, null, super.keyName, name));
    }
  },

  /**
   * The description of a checker. This property is equivalent to {@link Checker#getDescription()}.
   *
   * <p>It defaults to {@code null} if not set.
   */
  DESCRIPTION("description") {
    @Override
    void readFromConfig(String checkerUuid, Checker.Builder checker, Config config) {
      String description = config.getString(SECTION_NAME, null, super.keyName);
      if (!Strings.isNullOrEmpty(description)) {
        checker.setDescription(description);
      }
    }

    @Override
    void initNewConfig(Config config, CheckerCreation checkerCreation) {
      // Do nothing. Description key will be set by updateConfigValue.
    }

    @Override
    void updateConfigValue(Config config, CheckerUpdate checkerUpdate) {
      checkerUpdate
          .getDescription()
          .ifPresent(
              description -> {
                if (Strings.isNullOrEmpty(description)) {
                  config.unset(SECTION_NAME, null, super.keyName);
                } else {
                  config.setString(SECTION_NAME, null, super.keyName, description);
                }
              });
    }
  },

  /**
   * The URL of a checker. This property is equivalent to {@link Checker#getUrl()}.
   *
   * <p>It defaults to {@code null} if not set.
   */
  URL("url") {
    @Override
    void readFromConfig(String checkerUuid, Checker.Builder checker, Config config) {
      String url = config.getString(SECTION_NAME, null, super.keyName);
      if (!Strings.isNullOrEmpty(url)) {
        checker.setUrl(url);
      }
    }

    @Override
    void initNewConfig(Config config, CheckerCreation checkerCreation) {
      // Do nothing. URL key will be set by updateConfigValue.
    }

    @Override
    void updateConfigValue(Config config, CheckerUpdate checkerUpdate) {
      checkerUpdate
          .getUrl()
          .ifPresent(
              url -> {
                if (Strings.isNullOrEmpty(url)) {
                  config.unset(SECTION_NAME, null, super.keyName);
                } else {
                  config.setString(SECTION_NAME, null, super.keyName, url);
                }
              });
    }
  },

  /**
   * The repository for which the checker applies. This property is equivalent to {@link
   * Checker#getRepository()}.
   *
   * <p>This is a mandatory property.
   */
  REPOSITORY("repository") {
    @Override
    void readFromConfig(String checkerUuid, Checker.Builder checker, Config config)
        throws ConfigInvalidException {
      String repository = config.getString(SECTION_NAME, null, super.keyName);
      // An empty repository is invalid in NoteDb; CheckerConfig will refuse to store it
      if (repository == null) {
        throw new ConfigInvalidException(
            String.format("repository of checker %s not set", checkerUuid));
      }
      checker.setRepository(new Project.NameKey(repository));
    }

    @Override
    void initNewConfig(Config config, CheckerCreation checkerCreation) {
      String repository = checkerCreation.getRepository().get();
      config.setString(SECTION_NAME, null, super.keyName, repository);
    }

    @Override
    void updateConfigValue(Config config, CheckerUpdate checkerUpdate) {
      checkerUpdate
          .getRepository()
          .ifPresent(
              repository -> config.setString(SECTION_NAME, null, super.keyName, repository.get()));
    }
  },

  STATUS("status") {
    @Override
    void readFromConfig(String checkerUuid, Checker.Builder checker, Config config)
        throws ConfigInvalidException {
      String value = config.getString(SECTION_NAME, null, super.keyName);
      if (value == null) {
        throw new ConfigInvalidException(
            String.format("status of checker %s not set", checkerUuid));
      }
      checker.setStatus(config.getEnum(SECTION_NAME, null, super.keyName, CheckerStatus.ENABLED));
    }

    @Override
    void initNewConfig(Config config, CheckerCreation checkerCreation) {
      // New checkers default to enabled.
      config.setEnum(SECTION_NAME, null, super.keyName, CheckerStatus.ENABLED);
    }

    @Override
    void updateConfigValue(Config config, CheckerUpdate checkerUpdate) {
      checkerUpdate
          .getStatus()
          .ifPresent(status -> config.setEnum(SECTION_NAME, null, super.keyName, status));
    }
  };

  private static final String SECTION_NAME = "checker";

  private final String keyName;

  CheckerConfigEntry(String keyName) {
    this.keyName = keyName;
  }

  /**
   * Reads the corresponding property of this {@code CheckerConfigEntry} from the given {@code
   * Config}. The read value is written to the corresponding property of {@code Checker.Builder}.
   *
   * @param checkerUuid the UUID of the checker (necessary for helpful error messages)
   * @param checker the {@code Checker.Builder} whose property value should be set
   * @param config the {@code Config} from which the value of the property should be read
   * @throws ConfigInvalidException if the property has an unexpected value
   */
  abstract void readFromConfig(String checkerUuid, Checker.Builder checker, Config config)
      throws ConfigInvalidException;

  /**
   * Initializes the corresponding property of this {@code CheckerConfigEntry} in the given {@code
   * Config}.
   *
   * <p>If the specified {@code CheckerCreation} has an entry for the property, that value is used.
   * If not, the default value for the property is set. In any case, an existing entry for the
   * property in the {@code Config} will be overwritten.
   *
   * @param config a new {@code Config}, typically without an entry for the property
   * @param checkerCreation an {@code CheckerCreation} detailing the initial value of mandatory
   *     checker properties
   */
  abstract void initNewConfig(Config config, CheckerCreation checkerCreation);

  /**
   * Updates the corresponding property of this {@code CheckerConfigEntry} in the given {@code
   * Config} if the {@code CheckerUpdate} mentions a modification.
   *
   * <p>This call is a no-op if the {@code CheckerUpdate} doesn't contain a modification for the
   * property.
   *
   * @param config a {@code Config} for which the property should be updated
   * @param checkerUpdate an {@code CheckerUpdate} detailing the modifications on a checker
   */
  abstract void updateConfigValue(Config config, CheckerUpdate checkerUpdate);
}
