// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ExternalIdKeyFactory {
  private final boolean isUserNameCaseInsensitive;

  @Inject
  public ExternalIdKeyFactory(AuthConfig authConfig) {
    this.isUserNameCaseInsensitive = authConfig.isUserNameCaseInsensitive();
  }

  /**
   * Creates an external ID key.
   *
   * @param scheme the scheme name, must not contain colons (':')
   * @param id the external ID, must not contain colons (':')
   * @return the created external ID key
   */
  public ExternalId.Key create(@Nullable String scheme, String id) {
    if (scheme != null
        && (scheme.equals(ExternalId.SCHEME_USERNAME) || scheme.equals(ExternalId.SCHEME_GERRIT))) {
      return ExternalId.Key.create(scheme, id, isUserNameCaseInsensitive);
    }

    return ExternalId.Key.create(scheme, id, false);
  }

  /**
   * Parses an external ID key from its String representation
   *
   * @param externalId String representation of external ID key (e.g. username:johndoe)
   * @return the external Id key object
   */
  public ExternalId.Key parse(String externalId) {
    int c = externalId.indexOf(':');
    if (c < 1 || c >= externalId.length() - 1) {
      return create(null, externalId);
    }
    return create(externalId.substring(0, c), externalId.substring(c + 1));
  }
}
