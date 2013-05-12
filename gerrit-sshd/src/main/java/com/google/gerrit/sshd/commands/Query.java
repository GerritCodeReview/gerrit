// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.List;

@RequiresCapability(GlobalCapability.QUERY)
@CommandMetaData(name = "query", descr = "Query the change database")
class Query extends SshCommand {
  @Inject
  private QueryProcessor processor;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  void setFormat(QueryProcessor.OutputFormat format) {
    processor.setOutput(out, format);
  }

  @Option(name = "--current-patch-set", usage = "Include information about current patch set")
  void setCurrentPatchSet(boolean on) {
    processor.setIncludeCurrentPatchSet(on);
  }

  @Option(name = "--patch-sets", usage = "Include information about all patch sets")
  void setPatchSets(boolean on) {
    processor.setIncludePatchSets(on);
  }

  @Option(name = "--all-approvals", usage = "Include information about all patch sets and approvals")
  void setApprovals(boolean on) {
    if (on) {
      processor.setIncludePatchSets(on);
    }
    processor.setIncludeApprovals(on);
  }

  @Option(name = "--comments", usage = "Include patch set and inline comments")
  void setComments(boolean on) {
    processor.setIncludeComments(on);
  }

  @Option(name = "--files", usage = "Include file list on patch sets")
  void setFiles(boolean on) {
    processor.setIncludeFiles(on);
  }

  @Option(name = "--commit-message", usage = "Include the full commit message for a change")
  void setCommitMessage(boolean on) {
    processor.setIncludeCommitMessage(on);
  }

  @Option(name = "--dependencies", usage = "Include depends-on and needed-by information")
  void setDependencies(boolean on) {
    processor.setIncludeDependencies(on);
  }

  @Option(name = "--submit-records", usage = "Include submit and label status")
  void setSubmitRecords(boolean on) {
    processor.setIncludeSubmitRecords(on);
  }

  @Argument(index = 0, required = true, multiValued = true, metaVar = "QUERY", usage = "Query to execute")
  private List<String> query;

  @Override
  protected void run() throws Exception {
    processor.query(join(query, " "));
  }

  @Override
  protected void parseCommandLine() throws UnloggedFailure {
    processor.setOutput(out, QueryProcessor.OutputFormat.TEXT);
    super.parseCommandLine();
    if (processor.getIncludeFiles() &&
        !(processor.getIncludePatchSets() || processor.getIncludeCurrentPatchSet())) {
      throw new UnloggedFailure(1, "--files option needs --patch-sets or --current-patch-set");
    }
  }

  private static String join(List<String> list, String sep) {
    StringBuilder r = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        r.append(sep);
      }
      r.append(list.get(i));
    }
    return r.toString();
  }
}
