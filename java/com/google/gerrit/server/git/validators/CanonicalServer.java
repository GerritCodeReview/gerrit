// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.common.base.CharMatcher;
import com.google.gerrit.common.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import org.eclipse.jgit.util.SystemReader;

/** The currently running Gerrit server. */
public class CanonicalServer {

  private final Optional<String> canonicalWebUrl;

  public CanonicalServer(@Nullable String canonicalWebUrl) {
    this.canonicalWebUrl = Optional.ofNullable(canonicalWebUrl);
  }

  /**
   * Returns the URL of this server.
   *
   * @return the canonical URL (with any trailing slash removed) if it is configured, otherwise
   *     falls back to "http://hostname" where hostname is the value returned by {@link #getName()}
   */
  public String getUrl() {
    return canonicalWebUrl
        .map(CharMatcher.is('/')::trimTrailingFrom)
        .orElseGet(() -> "http://" + getName());
  }

  /**
   * Returns the name of this server.
   *
   * @return the name of the host from the canonical URL if it is configured, otherwise whatever the
   *     OS says the name of this server is
   */
  public String getName() {
    return canonicalWebUrl
        .flatMap(CanonicalServer::toUrl)
        .map(URL::getHost)
        .orElseGet(SystemReader.getInstance()::getHostname);
  }

  private static Optional<URL> toUrl(String url) {
    try {
      return Optional.of(new URL(url));
    } catch (MalformedURLException e) {
      return Optional.empty();
    }
  }
}
