// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Encrypts and decrypts an {@link OAuthToken} for storage at rest.
 *
 * <p>Implementations must encrypt only the secret fields ({@code token}, {@code secret}, {@code
 * raw}) and leave {@code expiresAt} and {@code providerId} in cleartext: they are non-secret
 * metadata (an expiry timestamp and a provider routing id) that Gerrit reads <em>without</em>
 * decrypting -- for instance to decide, on the refresh-on-read path, whether a cached token has
 * expired.
 */
@ExtensionPoint
public interface OAuthTokenEncrypter {

  /**
   * Encrypts the secret parts of the given OAuth access token.
   *
   * @param unencrypted a raw OAuth access token.
   */
  OAuthToken encrypt(OAuthToken unencrypted);

  /**
   * Decrypts the secret parts of the given OAuth access token.
   *
   * @param encrypted an encrypted OAuth access token.
   */
  OAuthToken decrypt(OAuthToken encrypted);
}
