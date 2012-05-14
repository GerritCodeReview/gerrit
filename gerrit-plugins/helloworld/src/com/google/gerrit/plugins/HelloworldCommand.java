package com.google.gerrit.plugins;

import com.google.gerrit.sshd.BaseCommand;

import org.apache.sshd.server.Environment;

import java.io.PrintWriter;

public final class HelloworldCommand extends BaseCommand {
  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        final PrintWriter stdout = toPrintWriter(out);
        stdout.println("Hello world!");
        stdout.flush();
      }
    });
  }
}
