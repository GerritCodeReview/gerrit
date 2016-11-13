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
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
  name = "ls-level",
  description = "list the level of loggers",
  runsAt = MASTER_OR_SLAVE
)
public class ListLoggingLevelCommand extends SshCommand {

  @Argument(index = 0, required = false, metaVar = "NAME", usage = "used to match loggers")
  private String name;

  @SuppressWarnings("unchecked")
  @Override
  protected void run() {
    Map<String, String> logs = new TreeMap<>();
    for (Enumeration<Logger> logger = LogManager.getCurrentLoggers(); logger.hasMoreElements(); ) {
      Logger log = logger.nextElement();
      if (name == null || log.getName().contains(name)) {
        logs.put(log.getName(), log.getEffectiveLevel().toString());
      }
    }
    for (Map.Entry<String, String> e : logs.entrySet()) {
      stdout.println(e.getKey() + ": " + e.getValue());
    }
  }
}
