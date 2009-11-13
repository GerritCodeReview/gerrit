package com.google.gerrit.main;

// Copyright (C) 2009 The Android Open Source Project
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Main class for a JAR file to run code from "WEB-INF/lib". */
public final class GerritLauncher {
  private static final String pkg = "com.google.gerrit.pgm";
  private static final String NOT_ARCHIVED = "NOT_ARCHIVED";

  public static void main(final String argv[]) throws Exception {
    if (argv.length == 0) {
      File me;
      try {
        me = getDistributionArchive();
      } catch (FileNotFoundException e) {
        me = null;
      }

      String jar = me != null ? me.getName() : "gerrit.war";
      System.err.println("Gerrit Code Review " + getVersion(me));
      System.err.println("usage: java -jar " + jar + " command [ARG ...]");
      System.err.println();
      System.err.println("The most commonly used commands are:");
      System.err.println("  init           Initialize a Gerrit installation");
      System.err.println("  daemon         Run the Gerrit network daemons");
      System.err.println("  version        Display the build version number");
      System.err.println();
      System.err.println("  ls             List files available for cat");
      System.err.println("  cat FILE       Display a file from the archive");
      System.err.println();
      System.exit(1);
    }

    if ("-v".equals(argv[0]) || "--version".equals(argv[0])) {
      // Special case, jump into the "Version" command which is
      // compiled somewhere in our library packages.
      //
      argv[0] = "Version";
    }

    final String cmd = argv[0];
    if ("cat".equals(cmd) || "-p".equals(cmd) || "--cat".equals(cmd)) {
      // Copy the contents of a file to System.out
      //
      if (argv.length == 2) {
        cat(argv[1]);
      } else {
        System.err.println("usage: cat FILE");
        System.exit(1);
      }

    } else if ("ls".equals(cmd) || "-l".equals(cmd) || "--ls".equals(cmd)) {
      // List the available files under WEB-INF/
      //
      if (argv.length == 1) {
        ls();
      } else {
        System.err.println("usage: ls");
        System.exit(1);
      }

    } else {
      // Run an arbitrary application class
      //
      final ClassLoader cl = libClassLoader();
      Thread.currentThread().setContextClassLoader(cl);
      runMain(cl, argv);
    }
  }

  private static String getVersion(final File me) {
    if (me == null) {
      return "";
    }

    try {
      final JarFile jar = new JarFile(me);
      try {
        Manifest mf = jar.getManifest();
        Attributes att = mf.getMainAttributes();
        String val = att.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        return val != null ? val : "";
      } finally {
        jar.close();
      }
    } catch (IOException e) {
      return "";
    }
  }

