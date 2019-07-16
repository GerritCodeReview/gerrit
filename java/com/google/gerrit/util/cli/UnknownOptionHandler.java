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

import com.google.gerrit.common.Nullable;

/**
 * Classes that define command-line options by using the {@link org.kohsuke.args4j.Option}
 * annotation can implement this class to accept and handle unknown options.
 *
 * <p>If a user specifies an unknown option and this unknown option doesn't get accepted, the
 * parsing of the command-line options fails and the user gets an error (this is the default
 * behavior if classes do not implement this interface).
 */
public interface UnknownOptionHandler {
  /**
   * Whether an unknown option should be accepted.
   *
   * <p>If an unknown option is not accepted, the parsing of the command-line options fails and the
   * user gets an error.
   *
   * <p>This method can be used to ignore unknown options (without failure for the user) or to
   * handle them.
   *
   * @param name the name of an unknown option that was provided by the user
   * @param value the value of the unknown option that was provided by the user
   * @return whether this unknown options is accepted
   */
  boolean accept(String name, @Nullable String value);
}
