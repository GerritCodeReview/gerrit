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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.sshd.CommandMetaData;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Shows metadata for the caller's OAuth token (provider, expiry, type) without the token value. */
@CommandMetaData(
    name = "show",
    description = "Show the caller's OAuth token metadata (not the token value)")
final class OAuthTokenShowCommand extends OAuthTokenSubcommand {
  @Override
  protected void run() throws Failure {
    OAuthToken token = currentToken();
    long expiresAt = token.getExpiresAt();
    stdout.print("provider_id=" + token.getProviderId() + "\n");
    stdout.print("expires_at=" + expiresAt + " (" + humanExpiry(expiresAt) + ")\n");
    stdout.print("type=bearer\n");
  }

  /** Renders the epoch-millis expiry as an ISO-8601 UTC instant (seconds precision). */
  private static String humanExpiry(long expiresAtMillis) {
    if (expiresAtMillis == Long.MAX_VALUE) {
      return "no expiry";
    }
    if (expiresAtMillis <= 0) {
      return "unknown";
    }
    return Instant.ofEpochMilli(expiresAtMillis).truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
