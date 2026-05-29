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

package com.google.gerrit.server.config;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
public class DefaultUrlFormatter implements UrlFormatter {
  private final Provider<String> canonicalWebUrlProvider;

  public static class DefaultUrlFormatterModule extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), UrlFormatter.class);
      DynamicItem.bind(binder(), UrlFormatter.class).to(DefaultUrlFormatter.class);
    }
  }

  @Inject
  public DefaultUrlFormatter(@CanonicalWebUrl Provider<String> canonicalWebUrlProvider) {
    this.canonicalWebUrlProvider = canonicalWebUrlProvider;
  }

  @Override
  public Optional<String> getWebUrl() {
    return Optional.ofNullable(canonicalWebUrlProvider.get());
  }
}
