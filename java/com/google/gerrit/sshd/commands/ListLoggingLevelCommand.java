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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "ls-level",
    description = "list the level of loggers",
    runsAt = MASTER_OR_SLAVE)
public class ListLoggingLevelCommand extends SshCommand {

  @Argument(index = 0, required = false, metaVar = "NAME", usage = "used to match loggers")
  private String name;

  @Override
  protected void run() {
    enableGracefulStop();
    Map<String, String> logs = new TreeMap<>();
    for (Logger logger : getAllLoggers()) {
      if (name == null || logger.getName().contains(name)) {
        logs.put(logger.getName(), getEffectiveLevel(logger));
      }
    }
    for (Map.Entry<String, String> e : logs.entrySet()) {
      if (e.getKey() != null && !e.getKey().isEmpty()) {
        stdout.println(e.getKey() + ": " + e.getValue());
      }
    }
  }

  private static ImmutableList<Logger> getAllLoggers() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Set<String> seenNames = new HashSet<>();
    ImmutableList.Builder<Logger> builder = ImmutableList.builder();

    ctx.getLoggers()
        .forEach(
            logger -> {
              if (seenNames.add(logger.getName())) {
                builder.add(logger);
              }
            });

    return builder.build();
  }

  private static String getEffectiveLevel(Logger logger) {
    Level level = logger.getLevel();
    if (level != null) {
      return level.name();
    }
    Logger parent = logger.getParent();
    return (parent != null && parent.getLevel() != null) ? parent.getLevel().name() : "UNKNOWN";
  }
}
