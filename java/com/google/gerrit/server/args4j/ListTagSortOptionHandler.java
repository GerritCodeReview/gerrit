// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.extensions.common.ListTagSortOption;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class ListTagSortOptionHandler extends OptionHandler<ListTagSortOption> {
  @Inject
  public ListTagSortOptionHandler(
      @Assisted CmdLineParser parser,
      @Assisted OptionDef option,
      @Assisted Setter<ListTagSortOption> setter) {
    super(parser, option, setter);
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String param = params.getParameter(0);
    try {
      setter.addValue(ListTagSortOption.valueOf(param.toUpperCase()));
      return 1;
    } catch (IllegalArgumentException e) {
      throw new CmdLineException(
          owner, localizable("\"%s\" is not a valid sort option: %s"), param, e.getMessage());
    }
  }

  @Override
  public String getDefaultMetaVariable() {
    return "SORT";
  }
}
