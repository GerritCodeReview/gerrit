// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.config.CanonicalWebUrl;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;

/** Supports binding the {@link CanonicalWebUrl} annotation. */
public abstract class CanonicalWebUrlModule extends AbstractModule {
  @Override
  protected void configure() {
    // Note that the CanonicalWebUrl itself must not be a singleton, but its
    // provider must be.
    //
    // If the value was not configured in the system configuration data the
    // provider may try to guess it from the current HTTP request, if we are
    // running in an HTTP environment.
    //
    final Class<? extends Provider<String>> provider = provider();
    bind(String.class).annotatedWith(CanonicalWebUrl.class).toProvider(provider);
  }

  protected abstract Class<? extends Provider<String>> provider();
}
