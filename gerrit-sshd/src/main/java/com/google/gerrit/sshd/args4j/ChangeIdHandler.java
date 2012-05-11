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

package com.google.gerrit.sshd.args4j;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.changedetail.ChangeIdFromTriplet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class ChangeIdHandler extends OptionHandler<Change.Id> {

  private ChangeIdFromTriplet.Factory factory;

  @Inject
  public ChangeIdHandler(
      final ChangeIdFromTriplet.Factory factory,
      @Assisted final CmdLineParser parser, @Assisted final OptionDef option,
      @Assisted final Setter<Change.Id> setter) {
    super(parser, option, setter);
    this.factory = factory;
  }

  @Override
  public final int parseArguments(final Parameters params)
      throws CmdLineException {
    final String token = params.getParameter(0);
    try {
      final Change.Id changeId = factory.create(token).call();
      if (changeId == null) {
        throw new CmdLineException(owner, "No such change " + token);
      }
      setter.addValue(factory.create(token).get());
    } catch (OrmException e) {
      throw new CmdLineException(owner, "Database error: " + e.getMessage());
    }
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "CHANGE";
  }
}
