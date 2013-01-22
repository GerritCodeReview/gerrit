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

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.ListMembers;
import com.google.gerrit.server.group.MembersCollection.MemberInfo;
import com.google.gerrit.server.ioutil.ColumnFormatter;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class ListMembersCommand extends BaseCommand {
  @Inject
  private MyListMembers impl;

  @Override
  public void start(Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine(impl);
        final PrintWriter stdout = toPrintWriter(out);
        try {
          impl.display(stdout);
        } finally {
          stdout.flush();
        }
      }
    });
  }

  private static class MyListMembers extends ListMembers {
    @Argument(index = 0, required = true, metaVar = "GROUP",
        usage = "name of group for which the members should be listed")
    private AccountGroup.UUID groupUUID;

    @Option(name = "--verbose", aliases = {"-v"},
        usage = "verbose output format with tab-separated columns for the " +
            "full name, account id, username, and preferred email")
    private boolean verboseOutput;

    private final GroupControl.Factory groupContralFactory;
    private final String anonymousCowardName;

    @Inject
    MyListMembers(final GroupCache groupCache,
        final GroupDetailFactory.Factory groupDetailFactory,
        final AccountCache accountCache,
        final GroupControl.Factory groupContralFactory,
        final @AnonymousCowardName String anonymousCowardName) {
      super(groupCache, groupDetailFactory, accountCache);
      this.groupContralFactory = groupContralFactory;
      this.anonymousCowardName = anonymousCowardName;
    }

    void display(final PrintWriter out) throws OrmException, NoSuchGroupException {
      final List<MemberInfo> members = apply(new GroupResource(groupContralFactory
          .controlFor(groupUUID)));
      final ColumnFormatter formatter = new ColumnFormatter(out, '\t');
      for (final MemberInfo info : members) {
        formatter.addColumn(info.fullName != null ? info.fullName : anonymousCowardName);
        if (verboseOutput) {
          formatter.addColumn(Integer.toString(info.accountId));
          formatter.addColumn(Strings.nullToEmpty(info.userName));
          formatter.addColumn(Strings.nullToEmpty(info.preferredEmail));
        }
        formatter.nextLine();
      }
      formatter.finish();
    }
  }
}
