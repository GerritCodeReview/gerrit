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

import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.query.change.OutputStreamQuery;
import com.google.gerrit.server.query.change.OutputStreamQuery.OutputFormat;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "query", description = "Query the change database")
public class Query extends SshCommand implements DynamicOptions.BeanReceiver {
  @Inject private OutputStreamQuery processor;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  void setFormat(OutputFormat format) {
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

  @Option(
      name = "--all-approvals",
      usage = "Include information about all patch sets and approvals")
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

  @Option(name = "--all-reviewers", usage = "Include all reviewers")
  void setAllReviewers(boolean on) {
    processor.setIncludeAllReviewers(on);
  }

  @Option(name = "--submit-records", usage = "Include submit and label status")
  void setSubmitRecords(boolean on) {
    processor.setIncludeSubmitRecords(on);
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      usage = "Number of changes to skip")
  void setStart(int start) {
    processor.setStart(start);
  }

  @Option(name = "--no-limit", usage = "Return all results, overriding the default limit")
  void setNoLimit(boolean on) {
    processor.setNoLimit(on);
  }

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "QUERY",
      usage = "Query to execute")
  private List<String> query;

  @Override
  protected void run() throws Exception {
    processor.query(join(query, " "));
  }

  @Override
  public void setDynamicBean(String plugin, DynamicOptions.DynamicBean dynamicBean) {
    processor.setDynamicBean(plugin, dynamicBean);
  }

  @Override
  protected void parseCommandLine() throws UnloggedFailure {
    processor.setOutput(out, OutputFormat.TEXT);
    super.parseCommandLine();
    if (processor.getIncludeFiles()
        && !(processor.getIncludePatchSets() || processor.getIncludeCurrentPatchSet())) {
      throw die("--files option needs --patch-sets or --current-patch-set");
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
