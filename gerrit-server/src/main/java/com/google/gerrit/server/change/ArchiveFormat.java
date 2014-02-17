// Copyright 2013 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.change;

import com.google.common.collect.Maps;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.archive.TarFormat;
import org.eclipse.jgit.archive.Tbz2Format;
import org.eclipse.jgit.archive.TgzFormat;
import org.eclipse.jgit.archive.TxzFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

enum ArchiveFormat {
  TGZ("application/x-gzip", new TgzFormat()),
  TAR("application/x-tar", new TarFormat()),
  TBZ2("application/x-bzip2", new Tbz2Format()),
  TXZ("application/x-xz", new TxzFormat());
  // Zip is not supported because it may be interpreted by a Java plugin as a
  // valid JAR file, whose code would have access to cookies on the domain.

  static final Logger log = LoggerFactory.getLogger(ArchiveFormat.class);

  private final ArchiveCommand.Format<?> format;
  private final String mimeType;

  private ArchiveFormat(String mimeType, ArchiveCommand.Format<?> format) {
    this.format = format;
    this.mimeType = mimeType;
    ArchiveCommand.registerFormat(name(), format);
  }

  String getShortName() {
    return name().toLowerCase();
  }

  String getMimeType() {
    return mimeType;
  }

  String getDefaultSuffix() {
    return getSuffixes().iterator().next();
  }

  Iterable<String> getSuffixes() {
    return format.suffixes();
  }

  static Map<String, ArchiveFormat> init() {
    String[] formats = new String[values().length];
    for (int i = 0; i < values().length; i++) {
      formats[i] = values()[i].name();
    }

    Map<String, ArchiveFormat> exts = Maps.newLinkedHashMap();
    for (String name : formats) {
      try {
        ArchiveFormat format = valueOf(name.toUpperCase());
        for (String ext : format.getSuffixes()) {
          exts.put(ext, format);
        }
      } catch (IllegalArgumentException e) {
        log.warn("Invalid archive.format {}", name);
      }
    }
    return Collections.unmodifiableMap(exts);
  }
}
