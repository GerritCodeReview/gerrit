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

package com.google.gerrit.server.git.receive;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import java.util.Arrays;
import java.util.Locale;

/** Possible values for {@code -o notedb=X} push option. */
public enum NoteDbPushOption {
  DISALLOW,
  ALLOW;

  public static final String OPTION_NAME = "notedb";

  private static final ImmutableMap<String, NoteDbPushOption> ALL =
      Arrays.stream(values()).collect(toImmutableMap(NoteDbPushOption::value, o -> o));

  @Nullable
  public static NoteDbPushOption parse(String value) {
    return ALL.get(value);
  }

  public String value() {
    return name().toLowerCase(Locale.US);
  }
}
