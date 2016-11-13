// Copyright (C) 2013 The Android Open Source Project
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

import com.googlecode.prolog_cafe.compiler.Compiler;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class BuckPrologCompiler {
  private static File tmpdir;

  public static void main(String[] argv) throws IOException, CompileException {
    int i = 0;
    tmpdir = new File(argv[i++]);
    File out = new File(argv[i++]);
    File java = tmpdir("java");
    for (; i < argv.length; i++) {
      new Compiler().prologToJavaSource(argv[i], java.getPath());
    }
    jar(out, java);
  }

  private static File tmpdir(String name) throws IOException {
    File d = File.createTempFile(name + "_", "", tmpdir);
    if (!d.delete() || !d.mkdir()) {
      throw new IOException("Cannot mkdir " + d);
    }
    return d;
  }

  private static void jar(File jar, File classes) throws IOException {
    File tmp = File.createTempFile("prolog", ".jar", tmpdir);
    try {
      try (JarOutputStream out = new JarOutputStream(new FileOutputStream(tmp))) {
        add(out, classes, "");
      }
      if (!tmp.renameTo(jar)) {
        throw new IOException("Cannot create " + jar);
      }
    } finally {
      tmp.delete();
    }
  }

  private static void add(JarOutputStream out, File classes, String prefix) throws IOException {
    String[] list = classes.list();
    if (list == null) {
      return;
    }
    for (String name : list) {
      File f = new File(classes, name);
      if (f.isDirectory()) {
        add(out, f, prefix + name + "/");
        continue;
      }

      JarEntry e = new JarEntry(prefix + name);
      try (FileInputStream in = new FileInputStream(f)) {
        e.setTime(f.lastModified());
        out.putNextEntry(e);
        byte[] buf = new byte[16 << 10];
        int n;
        while (0 < (n = in.read(buf))) {
          out.write(buf, 0, n);
        }
      } finally {
        out.closeEntry();
      }
    }
  }
}
