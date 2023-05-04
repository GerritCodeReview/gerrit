// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

/** An entity that could be cacheable when referred or returned by a REST API */
public interface Cacheability {

  /**
   * Returns the cacheability of the entity. Intended to be overridden for exposing the intent to be
   * cached.
   *
   * @return false when the entity should not be cached.
   */
  boolean isCacheable();
}
