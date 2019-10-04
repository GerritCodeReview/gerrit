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

import static com.google.gerrit.util.cli.Localizable.localizable;

import com.google.common.base.Splitter;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
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
  public final int parseArguments(Parameters params) throws CmdLineException {
    final String token = params.getParameter(0);
    final List<String> tokens = Splitter.on(',').splitToList(token);
    if (tokens.size() != 3) {
      throw new CmdLineException(
          owner, localizable("change should be specified as <project>,<branch>,<change-id>"));
    }

    try {
      final Change.Key key = Change.Key.parse(tokens.get(2));
      final Project.NameKey project = Project.nameKey(tokens.get(0));
      final BranchNameKey branch = BranchNameKey.create(project, tokens.get(1));
      for (ChangeData cd : queryProvider.get().byBranchKey(branch, key)) {
        setter.addValue(cd.getId());
        return 1;
      }
    } catch (IllegalArgumentException e) {
      throw new CmdLineException(owner, localizable("Change-Id is not valid"));
    } catch (StorageException e) {
      throw new CmdLineException(owner, localizable("Database error: %s"), e.getMessage());
    }

    throw new CmdLineException(owner, localizable("\"%s\": change not found"), token);
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "CHANGE";
  }
}
