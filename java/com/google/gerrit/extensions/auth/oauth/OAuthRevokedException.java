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

package com.google.gerrit.extensions.auth.oauth;

import java.io.IOException;

/**
 * Signals that the identity provider reported the grant as gone -- the OAuth 2.0 {@code
 * invalid_grant} error on a {@link OAuthServiceProvider#refresh refresh} (consent withdrawn,
 * account disabled, "sign out everywhere").
 *
 * <p>Extends {@link IOException} so it flows through the {@code refresh} signature, and so a caller
 * can distinguish revocation (end the session) from a transient IdP/network failure (a plain {@link
 * IOException}, handled by policy).
 */
public class OAuthRevokedException extends IOException {
  private static final long serialVersionUID = 1L;

  public OAuthRevokedException(String message) {
    super(message);
  }
}
