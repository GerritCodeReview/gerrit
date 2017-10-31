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

package com.google.gerrit.util.cli;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/** Typically used with {@code @Option(name="--")} to signal end of options. */
public class EndOfOptionsHandler extends OptionHandler<Boolean> {
  public EndOfOptionsHandler(
      CmdLineParser parser, OptionDef option, Setter<? super Boolean> setter) {
    super(parser, option, setter);
  }

  @Override
  public String getDefaultMetaVariable() {
    return null;
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    owner.stopOptionParsing();
    setter.addValue(true);
    return 0;
  }
}
