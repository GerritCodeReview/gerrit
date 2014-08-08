package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.Enumeration;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "set-logging-level", description = "Change the level of loggers")
final public class SetLoggingLevelCommand extends SshCommand {
  private static final String RESET = "reset";
  private static final String LEVEL_ALL = "all";
  private static final String LEVEL_TRACE = "trace";
  private static final String LEVEL_DEBUG = "debug";
  private static final String LEVEL_INFO = "info";
  private static final String LEVEL_WARN = "warn";
  private static final String LEVEL_ERROR = "error";
  private static final String LEVEL_FATAL = "fatal";
  private static final String LEVEL_OFF = "off";

  @Argument(index = 0, required = true, metaVar = "LEVEL", usage = "logging level to set to")
  private String level;

  @Option(name = "--logger", aliases = "-l", metaVar = "LOGGER", usage = "loggers to change")
  private String regex;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    if (regex == null) {
      regex = "^*.*$";
    }
    switch(level.toLowerCase()) {
      case RESET:
        reset();
        break;
      case LEVEL_ALL:
        setLoggingLevel(Level.ALL);
        break;
      case LEVEL_TRACE:
        setLoggingLevel(Level.TRACE);
        break;
      case LEVEL_DEBUG:
        setLoggingLevel(Level.DEBUG);
        break;
      case LEVEL_INFO:
        setLoggingLevel(Level.INFO);
        break;
      case LEVEL_WARN:
        setLoggingLevel(Level.WARN);
        break;
      case LEVEL_ERROR:
        setLoggingLevel(Level.ERROR);
        break;
      case LEVEL_FATAL:
        setLoggingLevel(Level.FATAL);
        break;
      case LEVEL_OFF:
        setLoggingLevel(Level.OFF);
        break;
      default:
        stdout.println("Invalid argument");
        stdout.flush();
        break;
    }
  }

  private void reset() {
    LogManager.resetConfiguration();
    PropertyConfigurator.configure(getClass().getClassLoader().getResource("log4j.properties"));
  }

  @SuppressWarnings("unchecked")
  private void setLoggingLevel(Level level) {
    for (Enumeration<Logger> logger = LogManager.getCurrentLoggers(); logger.hasMoreElements();) {
      Logger log = logger.nextElement();
      if (log.getName().matches(regex)) {
        log.setLevel(level);
      }
    }
  }
}
