/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * NB: This code was primarly ripped out of MINA SSHD.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
package com.google.gerrit.sshd.commands;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.tools.ToolsCatalog;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;

final class ScpCommand extends BaseCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TYPE_DIR = "D";
  private static final String TYPE_FILE = "C";

  private boolean opt_r;
  private boolean opt_t;
  private boolean opt_f;
  private String root;

  @Inject private ToolsCatalog toc;
  private IOException error;

  @Override
  public void setArguments(String[] args) {
    root = "";
    for (int i = 0; i < args.length; i++) {
      if (args[i].charAt(0) == '-') {
        for (int j = 1; j < args[i].length(); j++) {
          switch (args[i].charAt(j)) {
            case 'f':
              opt_f = true;
              break;
            case 'p':
              break;
            case 'r':
              opt_r = true;
              break;
            case 't':
              opt_t = true;
              break;
            case 'v':
              break;
          }
        }
      } else if (i == args.length - 1) {
        root = args[args.length - 1];
      }
    }
    if (!opt_f && !opt_t) {
      error = new IOException("Either -f or -t option should be set");
    }
  }

  @Override
  public void start(ChannelSession channel, Environment env) {
    startThread(this::runImp, AccessPath.SSH_COMMAND);
  }

  private void runImp() {
    try {
      readAck();
      if (error != null) {
        throw error;
      }

      if (opt_f) {
        if (root.startsWith("/")) {
          root = root.substring(1);
        }
        if (root.endsWith("/")) {
          root = root.substring(0, root.length() - 1);
        }
        if (root.equals(".")) {
          root = "";
        }

        final ToolsCatalog.Entry ent = toc.get(root);
        if (ent == null) {
          throw new IOException(root + " not found");

        } else if (ToolsCatalog.Entry.Type.FILE == ent.getType()) {
          readFile(ent);

        } else if (ToolsCatalog.Entry.Type.DIR == ent.getType()) {
          if (!opt_r) {
            throw new IOException(root + " not a regular file");
          }
          readDir(ent);
        } else {
          throw new IOException(root + " not supported");
        }
      } else {
        throw new IOException("Unsupported mode");
      }
    } catch (IOException e) {
      if (e.getClass() == IOException.class && "Pipe closed".equals(e.getMessage())) {
        // Ignore a pipe closed error, its the user disconnecting from us
        // while we are waiting for them to stalk.
        //
        return;
      }

      try {
        out.write(2);
        out.write(e.getMessage().getBytes(UTF_8));
        out.write('\n');
        out.flush();
      } catch (IOException e2) {
        // Ignore
      }
      logger.atFine().withCause(e).log("Error in scp command");
    }
  }

  private String readLine() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    for (; ; ) {
      int c = in.read();
      if (c == '\n') {
        return baos.toString(UTF_8);
      } else if (c == -1) {
        throw new IOException("End of stream");
      } else {
        baos.write(c);
      }
    }
  }

  private void readFile(ToolsCatalog.Entry ent) throws IOException {
    byte[] data = ent.getBytes();
    if (data == null) {
      throw new FileNotFoundException(ent.getPath());
    }

    header(ent, data.length);
    readAck();

    out.write(data);
    ack();
    readAck();
  }

  private void readDir(ToolsCatalog.Entry dir) throws IOException {
    header(dir, 0);
    readAck();

    for (ToolsCatalog.Entry e : dir.getChildren()) {
      if (ToolsCatalog.Entry.Type.DIR == e.getType()) {
        readDir(e);
      } else {
        readFile(e);
      }
    }

    out.write("E\n".getBytes(UTF_8));
    out.flush();
    readAck();
  }

  private void header(ToolsCatalog.Entry dir, int len)
      throws IOException, UnsupportedEncodingException {
    final StringBuilder buf = new StringBuilder();
    switch (dir.getType()) {
      case DIR:
        buf.append(TYPE_DIR);
        break;
      case FILE:
        buf.append(TYPE_FILE);
        break;
    }
    buf.append("0").append(Integer.toOctalString(dir.getMode())); // perms
    buf.append(" ");
    buf.append(len); // length
    buf.append(" ");
    buf.append(dir.getName());
    buf.append("\n");
    out.write(buf.toString().getBytes(UTF_8));
    out.flush();
  }

  private void ack() throws IOException {
    out.write(0);
    out.flush();
  }

  private void readAck() throws IOException {
    switch (in.read()) {
      case 0:
        break;
      case 1:
        logger.atFine().log("Received warning: %s", readLine());
        break;
      case 2:
        throw new IOException("Received nack: " + readLine());
    }
  }
}
