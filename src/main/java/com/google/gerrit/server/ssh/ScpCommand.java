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
package com.google.gerrit.server.ssh;

import org.apache.sshd.server.CommandFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

class ScpCommand implements CommandFactory.Command, Runnable {
  private static final String TYPE_DIR = "D";
  private static final String TYPE_FILE = "C";
  private static final Logger log = LoggerFactory.getLogger(ScpCommand.class);

  private boolean opt_r;
  private boolean opt_t;
  private boolean opt_f;
  private boolean opt_v;
  private boolean opt_p;
  private String root;

  private TreeMap<String, Entry> toc;
  private InputStream in;
  private OutputStream out;
  private OutputStream err;
  private CommandFactory.ExitCallback callback;
  private IOException error;

  public ScpCommand(final String[] args) {
    root = "";
    for (int i = 0; i < args.length; i++) {
      if (args[i].charAt(0) == '-') {
        for (int j = 1; j < args[i].length(); j++) {
          switch (args[i].charAt(j)) {
            case 'f':
              opt_f = true;
              break;
            case 'p':
              opt_p = true;
              break;
            case 'r':
              opt_r = true;
              break;
            case 't':
              opt_t = true;
              break;
            case 'v':
              opt_v = true;
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

  public void setInputStream(InputStream in) {
    this.in = in;
  }

  public void setOutputStream(OutputStream out) {
    this.out = out;
  }

  public void setErrorStream(OutputStream err) {
    this.err = err;
  }

  public void setExitCallback(CommandFactory.ExitCallback callback) {
    this.callback = callback;
  }

  public void start() throws IOException {
    if (error != null) {
      throw error;
    }
    new Thread(this).start();
  }

  public void run() {
    try {
      readToc();
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

        final Entry ent = toc.get(root);
        if (ent == null) {
          throw new IOException(root + " not found");

        } else if (TYPE_FILE.equals(ent.type)) {
          readFile(ent);

        } else if (TYPE_DIR.equals(ent.type)) {
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
      try {
        out.write(2);
        out.write(e.getMessage().getBytes());
        out.write('\n');
        out.flush();
      } catch (IOException e2) {
        // Ignore
      }
      log.debug("Error in scp command", e);
    } finally {
      callback.onExit(0);
    }
  }

  private void readToc() throws IOException {
    toc = new TreeMap<String, Entry>();
    final BufferedReader br =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
            read("TOC")), "UTF-8"));
    String line;
    while ((line = br.readLine()) != null) {
      if (line.length() > 0 && !line.startsWith("#")) {
        final Entry e = new Entry(TYPE_FILE, line);
        toc.put(e.path, e);
      }
    }

    final List<Entry> all = new ArrayList<Entry>(toc.values());
    for (Entry e : all) {
      String path = dirOf(e.path);
      while (path != null) {
        Entry d = toc.get(path);
        if (d == null) {
          d = new Entry(TYPE_DIR, 0755, path);
          toc.put(d.path, d);
        }
        d.children.add(e);
        path = dirOf(path);
        e = d;
      }
    }

    final Entry top = new Entry(TYPE_DIR, 0755, "");
    for (Entry e : toc.values()) {
      if (dirOf(e.path) == null) {
        top.children.add(e);
      }
    }
    toc.put(top.path, top);
  }

  private String readLine() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    for (;;) {
      int c = in.read();
      if (c == '\n') {
        return baos.toString();
      } else if (c == -1) {
        throw new IOException("End of stream");
      } else {
        baos.write(c);
      }
    }
  }

  private static String nameOf(String path) {
    final int s = path.lastIndexOf('/');
    return s < 0 ? path : path.substring(s + 1);
  }

  private static String dirOf(String path) {
    final int s = path.lastIndexOf('/');
    return s < 0 ? null : path.substring(0, s);
  }

  private static byte[] read(String path) {
    final InputStream in =
        ScpCommand.class.getClassLoader().getResourceAsStream(
            "com/google/gerrit/server/ssh/scproot/" + path);
    if (in == null) {
      return null;
    }
    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        final byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf, 0, buf.length)) > 0) {
          out.write(buf, 0, n);
        }
      } finally {
        in.close();
      }
      return out.toByteArray();
    } catch (Exception e) {
      log.debug("Cannot read " + path, e);
      return null;
    }
  }

  private void readFile(final Entry ent) throws IOException {
    final byte[] data = read(ent.path);
    if (data == null) {
      throw new FileNotFoundException(ent.path);
    }

    header(ent, data.length);
    readAck();

    out.write(data);
    ack();
    readAck();
  }

  private void readDir(final Entry dir) throws IOException {
    header(dir, 0);
    readAck();

    for (Entry e : dir.children) {
      if (TYPE_DIR.equals(e.type)) {
        readDir(e);
      } else {
        readFile(e);
      }
    }

    out.write("E\n".getBytes("UTF-8"));
    out.flush();
    readAck();
  }

  private void header(final Entry dir, final int len) throws IOException,
      UnsupportedEncodingException {
    final StringBuilder buf = new StringBuilder();
    buf.append(dir.type);
    buf.append(dir.mode); // perms
    buf.append(" ");
    buf.append(len); // length
    buf.append(" ");
    buf.append(nameOf(dir.path));
    buf.append("\n");
    out.write(buf.toString().getBytes("UTF-8"));
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
        log.debug("Received warning: " + readLine());
        break;
      case 2:
        throw new IOException("Received nack: " + readLine());
    }
  }

  private static class Entry {
    String type;
    String mode;
    String path;
    List<Entry> children;

    Entry(String type, String line) {
      this.type = type;
      int s = line.indexOf(' ');
      mode = line.substring(0, s);
      path = line.substring(s + 1);

      if (!mode.startsWith("0")) {
        mode = "0" + mode;
      }
    }

    Entry(String type, int mode, String path) {
      this.type = type;
      this.mode = "0" + Integer.toOctalString(mode);
      this.path = path;
      this.children = new ArrayList<Entry>();
    }
  }
}
