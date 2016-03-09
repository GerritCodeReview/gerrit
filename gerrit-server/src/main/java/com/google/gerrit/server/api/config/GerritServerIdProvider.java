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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.CharMatcher;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

public class GerritServerIdProvider implements Provider<String> {
  private final String id;

  @Inject
  GerritServerIdProvider(SitePaths sitePaths) throws IOException {
    Path p = sitePaths.etc_dir.resolve("uuid");
    byte[] b;
    try {
      b = Files.readAllBytes(p);
    } catch (NoSuchFileException e) {
      b = null;
    }
    if (b != null) {
      id = CharMatcher.whitespace().trimFrom(new String(b, UTF_8));
      checkArgument(!id.contains(">") && !id.contains("<"),
          "invalid server UUID: %s", id);
    } else {
      id = generateAndSaveUuid(p);
    }
  }

  private static String generateAndSaveUuid(Path p) throws IOException {
    String id = UUID.randomUUID().toString();
    Files.write(p, id.getBytes(UTF_8));
    return id;
  }

  @Override
  public String get() {
    return id;
  }
}
