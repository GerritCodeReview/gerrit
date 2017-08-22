// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.common.TestSubmitRuleInput.Filters;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.nio.ByteBuffer;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

abstract class BaseTestPrologCommand extends SshCommand {
  private TestSubmitRuleInput input = new TestSubmitRuleInput();

  @Inject private ChangesCollection changes;

  @Inject private Revisions revisions;

  @Argument(index = 0, required = true, usage = "ChangeId to load in prolog environment")
  protected String changeId;

  @Option(
    name = "-s",
    usage =
        "Read prolog script from stdin instead of reading rules.pl from the refs/meta/config branch"
  )
  protected boolean useStdin;

  @Option(
    name = "--no-filters",
    aliases = {"-n"},
    usage = "Don't run the submit_filter/2 from the parent projects"
  )
  void setNoFilters(boolean no) {
    input.filters = no ? Filters.SKIP : Filters.RUN;
  }

  protected abstract RestModifyView<RevisionResource, TestSubmitRuleInput> createView();

  @Override
  protected final void run() throws UnloggedFailure {
    try {
      RevisionResource revision =
          revisions.parse(
              changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(changeId)),
              IdString.fromUrl("current"));
      if (useStdin) {
        ByteBuffer buf = IO.readWholeStream(in, 4096);
        input.rule = RawParseUtils.decode(buf.array(), buf.arrayOffset(), buf.limit());
      }
      Object result = createView().apply(revision, input);
      OutputFormat.JSON.newGson().toJson(result, stdout);
      stdout.print('\n');
    } catch (Exception e) {
      throw die("Processing of prolog script failed: " + e);
    }
  }
}
