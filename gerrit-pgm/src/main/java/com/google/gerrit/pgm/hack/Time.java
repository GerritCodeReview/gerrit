package com.google.gerrit.pgm.hack;

import com.google.gerrit.sshd.BaseCommand;

import org.apache.sshd.server.Environment;

import java.io.IOException;
import java.io.PrintWriter;

public class Time extends BaseCommand {

  @Override
  public void start(Environment env) throws IOException {
    startThread(new CommandRunnable() {

      @Override
      public void run() throws Exception {
        parseCommandLine();
        PrintWriter stdout = toPrintWriter(out);
        stdout.println(System.currentTimeMillis());
        stdout.flush();
      }
    });
  }

}
