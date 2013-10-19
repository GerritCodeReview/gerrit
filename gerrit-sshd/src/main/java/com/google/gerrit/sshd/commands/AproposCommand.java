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

import com.google.gerrit.server.documentation.QueryDocumentationExecutor;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor.DocResult;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;

import java.util.List;

@CommandMetaData(name = "apropos", description = "Search in Gerrit documentation")
final class AproposCommand extends SshCommand {
  @Inject
  private QueryDocumentationExecutor searcher;

  @Argument(index=0, multiValued = false, required = true, metaVar = "QUERY")
  private String q;

  @Override
  public void run() throws Exception {
    List<QueryDocumentationExecutor.DocResult> res = searcher.doQuery(q);
      for (DocResult docResult : res) {
        stdout.println(String.format("%s: %s",
            docResult.title, docResult.url));
      }
  }
}
