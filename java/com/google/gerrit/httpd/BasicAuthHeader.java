// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static com.google.gerrit.common.CharsetUtil.forNameOrUtf8;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.Nullable;
import java.io.IOException;
import java.util.Optional;

final class BasicAuthHeader {
  static final String PREFIX = "Basic ";

  private BasicAuthHeader() {}

  static Optional<Credentials> parse(@Nullable String header, @Nullable String encoding)
      throws IOException {
    if (header == null || !header.startsWith(PREFIX)) {
      return Optional.empty();
    }

    byte[] decoded = BaseEncoding.base64().decode(header.substring(PREFIX.length()));

    String usernamePassword = new String(decoded, forNameOrUtf8(encoding));
    int splitPos = usernamePassword.indexOf(':');
    if (splitPos < 1 || splitPos == usernamePassword.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(
        new Credentials(
            usernamePassword.substring(0, splitPos), usernamePassword.substring(splitPos + 1)));
  }

  record Credentials(String username, String password) {}
}
