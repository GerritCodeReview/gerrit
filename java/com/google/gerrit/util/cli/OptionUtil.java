// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.util.cli;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableList;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.OptionHandler;

/** Utilities to support creating new {@link Option} instances. */
public class OptionUtil {
  @AutoAnnotation
  @SuppressWarnings("rawtypes")
  public static Option newOption(
      String name,
      ImmutableList<String> aliases,
      String usage,
      String metaVar,
      boolean required,
      boolean help,
      boolean hidden,
      Class<? extends OptionHandler> handler,
      ImmutableList<String> depends,
      ImmutableList<String> forbids) {
    return new AutoAnnotation_OptionUtil_newOption(
        name, aliases, usage, metaVar, required, help, hidden, handler, depends, forbids);
  }
}
