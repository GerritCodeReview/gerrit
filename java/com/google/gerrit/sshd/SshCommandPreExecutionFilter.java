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

package com.google.gerrit.sshd;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.List;

@ExtensionPoint
public interface SshCommandPreExecutionFilter {

  /**
   * Check the command and throw an exception if this command must not be run.
   *
   * @param command the command
   * @param arguments the list of arguments
   * @return whether or not the filter can be executed
   */
  boolean accept(String command, List<String> arguments);

  default String name() {
    return this.getClass().getSimpleName();
  }
}
