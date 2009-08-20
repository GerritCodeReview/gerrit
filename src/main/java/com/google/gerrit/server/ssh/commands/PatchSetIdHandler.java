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

package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.client.reviewdb.PatchSet;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class PatchSetIdHandler extends OptionHandler<PatchSet.Id> {
  public PatchSetIdHandler(final CmdLineParser parser, final OptionDef option,
      final Setter<? super PatchSet.Id> setter) {
    super(parser, option, setter);
  }

  @Override
  public final int parseArguments(final Parameters params)
      throws CmdLineException {
    final String idString = params.getParameter(0);
    final PatchSet.Id id;
    try {
      id = PatchSet.Id.parse(idString);
    } catch (IllegalArgumentException e) {
      throw new CmdLineException("Invalid patch set: " + idString);
    }

    setter.addValue(id);
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "CHANGE,PATCHSET";
  }
}
