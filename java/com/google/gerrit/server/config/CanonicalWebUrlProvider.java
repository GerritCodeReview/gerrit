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
  private final String canonicalUrl;

  @Inject
  public CanonicalWebUrlProvider(@GerritServerConfig Config config) {
    String u = config.getString("gerrit", null, "canonicalweburl");
    if (u != null && !u.endsWith("/")) {
      u += "/";
    }
    canonicalUrl = u;
  }

  @Override
  public String get() {
    return canonicalUrl;
  }
}
