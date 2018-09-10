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

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

/** Provides {@link CanonicalWebUrl} from {@code gerrit.canonicalWebUrl}. */
public class CanonicalWebUrlProvider implements Provider<String> {

  /**
   * An invalid URL for this gerrit server. This lets the rest of the code avoid having to handle
   * the null case.
   */
  public static final String PLACEHOLDER_URL = "https://gerrit.invalid/";

  private final String canonicalUrl;

  @Inject
  public CanonicalWebUrlProvider(@GerritServerConfig Config config) {
    String u = config.getString("gerrit", null, "canonicalweburl");
    if (u != null && !u.endsWith("/")) {
      u += "/";
    }
    if (u == null) {
      u = PLACEHOLDER_URL;
    }

    canonicalUrl = u;
  }

  /** Returns the a canonical URL. This is never null. */
  @Override
  public String get() {
    return canonicalUrl;
  }
}
