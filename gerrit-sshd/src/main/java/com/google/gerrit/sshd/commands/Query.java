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

import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.List;

class Query extends BaseCommand {
  @Inject
  private QueryProcessor processor;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  void setFormat(QueryProcessor.OutputFormat format) {
    processor.setOutput(out, format);
  }

  @Option(name = "-n", aliases = {"--limit"}, metaVar = "CNT", usage = "Maximum number of results to output")
  void setLimit(int limit) {
    processor.setLimit(limit);
  }

  @Option(name = "--sort-key", metaVar = "KEY", usage = "sortKey to resume results at")
  void setSortKey(String sortKey) {
    processor.setSortKey(sortKey);
  }

  @Option(name = "--current-patch-set", usage = "Include information about current patch set")
  void setCurrentPatchSet(boolean on) {
    processor.setIncludeCurrentPatchSet(on);
  }

  @Option(name = "--patch-sets", usage = "Include information about all patch sets")
  void setPatchSets(boolean on) {
    processor.setIncludePatchSets(on);
  }

  @Argument(index = 0, required = true, multiValued = true, metaVar = "QUERY", usage = "Query to execute")
  private List<String> query;

  @Override
  public void start(Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        processor.setLimit(2000);
        processor.setOutput(out, QueryProcessor.OutputFormat.TEXT);
        parseCommandLine();
        processor.query(join(query, " "));
      }
    });
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
