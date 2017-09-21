// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.tools;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.Version;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listing of all client side tools stored on this server.
 *
 * <p>Clients may download these tools through our file server, as they are packaged with our own
 * software releases.
 */
@Singleton
public class ToolsCatalog {
  private static final Logger log = LoggerFactory.getLogger(ToolsCatalog.class);

  private final SortedMap<String, Entry> toc;

  @Inject
  ToolsCatalog() throws IOException {
    this.toc = readToc();
  }

  /**
   * Lookup an entry in the tools catalog.
   *
   * @param name path of the item, relative to the root of the catalog.
   * @return the entry; null if the item is not part of the catalog.
   */
  @Nullable
  public Entry get(@Nullable String name) {
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }
    if (name.startsWith("/")) {
      name = name.substring(1);
    }
    if (name.endsWith("/")) {
      name = name.substring(0, name.length() - 1);
    }
    return toc.get(name);
  }

  private static SortedMap<String, Entry> readToc() throws IOException {
    SortedMap<String, Entry> toc = new TreeMap<>();
    final BufferedReader br =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(read("TOC")), UTF_8));
    String line;
    while ((line = br.readLine()) != null) {
      if (line.length() > 0 && !line.startsWith("#")) {
        final Entry e = new Entry(Entry.Type.FILE, line);
        toc.put(e.getPath(), e);
      }
    }

    final List<Entry> all = new ArrayList<>(toc.values());
    for (Entry e : all) {
      String path = dirOf(e.getPath());
      while (path != null) {
        Entry d = toc.get(path);
        if (d == null) {
          d = new Entry(Entry.Type.DIR, 0755, path);
          toc.put(d.getPath(), d);
        }
        d.children.add(e);
        path = dirOf(path);
        e = d;
      }
    }

    final Entry top = new Entry(Entry.Type.DIR, 0755, "");
    for (Entry e : toc.values()) {
      if (dirOf(e.getPath()) == null) {
        top.children.add(e);
      }
    }
    toc.put(top.getPath(), top);

    return Collections.unmodifiableSortedMap(toc);
  }

  @Nullable
  private static byte[] read(String path) {
    String name = "root/" + path;
    try (InputStream in = ToolsCatalog.class.getResourceAsStream(name)) {
      if (in == null) {
        return null;
      }
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf, 0, buf.length)) > 0) {
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    } catch (Exception e) {
      log.debug("Cannot read " + path, e);
      return null;
    }
  }

  @Nullable
  private static String dirOf(String path) {
    final int s = path.lastIndexOf('/');
    return s < 0 ? null : path.substring(0, s);
  }

  /** A file served out of the tools root directory. */
  public static class Entry {
    public enum Type {
      DIR,
      FILE
    }

    private final Type type;
    private final int mode;
    private final String path;
    private final List<Entry> children;

    Entry(Type type, String line) {
      int s = line.indexOf(' ');
      String mode = line.substring(0, s);
      String path = line.substring(s + 1);

      this.type = type;
      this.mode = Integer.parseInt(mode, 8);
      this.path = path;
      if (type == Type.FILE) {
        this.children = Collections.emptyList();
      } else {
        this.children = new ArrayList<>();
      }
    }

    Entry(Type type, int mode, String path) {
      this.type = type;
      this.mode = mode;
      this.path = path;
      this.children = new ArrayList<>();
    }

    public Type getType() {
      return type;
    }

    /** @return the preferred UNIX file mode, e.g. {@code 0755}. */
    public int getMode() {
      return mode;
    }

    /** @return path of the entry, relative to the catalog root. */
    public String getPath() {
      return path;
    }

    /** @return name of the entry, within its parent directory. */
    public String getName() {
      final int s = path.lastIndexOf('/');
      return s < 0 ? path : path.substring(s + 1);
    }

    /** @return collection of entries below this one, if this is a directory. */
    public List<Entry> getChildren() {
      return Collections.unmodifiableList(children);
    }

    /** @return a copy of the file's contents. */
    public byte[] getBytes() {
      byte[] data = read(getPath());

      if (isScript(data)) {
        // Embed Gerrit's version number into the top of the script.
        //
        final String version = Version.getVersion();
        final int lf = RawParseUtils.nextLF(data, 0);
        if (version != null && lf < data.length) {
          byte[] versionHeader = Constants.encode("# From Gerrit Code Review " + version + "\n");

          ByteArrayOutputStream buf = new ByteArrayOutputStream();
          buf.write(data, 0, lf);
          buf.write(versionHeader, 0, versionHeader.length);
          buf.write(data, lf, data.length - lf);
          data = buf.toByteArray();
        }
      }

      return data;
    }

    private boolean isScript(byte[] data) {
      return data != null
          && data.length > 3 //
          && data[0] == '#' //
          && data[1] == '!' //
          && data[2] == '/';
    }
  }
}
