// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.project;

/**
 *
 * This interface provides a cache interface to be used when evaluating
 * different regular expressions importance compared to each other.
 *
 */

public interface RegExpCache {
  /**
   * Returns the result of the shortestExample method for the given pattern.
   *
   * @param pattern the regular expression pattern to lookup shortestExample
   *        result for in cache.
   * @return the result of the shortestExample call for the given pattern.
   */
  public String get(String pattern);
}
