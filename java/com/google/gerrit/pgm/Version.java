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

import com.google.gerrit.extensions.common.VersionInfo;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.pgm.util.AbstractProgram;
import com.google.gerrit.server.version.VersionInfoModule;
import org.kohsuke.args4j.Option;

/** Display the version of Gerrit. */
public class Version extends AbstractProgram {

  @Option(
      name = "--verbose",
      aliases = {"-v"},
      usage = "verbose version info")
  private boolean verbose;

  @Option(name = "--json", usage = "json output format, assumes verbose output")
  private boolean json;

  @Override
  public int run() throws Exception {
    VersionInfo versionInfo = new VersionInfoModule().createVersionInfo();
    if (versionInfo.gerritVersion == null) {
      System.err.println("fatal: version unavailable");
      return 1;
    }

    if (json) {
      System.out.println(OutputFormat.JSON.newGson().toJson(versionInfo));
    } else if (verbose) {
      System.out.print(versionInfo.verbose());
    } else {
      System.out.print(versionInfo.compact());
    }

    return 0;
  }
}
