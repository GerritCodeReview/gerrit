package com.google.gerrit.plugins;

import com.google.gerrit.sshd.BaseCommand;

import org.apache.sshd.server.Environment;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;

public final class TimestampCommand extends BaseCommand {
  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        final PrintWriter stdout = toPrintWriter(out);
        stdout.println("Server Time: "
            + DateFormat.getDateTimeInstance().format(new Date()));
        stdout.flush();
      }
    });
  }
}
