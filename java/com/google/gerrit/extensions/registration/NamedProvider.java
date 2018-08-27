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

package com.google.gerrit.extensions.registration;

import com.google.inject.Provider;

/** Pair of provider implementation and plugin providing it. */
class NamedProvider<T> {
  final Provider<T> impl;
  final String pluginName;

  NamedProvider(Provider<T> provider, String pluginName) {
    this.impl = provider;
    this.pluginName = pluginName;
  }
}
