// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.api.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

public class GerritServerIdProvider implements Provider<String> {
  public static final String SECTION = "gerrit";
  public static final String KEY = "serverId";

  public static String generate() {
    return UUID.randomUUID().toString();
  }

  private final String id;

  @Inject
  GerritServerIdProvider(@GerritServerConfig Config cfg,
      SitePaths sitePaths) throws IOException, ConfigInvalidException {
    String origId = cfg.getString(SECTION, null, KEY);
    if (!Strings.isNullOrEmpty(origId)) {
      id = origId;
      return;
    }

    // We're not generally supposed to do work in provider constructors, but
    // this is a bit of a special case because we really need to have the ID
    // available by the time the dbInjector is created. This even applies during
    // RebuildNoteDb, which otherwise would have been a reasonable place to do
    // the ID generation. Fortunately, it's not much work, and it happens once.
    id = generate();
    Config cfgCopy = new Config();
    cfgCopy.fromText(cfg.toText());
    cfg.setString(SECTION, null, KEY, id);
    Files.write(sitePaths.gerrit_config, cfg.toText().getBytes(UTF_8));
  }

  @Override
  public String get() {
    return id;
  }
}
