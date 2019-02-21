// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.BadRequestException;
import java.net.URI;
import java.net.URISyntaxException;

public class CheckerUrl {
  /**
   * Cleans a user-provided URL.
   *
   * @param urlString URL string. Must be either empty (after trimming), or a valid http/https URL.
   * @return input string after trimming, guaranteed to be either the empty string or a valid
   *     http/https URL.
   * @throws BadRequestException if the input is neither empty (after trimming) nor a valid URL.
   */
  public static String clean(String urlString) throws BadRequestException {
    String trimmed = requireNonNull(urlString).trim();
    if (trimmed.isEmpty()) {
      return trimmed;
    }
    URI uri;
    try {
      uri = new URI(trimmed);
    } catch (URISyntaxException e) {
      uri = null;
    }
    if (uri == null || Strings.isNullOrEmpty(uri.getScheme())) {
      throw new BadRequestException("invalid URL: " + urlString);
    }
    if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https")) {
      throw new BadRequestException("only http/https URLs supported: " + urlString);
    }
    return trimmed;
  }

  private CheckerUrl() {}
}
