// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadUrl;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

/** Download protocol from {@code gerrit.config}. */
@Singleton
public class DownloadUrlConfig {
  private final DownloadUrl dowloadUrl;

  @Inject
  DownloadUrlConfig(@GerritServerConfig final Config cfg, final SystemConfig s)
      throws XsrfException {
    dowloadUrl = toType(cfg);
  }

  private static DownloadUrl toType(final Config cfg) {
    DownloadUrl downloadUrl = null;

    try {
      downloadUrl = ConfigUtil.getEnum(cfg, "download", null, "url", DownloadUrl.SSH_HTTP_ANON_HTTP);
    } catch (IllegalArgumentException e) {
    }

    if (downloadUrl == null) {
      downloadUrl = DownloadUrl.SSH_HTTP_ANON_HTTP;
    }

    return downloadUrl;
  }

  /** Protocol used to download. */
  public DownloadUrl getDownloadUrl() {
    return dowloadUrl;
  }
}
