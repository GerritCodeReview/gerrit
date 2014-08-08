package com.google.gerrit.sshd.commands;

import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.Option;

import java.util.Enumeration;

@CommandMetaData(name = "ls-logging-level", description = "list the level of loggers")
final public class ListLoggingLevelCommand extends SshCommand {

  @Option(name = "--logger", aliases = "-l", usage = "loggers to view")
  private String regex;

  @Override
  protected void run() throws UnloggedFailure {
    if (regex == null) {
      regex = "^*.*$";
    }
    printAll();
  }

  @SuppressWarnings("unchecked")
  private void printAll() {
    for (Enumeration<Logger> logger = LogManager.getCurrentLoggers(); logger.hasMoreElements();) {
      Logger log = logger.nextElement();
      if (log.getName().matches(regex)) {
        Level l = log.getEffectiveLevel();
        stdout.println(log.getName() + " : " + l);
      }
    }
    stdout.flush();
  }
}
