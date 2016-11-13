// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/** Utilities for option parsing. */
public class OptionUtil {
  private static final Splitter COMMA_OR_SPACE =
      Splitter.on(CharMatcher.anyOf(", ")).omitEmptyStrings().trimResults();

  public static Iterable<String> splitOptionValue(String value) {
    return Iterables.transform(COMMA_OR_SPACE.split(value), String::toLowerCase);
  }

  private OptionUtil() {}
}
