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

package com.google.gerrit.server.args4j;

import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.net.SocketAddress;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class SocketAddressHandler extends OptionHandler<SocketAddress> {

  @Inject
  public SocketAddressHandler(
      @Assisted final CmdLineParser parser,
      @Assisted final OptionDef option,
      @Assisted final Setter<SocketAddress> setter) {
    super(parser, option, setter);
  }

  @Override
  public final int parseArguments(Parameters params) throws CmdLineException {
    final String token = params.getParameter(0);
    try {
      setter.addValue(SocketUtil.parse(token, 0));
    } catch (IllegalArgumentException e) {
      throw new CmdLineException(owner, e.getMessage());
    }
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "HOST:PORT";
  }
}
