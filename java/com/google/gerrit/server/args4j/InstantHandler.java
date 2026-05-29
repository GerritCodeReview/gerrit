// Copyright (C) 2021 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class InstantHandler extends OptionHandler<Instant> {
  public static final String TIMESTAMP_FORMAT = "yyyyMMdd_HHmm";

  @Inject
  public InstantHandler(
      @Assisted CmdLineParser parser,
      @Assisted OptionDef option,
      @Assisted Setter<Instant> setter) {
    super(parser, option, setter);
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String timestamp = params.getParameter(0);
    try {
      setter.addValue(
          DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT)
              .withZone(ZoneId.of("UTC"))
              .parse(timestamp, Instant::from));
      return 1;
    } catch (DateTimeParseException e) {
      throw new CmdLineException(
          owner,
          String.format("Invalid timestamp: %s; expected format: %s", timestamp, TIMESTAMP_FORMAT),
          e);
    }
  }

  @Override
  public String getDefaultMetaVariable() {
    return "TIMESTAMP";
  }
}
