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

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.compiler.Compiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class BuckPrologCompiler {
  public static void main(String[] argv) throws IOException, CompileException {
    File out = new File(argv[argv.length - 1]);
    File java = tmpdir("java");
    for (int i = 0; i < argv.length - 1; i++) {
      File src = new File(argv[i]);
      new Compiler().prologToJavaSource(src.getPath(), java.getPath());
    }
    jar(out, java);
  }

  private static File tmpdir(String name) throws IOException {
    File d = File.createTempFile(name + "_", "");
    if (!d.delete() || !d.mkdir()) {
      throw new IOException("Cannot mkdir " + d);
    }
    return d;
  }

  private static void jar(File jar, File classes) throws IOException {
    File tmp = File.createTempFile("prolog", ".jar", jar.getParentFile());
    try {
      JarOutputStream out = new JarOutputStream(new FileOutputStream(tmp));
      try {
        add(out, classes, "");
      } finally {
        out.close();
      }
      if (!tmp.renameTo(jar)) {
        throw new IOException("Cannot create " + jar);
      }
    } finally {
      tmp.delete();
    }
  }

  private static void add(JarOutputStream out, File classes, String prefix)
      throws IOException {
    for (String name : classes.list()) {
      File f = new File(classes, name);
      if (f.isDirectory()) {
        add(out, f, prefix + name + "/");
        continue;
      }

      JarEntry e = new JarEntry(prefix + name);
      FileInputStream in = new FileInputStream(f);
      try {
        e.setTime(f.lastModified());
        out.putNextEntry(e);
        byte[] buf = new byte[16 << 10];
        int n;
        while (0 < (n = in.read(buf))) {
          out.write(buf, 0, n);
        }
      } finally {
        in.close();
        out.closeEntry();
      }
    }
  }
}
