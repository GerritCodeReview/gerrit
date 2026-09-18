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

package com.google.gerrit.httpd.auth.openid;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import com.google.inject.servlet.SessionScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Per-session token that binds an OpenID {@code LINK_IDENTIY} callback to the session that
 * initiated the link, mirroring the {@code state} check {@link OAuthSessionOverOpenID} applies to
 * OAuth.
 */
@SessionScoped
class OpenIdLinkState {
  private static final SecureRandom randomState = newRandomGenerator();

  private final String state;

  @Inject
  OpenIdLinkState() {
    this.state = generateRandomState();
  }

  /** Token to embed in the signed {@code return_to} when initiating a link operation. */
  String get() {
    return state;
  }

  /** True if {@code candidate} matches this session's token, compared in constant time. */
  boolean check(String candidate) {
    if (Strings.isNullOrEmpty(candidate)) {
      return false;
    }
    return MessageDigest.isEqual(
        state.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
  }

  private static SecureRandom newRandomGenerator() {
    try {
      return SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No SecureRandom available for OpenID link state", e);
    }
  }

  private static String generateRandomState() {
    // 256 bits (32 bytes) of entropy: an unguessable anti-forgery state nonce.
    byte[] buf = new byte[32];
    randomState.nextBytes(buf);
    return BaseEncoding.base64Url().encode(buf);
  }
}
