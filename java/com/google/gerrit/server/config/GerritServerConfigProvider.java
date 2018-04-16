// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static java.util.stream.Collectors.joining;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides {@link Config} annotated with {@link GerritServerConfig}.
 *
 * <p>To react on config updates, the caller should implement @see GerritConfigListener.
 *
 * <p>The few callers that need a reloaded-on-demand config can inject a {@code
 * GerritServerConfigProvider} and request the lastest config with fetchLatestConfig().
 */
@Singleton
public class GerritServerConfigProvider implements Provider<Config> {
  private static final Logger log = LoggerFactory.getLogger(GerritServerConfigProvider.class);

  private final SitePaths site;
  private final SecureStore secureStore;

  private final Object lock = new Object();

  @Nullable private GerritConfig gerritConfig;

  @Inject
  GerritServerConfigProvider(SitePaths site, SecureStore secureStore) {
    this.site = site;
    this.secureStore = secureStore;
  }

  @Override
  public Config get() {
    synchronized (lock) {
      if (gerritConfig == null) {
        gerritConfig = loadConfig();
      }
    }
    return gerritConfig;
  }

  protected Config updateConfig() {
    synchronized (lock) {
      gerritConfig = loadConfig();
      return gerritConfig;
    }
  }

  public GerritConfig loadConfig() {
    FileBasedConfig baseConfig = loadConfig(null, site.gerrit_config);
    if (!baseConfig.getFile().exists()) {
      log.info("No " + site.gerrit_config.toAbsolutePath() + "; assuming defaults");
    }

    FileBasedConfig noteDbConfigOverBaseConfig = loadConfig(baseConfig, site.notedb_config);
    checkNoteDbConfig(noteDbConfigOverBaseConfig);

    return new GerritConfig(noteDbConfigOverBaseConfig, baseConfig, secureStore);
  }

  private static FileBasedConfig loadConfig(@Nullable Config base, Path path) {
    FileBasedConfig cfg = new FileBasedConfig(base, path.toFile(), FS.DETECTED);
    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      throw new ProvisionException(e.getMessage(), e);
    }
    return cfg;
  }

  private static void checkNoteDbConfig(FileBasedConfig noteDbConfig) {
    List<String> bad = new ArrayList<>();
    for (String section : noteDbConfig.getSections()) {
      if (section.equals(NotesMigration.SECTION_NOTE_DB)) {
        continue;
      }
      for (String subsection : noteDbConfig.getSubsections(section)) {
        noteDbConfig
            .getNames(section, subsection, false)
            .forEach(n -> bad.add(section + "." + subsection + "." + n));
      }
      noteDbConfig.getNames(section, false).forEach(n -> bad.add(section + "." + n));
    }
    if (!bad.isEmpty()) {
      throw new ProvisionException(
          "Non-NoteDb config options not allowed in "
              + noteDbConfig.getFile()
              + ":\n"
              + bad.stream().collect(joining("\n")));
    }
  }
}
