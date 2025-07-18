// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@AutoValue
public abstract class AuthToken {
  private static final Pattern TOKEN_ID_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9-_]*$");

  public static AuthToken createWithPlainToken(@Nullable String id, String plainToken)
      throws InvalidAuthTokenException {
    return createWithPlainToken(id, plainToken, Optional.empty());
  }

  public static AuthToken createWithPlainToken(
      @Nullable String id, String plainToken, Optional<Instant> expirationDate)
      throws InvalidAuthTokenException {
    return create(id, HashedPassword.fromPassword(plainToken).encode(), expirationDate);
  }

  public static AuthToken create(@Nullable String id, String hashedToken)
      throws InvalidAuthTokenException {
    return create(id, hashedToken, Optional.empty());
  }

  public static AuthToken create(
      @Nullable String id, String hashedToken, Optional<Instant> expirationDate)
      throws InvalidAuthTokenException {
    if (Strings.isNullOrEmpty(id)) {
      id = "token_" + System.currentTimeMillis();
    } else {
      validateId(id);
    }
    return new AutoValue_AuthToken(id, hashedToken, expirationDate);
  }

  public abstract String id();

  public abstract String hashedToken();

  public abstract Optional<Instant> expirationDate();

  public boolean isExpired() {
    return expirationDate().isPresent() && Instant.now().isAfter(expirationDate().get());
  }

  private static void validateId(String id) throws InvalidAuthTokenException {
    if (!TOKEN_ID_PATTERN.matcher(id).matches()) {
      throw new InvalidAuthTokenException(
          "Token ID must contain only letters, numbers, hyphens and underscores.");
    }
  }
}
