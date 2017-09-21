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

package com.google.gerrit.server;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.args4j.AccountGroupIdHandler;
import com.google.gerrit.server.args4j.AccountGroupUUIDHandler;
import com.google.gerrit.server.args4j.AccountIdHandler;
import com.google.gerrit.server.args4j.ChangeIdHandler;
import com.google.gerrit.server.args4j.ObjectIdHandler;
import com.google.gerrit.server.args4j.PatchSetIdHandler;
import com.google.gerrit.server.args4j.ProjectControlHandler;
import com.google.gerrit.server.args4j.SocketAddressHandler;
import com.google.gerrit.server.args4j.TimestampHandler;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.OptionHandlerUtil;
import com.google.gerrit.util.cli.OptionHandlers;
import java.net.SocketAddress;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.spi.OptionHandler;

public class CmdLineParserModule extends FactoryModule {
  public CmdLineParserModule() {}

  @Override
  protected void configure() {
    factory(CmdLineParser.Factory.class);
    bind(OptionHandlers.class);

    registerOptionHandler(Account.Id.class, AccountIdHandler.class);
    registerOptionHandler(AccountGroup.Id.class, AccountGroupIdHandler.class);
    registerOptionHandler(AccountGroup.UUID.class, AccountGroupUUIDHandler.class);
    registerOptionHandler(Change.Id.class, ChangeIdHandler.class);
    registerOptionHandler(ObjectId.class, ObjectIdHandler.class);
    registerOptionHandler(PatchSet.Id.class, PatchSetIdHandler.class);
    registerOptionHandler(ProjectControl.class, ProjectControlHandler.class);
    registerOptionHandler(SocketAddress.class, SocketAddressHandler.class);
    registerOptionHandler(Timestamp.class, TimestampHandler.class);
  }

  private <T> void registerOptionHandler(Class<T> type, Class<? extends OptionHandler<T>> impl) {
    install(OptionHandlerUtil.moduleFor(type, impl));
  }
}
