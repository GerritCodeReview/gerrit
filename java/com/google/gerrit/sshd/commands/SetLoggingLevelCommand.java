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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import java.net.MalformedURLException;
import java.net.URI;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "set-level",
    description = "Change the level of loggers",
    runsAt = MASTER_OR_SLAVE)
public class SetLoggingLevelCommand extends SshCommand {
  private static final String LOG_CONFIGURATION = "log4j.properties";
  private static final String JAVA_OPTIONS_LOG_CONFIG = "log4j.configuration";

  private enum LevelOption {
    ALL,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
    OFF,
    RESET,
  }

  @Argument(index = 0, required = true, metaVar = "LEVEL", usage = "logging level to set to")
  private LevelOption level;

  @Argument(index = 1, required = false, metaVar = "NAME", usage = "used to match loggers")
  private String name;

  @Override
  protected void run() throws MalformedURLException {
    enableGracefulStop();
    if (level == LevelOption.RESET) {
      reset();
    } else {
      for (Logger logger : getCurrentLoggers()) {
        if (name == null || logger.getName().contains(name)) {
          logger.setLevel(Level.toLevel(level.name()));
        }
      }
    }
  }

  private static void reset() throws MalformedURLException {
    for (Logger logger : getCurrentLoggers()) {
      logger.setLevel(null);
    }

    String path = System.getProperty(JAVA_OPTIONS_LOG_CONFIG);
    if (Strings.isNullOrEmpty(path)) {
      PropertyConfigurator.configure(Loader.getResource(LOG_CONFIGURATION));
    } else {
      PropertyConfigurator.configure(URI.create(path).toURL());
    }
  }

  @SuppressWarnings({"unchecked", "JdkObsolete"})
  private static ImmutableList<Logger> getCurrentLoggers() {
    return ImmutableList.copyOf(Iterators.forEnumeration(LogManager.getCurrentLoggers()));
  }
}
