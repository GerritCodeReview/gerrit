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

import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.sshd.commands.QueryShell;
import org.kohsuke.args4j.Option;

/** Run Gerrit's SQL query tool */
// TODO(dborowitz): Delete this program.
public class Gsql extends SiteProgram {
  @Option(name = "--format", usage = "Set output format")
  private QueryShell.OutputFormat format = QueryShell.OutputFormat.PRETTY;

  @Option(name = "-c", metaVar = "SQL QUERY", usage = "Query to execute")
  private String query;

  @Override
  public int run() throws Exception {
    throw die("SQL not supported; ReviewDb no longer exists");
  }
}
