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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.compiler.Compiler;

public class BuckPrologCompiler {
  public static void main(String[] argv) throws IOException, CompileException {
    List<File> srcs = new ArrayList<File>();
    List<File> jars = new ArrayList<File>();
    for (int i = 0; i < argv.length - 1; i++) {
      String s = argv[i];
      if (s.endsWith(".pl")) {
        srcs.add(new File(s));
      } else if (s.endsWith(".jar")) {
        jars.add(new File(s));
      }
    }

    File out = new File(argv[argv.length - 1]);
    File java = tmpdir("java");
    File classes = tmpdir("classes");
    for (File src : srcs) {
      new Compiler().prologToJavaSource(src.getPath(), java.getPath());
    }
    javac(jars, java, classes);
    jar(out, classes);
  }

  private static File tmpdir(String name) throws IOException {
    File d = File.createTempFile(name + "_", "");
    if (!d.delete() || !d.mkdir()) {
      throw new IOException("Cannot mkdir " + d);
    }
    return d;
  }

  private static void javac(List<File> cp, File java, File classes)
      throws IOException, CompileException {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    if (javac == null) {
      throw new CompileException("JDK required (running inside of JRE)");
    }

    DiagnosticCollector<JavaFileObject> d =
        new DiagnosticCollector<JavaFileObject>();
    StandardJavaFileManager fm = javac.getStandardFileManager(d, null, null);
    try {
      StringBuilder classpath = new StringBuilder();
      for (File jar : cp) {
        if (classpath.length() > 0) {
          classpath.append(File.pathSeparatorChar);
        }
        classpath.append(jar.getPath());
      }
      ArrayList<String> args = new ArrayList<String>();
      args.add("-g:none");
      args.add("-nowarn");
      if (classpath.length() > 0) {
        args.add("-classpath");
        args.add(classpath.toString());
      }
      args.add("-d");
      args.add(classes.getPath());
      if (!javac.getTask(null, fm, d, args, null,
          fm.getJavaFileObjectsFromFiles(find(java, ".java"))).call()) {
        StringBuilder msg = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> err : d.getDiagnostics()) {
          msg.append('\n').append(err.getKind()).append(": ");
          if (err.getSource() != null) {
            msg.append(err.getSource().getName());
          }
          msg.append(':').append(err.getLineNumber()).append(": ");
          msg.append(err.getMessage(Locale.getDefault()));
        }
        throw new CompileException(msg.toString());
      }
    } finally {
      fm.close();
    }
  }

  private static void jar(File jar, File classes) throws IOException {
    File tmp = File.createTempFile("prolog", ".jar", jar.getParentFile());
    try {
      JarOutputStream out = new JarOutputStream(new FileOutputStream(tmp));
      try {
        out.setLevel(9);
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

  private static List<File> find(File dir, String extension) {
    ArrayList<File> list = new ArrayList<File>();
    for (File f : dir.listFiles()) {
      if (f.getName().endsWith(extension)) {
        list.add(f);
      } else if (f.isDirectory()) {
        list.addAll(find(f, extension));
      }
    }
    return list;
  }
}
