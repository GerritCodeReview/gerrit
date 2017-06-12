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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
public class AllowedFormats {
  final ImmutableMap<String, ArchiveFormat> extensions;
  final ImmutableSet<ArchiveFormat> allowed;

  @Inject
  AllowedFormats(DownloadConfig cfg) {
    Map<String, ArchiveFormat> exts = new HashMap<>();
    for (ArchiveFormat format : cfg.getArchiveFormats()) {
      for (String ext : format.getSuffixes()) {
        exts.put(ext, format);
      }
      exts.put(format.name().toLowerCase(), format);
    }
    extensions = ImmutableMap.copyOf(exts);

    // Zip is not supported because it may be interpreted by a Java plugin as a
    // valid JAR file, whose code would have access to cookies on the domain.
    allowed =
        Sets.immutableEnumSet(
            Iterables.filter(cfg.getArchiveFormats(), f -> f != ArchiveFormat.ZIP));
  }

  public Set<ArchiveFormat> getAllowed() {
    return allowed;
  }

  public ImmutableMap<String, ArchiveFormat> getExtensions() {
    return extensions;
  }
}
