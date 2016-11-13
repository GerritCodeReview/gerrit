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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor.DocQueryException;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor.DocResult;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.List;
import org.kohsuke.args4j.Argument;

@CommandMetaData(
  name = "apropos",
  description = "Search in Gerrit documentation",
  runsAt = MASTER_OR_SLAVE
)
final class AproposCommand extends SshCommand {
  @Inject private QueryDocumentationExecutor searcher;
  @Inject @CanonicalWebUrl String url;

  @Argument(index = 0, required = true, metaVar = "QUERY")
  private String q;

  @Override
  public void run() throws Exception {
    try {
      List<QueryDocumentationExecutor.DocResult> res = searcher.doQuery(q);
      for (DocResult docResult : res) {
        stdout.println(String.format("%s:\n%s%s\n", docResult.title, url, docResult.url));
      }
    } catch (DocQueryException dqe) {
      throw die(dqe);
    }
  }
}
