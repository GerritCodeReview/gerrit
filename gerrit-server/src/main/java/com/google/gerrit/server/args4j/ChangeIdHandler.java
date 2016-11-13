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

package com.google.gerrit.server.args4j;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class ChangeIdHandler extends OptionHandler<Change.Id> {
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  public ChangeIdHandler(
      // TODO(dborowitz): Not sure whether this is injectable here.
      Provider<InternalChangeQuery> queryProvider,
      @Assisted final CmdLineParser parser,
      @Assisted final OptionDef option,
      @Assisted final Setter<Change.Id> setter) {
    super(parser, option, setter);
    this.queryProvider = queryProvider;
  }

  @Override
  public final int parseArguments(final Parameters params) throws CmdLineException {
    final String token = params.getParameter(0);
    final String[] tokens = token.split(",");
    if (tokens.length != 3) {
      throw new CmdLineException(
          owner, "change should be specified as " + "<project>,<branch>,<change-id>");
    }

    try {
      final Change.Key key = Change.Key.parse(tokens[2]);
      final Project.NameKey project = new Project.NameKey(tokens[0]);
      final Branch.NameKey branch = new Branch.NameKey(project, tokens[1]);
      for (final ChangeData cd : queryProvider.get().byBranchKey(branch, key)) {
        setter.addValue(cd.getId());
        return 1;
      }
    } catch (IllegalArgumentException e) {
      throw new CmdLineException(owner, "Change-Id is not valid");
    } catch (OrmException e) {
      throw new CmdLineException(owner, "Database error: " + e.getMessage());
    }

    throw new CmdLineException(owner, "\"" + token + "\": change not found");
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "CHANGE";
  }
}
