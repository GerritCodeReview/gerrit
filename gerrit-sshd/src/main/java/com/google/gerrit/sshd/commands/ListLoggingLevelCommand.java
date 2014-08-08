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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;

import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.Argument;

import java.util.Enumeration;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "ls", description = "list the level of loggers",
  runsAt = MASTER_OR_SLAVE)
public class ListLoggingLevelCommand extends SshCommand {

  @Argument(index = 0, required = false, metaVar = "LOGGER", usage = "loggers to print")
  private String logger;

  @Override
  protected void run() {
    if (logger == null) {
      printUsingRegex("^.*");
    } else if (logger.startsWith("^")) {
      printUsingRegex(logger);
    } else {
      Logger log = LogManager.getLogger(logger);
      stdout.println(log.getName() + ": " + log.getEffectiveLevel());
    }
  }

  @SuppressWarnings("unchecked")
  private void printUsingRegex(String regex) {
    if (regex.startsWith("^")) {
      regex = regex.substring(1);
      if (regex.endsWith("$") && !regex.endsWith("\\$")) {
        regex = regex.substring(0, regex.length() - 1);
      }
    }
    final RunAutomaton a = new RunAutomaton(new RegExp(regex).toAutomaton());
    for (Enumeration<Logger> logger = LogManager.getCurrentLoggers(); logger
        .hasMoreElements();) {
      Logger log = logger.nextElement();
      if (a.run(log.getName())) {
        stdout.println(log.getName() + ": " + log.getEffectiveLevel());
      }
    }
  }
}
