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

package com.google.gerrit.server.args4j;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class AccountGroupIdHandler extends OptionHandler<AccountGroup.Id> {
  private final GroupCache groupCache;

  @Inject
  public AccountGroupIdHandler(
      final GroupCache groupCache,
      @Assisted final CmdLineParser parser,
      @Assisted final OptionDef option,
      @Assisted final Setter<AccountGroup.Id> setter) {
    super(parser, option, setter);
    this.groupCache = groupCache;
  }

  @Override
  public final int parseArguments(final Parameters params) throws CmdLineException {
    final String n = params.getParameter(0);
    final AccountGroup group = groupCache.get(new AccountGroup.NameKey(n));
    if (group == null) {
      throw new CmdLineException(owner, "Group \"" + n + "\" does not exist");
    }
    setter.addValue(group.getId());
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "GROUP";
  }
}
