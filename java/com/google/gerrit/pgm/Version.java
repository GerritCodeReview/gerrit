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

package com.google.gerrit.pgm;

import com.google.gerrit.pgm.util.AbstractProgram;

/** Display the version of Gerrit. */
public class Version extends AbstractProgram {
  @Override
  public int run() throws Exception {
    final String v = com.google.gerrit.common.Version.getVersion();
    if (v == null) {
      System.err.println("fatal: version unavailable");
      return 1;
    }
    System.out.println("gerrit version " + v);
    return 0;
  }
}
