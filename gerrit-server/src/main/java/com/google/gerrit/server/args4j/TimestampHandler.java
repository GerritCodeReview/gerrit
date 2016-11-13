// Copyright (C) 2014 The Android Open Source Project
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
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class TimestampHandler extends OptionHandler<Timestamp> {
  public static final String TIMESTAMP_FORMAT = "yyyyMMdd_HHmm";

  @Inject
  public TimestampHandler(
      @Assisted CmdLineParser parser,
      @Assisted OptionDef option,
      @Assisted Setter<Timestamp> setter) {
    super(parser, option, setter);
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String timestamp = params.getParameter(0);
    try {
      DateFormat fmt = new SimpleDateFormat(TIMESTAMP_FORMAT);
      fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
      setter.addValue(new Timestamp(fmt.parse(timestamp).getTime()));
      return 1;
    } catch (ParseException e) {
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
