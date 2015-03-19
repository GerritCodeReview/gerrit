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

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.archive.TarFormat;
import org.eclipse.jgit.archive.Tbz2Format;
import org.eclipse.jgit.archive.TgzFormat;
import org.eclipse.jgit.archive.TxzFormat;
import org.eclipse.jgit.archive.ZipFormat;

public enum ArchiveFormat {
  TGZ("application/x-gzip", new TgzFormat()),
  TAR("application/x-tar", new TarFormat()),
  TBZ2("application/x-bzip2", new Tbz2Format()),
  TXZ("application/x-xz", new TxzFormat()),
  ZIP("application/x-zip", new ZipFormat());

  private final ArchiveCommand.Format<?> format;
  private final String mimeType;

  private ArchiveFormat(String mimeType, ArchiveCommand.Format<?> format) {
    this.format = format;
    this.mimeType = mimeType;
    ArchiveCommand.registerFormat(name(), format);
  }

  public String getShortName() {
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
}