  private static void cat(String fileName) throws IOException {
    while (fileName.startsWith("/")) {
      fileName = fileName.substring(1);
    }

    String name;
    if (fileName.equals("LICENSES.txt")) {
      name = fileName;
    } else {
      name = "WEB-INF/" + fileName;
    }

    final InputStream in = GerritLauncher.class.getResourceAsStream(name);
    if (in == null) {
      System.err.println("error: no such file " + fileName);
      System.exit(1);
    }

    try {
      try {
        final byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
          System.out.write(buf, 0, n);
        }
      } finally {
        System.out.flush();
      }
    } finally {
      in.close();
    }
  }

  private static void ls() throws IOException {
    final ZipFile zf = new ZipFile(getDistributionArchive());
    try {
      final Enumeration<? extends ZipEntry> e = zf.entries();
      while (e.hasMoreElements()) {
        final ZipEntry ze = e.nextElement();
        String name = ze.getName();
        boolean show = false;
        show |= name.startsWith("WEB-INF/");
        show |= name.equals("LICENSES.txt");

        show &= !ze.isDirectory();
        show &= !name.startsWith("WEB-INF/classes/");
        show &= !name.startsWith("WEB-INF/lib/");
        show &= !name.equals("WEB-INF/web.xml");
        if (show) {
          if (name.startsWith("WEB-INF/")) {
            name = name.substring("WEB-INF/".length());
          }
          System.out.println(name);
        }
      }
    } finally {
      zf.close();
    }
  }

  private static void runMain(final ClassLoader loader, final String[] origArgv)
      throws Exception {
    String name = origArgv[0];
    final String[] argv = new String[origArgv.length - 1];
    System.arraycopy(origArgv, 1, argv, 0, argv.length);

    Class<?> clazz;
    try {
      try {
        clazz = Class.forName(pkg + "." + name, true, loader);
      } catch (ClassNotFoundException cnfe) {
        if (name.equals(name.toLowerCase())) {
          String first = name.substring(0, 1).toUpperCase();
          String cn = first + name.substring(1);
          clazz = Class.forName(pkg + "." + cn, true, loader);
        } else {
          throw cnfe;
        }
      }
    } catch (ClassNotFoundException cnfe) {
      System.err.println("fatal: unknown command " + name);
      System.err.println("      (no " + pkg + "." + name + ")");
      System.exit(1);
      return;
    }

    final Method main;
    try {
      main = clazz.getMethod("main", argv.getClass());
    } catch (SecurityException e) {
      System.err.println("fatal: unknown command " + name);
      System.exit(1);
      return;
    } catch (NoSuchMethodException e) {
      System.err.println("fatal: unknown command " + name);
      System.exit(1);
      return;
    }

    final Object res;
    if ((main.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
      res = main.invoke(null, new Object[] {argv});
    } else {
      res = main.invoke(clazz.newInstance(), new Object[] {argv});
    }
    if (res instanceof Number) {
      System.exit(((Number) res).intValue());
    } else {
      System.exit(0);
    }
  }

  private static ClassLoader libClassLoader() throws IOException {
    final File path;
    try {
      path = getDistributionArchive();
    } catch (FileNotFoundException e) {
      if (NOT_ARCHIVED == e.getMessage()) {
        // Assume the CLASSPATH was made complete by the calling process,
        // as we are likely being run from within a developer's IDE.
        //
        return GerritLauncher.class.getClassLoader();
      }
      throw e;
    }

    final ArrayList<URL> jars = new ArrayList<URL>();
    try {
      final ZipFile zf = new ZipFile(path);
      try {
        final Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
          final ZipEntry ze = e.nextElement();
          if (ze.isDirectory()) {
            continue;
          }

          if (ze.getName().startsWith("WEB-INF/lib/")) {
            final File tmp = createTempFile(safeName(ze), ".jar");
            final FileOutputStream out = new FileOutputStream(tmp);
            try {
              final InputStream in = zf.getInputStream(ze);
              try {
                final byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf, 0, buf.length)) > 0) {
                  out.write(buf, 0, n);
                }
              } finally {
                in.close();
              }
            } finally {
              out.close();
            }
            jars.add(tmp.toURI().toURL());
          }
        }
      } finally {
        zf.close();
      }
    } catch (IOException e) {
      throw new IOException("Cannot obtain libraries from " + path, e);
    }

    if (jars.isEmpty()) {
      return GerritLauncher.class.getClassLoader();
    }
    return new URLClassLoader(jars.toArray(new URL[jars.size()]));
  }

  private static String safeName(final ZipEntry ze) {
    // Try to derive the name of the temporary file so it
    // doesn't completely suck. Best if we can make it
    // match the name it was in the archive.
    //
    String name = ze.getName();
    if (0 <= name.lastIndexOf('/')) {
      name = name.substring(name.lastIndexOf('/') + 1);
    }
    if (0 <= name.lastIndexOf('.')) {
      name = name.substring(0, name.lastIndexOf('.'));
    }
    if (name.isEmpty()) {
      name = "code";
    }
    return name;
  }

  private static File myArchive;

  /**
   * Locate the JAR/WAR file we were launched from.
   *
   * @return local path of the Gerrit WAR file.
   * @throws FileNotFoundException if the code cannot guess the location.
   */
  public static File getDistributionArchive() throws FileNotFoundException {
    if (myArchive == null) {
      myArchive = locateMyArchive();
    }
    return myArchive;
  }

  private static File locateMyArchive() throws FileNotFoundException {
    final ClassLoader myCL = GerritLauncher.class.getClassLoader();
    final String myName =
        GerritLauncher.class.getName().replace('.', '/') + ".class";

    final URL myClazz = myCL.getResource(myName);
    if (myClazz == null) {
      throw new FileNotFoundException("Cannot find JAR: no " + myName);
    }

    // ZipFile may have the path of our JAR hiding within itself.
    //
    try {
      Field nameField = ZipFile.class.getDeclaredField("name");
      nameField.setAccessible(true);

      JarFile jar = ((JarURLConnection) myClazz.openConnection()).getJarFile();
      File path = new File((String) nameField.get(jar));
      if (path.isFile()) {
        return path;
      }
    } catch (Exception e) {
      // Nope, that didn't work. Try a different method.
      //
    }

    // Maybe this is a local class file, running under a debugger?
    //
    if ("file".equals(myClazz.getProtocol())) {
      final File path = new File(myClazz.getPath());
      if (path.isFile() && path.getParentFile().isDirectory()) {
        throw new FileNotFoundException(NOT_ARCHIVED);
      }
    }

    // The CodeSource might be able to give us the source as a stream.
    // If so, copy it to a local file so we have random access to it.
    //
    final CodeSource src =
        GerritLauncher.class.getProtectionDomain().getCodeSource();
    if (src != null) {
      try {
        final InputStream in = src.getLocation().openStream();
        try {
          final File tmp = createTempFile("gerrit_", ".zip");
          final FileOutputStream out = new FileOutputStream(tmp);
          try {
            final byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf, 0, buf.length)) > 0) {
              out.write(buf, 0, n);
            }
          } finally {
            out.close();
          }
          return tmp;
        } finally {
          in.close();
        }
      } catch (IOException e) {
        // Nope, that didn't work.
        //
      }
    }

    throw new FileNotFoundException("Cannot find local copy of JAR");
  }

  private static boolean temporaryDirectoryFound;
  private static File temporaryDirectory;

  private static File createTempFile(String prefix, String suffix)
      throws IOException {
    if (!temporaryDirectoryFound) {
      final File d = File.createTempFile("gerrit_", "_app");
      if (d.delete() && d.mkdir()) {
        // Try to lock the directory down to be accessible by us.
        // We first have to remove all permissions, then add back
        // only the owner permissions.
        //
        d.setWritable(false, false /* all */);
        d.setReadable(false, false /* all */);
        d.setExecutable(false, false /* all */);

        d.setWritable(true, true /* owner only */);
        d.setReadable(true, true /* owner only */);
        d.setExecutable(true, true /* owner only */);

        d.deleteOnExit();
        temporaryDirectory = d;
      }
      temporaryDirectoryFound = true;
    }

    if (temporaryDirectory != null) {
      // If we have a private directory and this name has not yet
      // been used within the private directory, create it as-is.
      //
      final File tmp = new File(temporaryDirectory, prefix + suffix);
      if (tmp.createNewFile()) {
        tmp.deleteOnExit();
        return tmp;
      }
    }

    if (!prefix.endsWith("_")) {
      prefix += "_";
    }

    final File tmp = File.createTempFile(prefix, suffix, temporaryDirectory);
    tmp.deleteOnExit();
    return tmp;
  }

  private GerritLauncher() {
  }
}
