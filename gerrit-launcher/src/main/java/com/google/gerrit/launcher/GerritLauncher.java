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

package com.google.gerrit.launcher;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Main class for a JAR file to run code from "WEB-INF/lib". */
public final class GerritLauncher {
  private static final String pkg = "com.google.gerrit.pgm";
  public static final String NOT_ARCHIVED = "NOT_ARCHIVED";

  private static ClassLoader daemonClassLoader;

  public static void main(final String[] argv) throws Exception {
    System.exit(mainImpl(argv));
  }

  public static int mainImpl(final String[] argv) throws Exception {
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
      System.err.println("  init            Initialize a Gerrit installation");
      System.err.println("  reindex         Rebuild the secondary index");
      System.err.println("  daemon          Run the Gerrit network daemons");
      System.err.println("  gsql            Run the interactive query console");
      System.err.println("  version         Display the build version number");
      System.err.println();
      System.err.println("  ls              List files available for cat");
      System.err.println("  cat FILE        Display a file from the archive");
      System.err.println();
      return 1;
    }

    // Special cases, a few global options actually are programs.
    //
    if ("-v".equals(argv[0]) || "--version".equals(argv[0])) {
      argv[0] = "version";
    } else if ("-p".equals(argv[0]) || "--cat".equals(argv[0])) {
      argv[0] = "cat";
    } else if ("-l".equals(argv[0]) || "--ls".equals(argv[0])) {
      argv[0] = "ls";
    }

    // Run the application class
    //
    final ClassLoader cl = libClassLoader(isProlog(programClassName(argv[0])));
    Thread.currentThread().setContextClassLoader(cl);
    return invokeProgram(cl, argv);
  }

  public static void daemonStart(final String[] argv) throws Exception {
    if (daemonClassLoader != null) {
      throw new IllegalStateException("daemonStart can be called only once per JVM instance");
    }
    final ClassLoader cl = libClassLoader(false);
    Thread.currentThread().setContextClassLoader(cl);

    daemonClassLoader = cl;

    String[] daemonArgv = new String[argv.length + 1];
    daemonArgv[0] = "daemon";
    for (int i = 0; i < argv.length; i++) {
      daemonArgv[i + 1] = argv[i];
    }
    int res = invokeProgram(cl, daemonArgv);
    if (res != 0) {
      throw new Exception("Unexpected return value: " + res);
    }
  }

  public static void daemonStop(final String[] argv) throws Exception {
    if (daemonClassLoader == null) {
      throw new IllegalStateException("daemonStop can be called only after call to daemonStop");
    }
    String[] daemonArgv = new String[argv.length + 2];
    daemonArgv[0] = "daemon";
    daemonArgv[1] = "--stop-only";
    for (int i = 0; i < argv.length; i++) {
      daemonArgv[i + 2] = argv[i];
    }
    int res = invokeProgram(daemonClassLoader, daemonArgv);
    if (res != 0) {
      throw new Exception("Unexpected return value: " + res);
    }
  }

  private static boolean isProlog(String cn) {
    return "PrologShell".equals(cn) || "Rulec".equals(cn);
  }

  private static String getVersion(final File me) {
    if (me == null) {
      return "";
    }

    try (JarFile jar = new JarFile(me)) {
      Manifest mf = jar.getManifest();
      Attributes att = mf.getMainAttributes();
      String val = att.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
      return val != null ? val : "";
    } catch (IOException e) {
      return "";
    }
  }

  private static int invokeProgram(final ClassLoader loader, final String[] origArgv)
      throws Exception {
    String name = origArgv[0];
    final String[] argv = new String[origArgv.length - 1];
    System.arraycopy(origArgv, 1, argv, 0, argv.length);

    Class<?> clazz;
    try {
      try {
        String cn = programClassName(name);
        clazz = Class.forName(pkg + "." + cn, true, loader);
      } catch (ClassNotFoundException cnfe) {
        if (name.equals(name.toLowerCase())) {
          clazz = Class.forName(pkg + "." + name, true, loader);
        } else {
          throw cnfe;
        }
      }
    } catch (ClassNotFoundException cnfe) {
      System.err.println("fatal: unknown command " + name);
      System.err.println("      (no " + pkg + "." + name + ")");
      return 1;
    }

    final Method main;
    try {
      main = clazz.getMethod("main", argv.getClass());
    } catch (SecurityException | NoSuchMethodException e) {
      System.err.println("fatal: unknown command " + name);
      return 1;
    }

    final Object res;
    try {
      if ((main.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
        res = main.invoke(null, new Object[] {argv});
      } else {
        res = main.invoke(clazz.newInstance(), new Object[] {argv});
      }
    } catch (InvocationTargetException ite) {
      if (ite.getCause() instanceof Exception) {
        throw (Exception) ite.getCause();
      } else if (ite.getCause() instanceof Error) {
        throw (Error) ite.getCause();
      } else {
        throw ite;
      }
    }
    if (res instanceof Number) {
      return ((Number) res).intValue();
    }
    return 0;
  }

  private static String programClassName(String cn) {
    if (cn.equals(cn.toLowerCase())) {
      StringBuilder buf = new StringBuilder();
      buf.append(Character.toUpperCase(cn.charAt(0)));
      for (int i = 1; i < cn.length(); i++) {
        if (cn.charAt(i) == '-' && i + 1 < cn.length()) {
          i++;
          buf.append(Character.toUpperCase(cn.charAt(i)));
        } else {
          buf.append(cn.charAt(i));
        }
      }
      return buf.toString();
    }
    return cn;
  }

  private static ClassLoader libClassLoader(boolean prologCompiler) throws IOException {
    final File path;
    try {
      path = getDistributionArchive();
    } catch (FileNotFoundException e) {
      if (NOT_ARCHIVED.equals(e.getMessage())) {
        return useDevClasspath();
      }
      throw e;
    }

    final SortedMap<String, URL> jars = new TreeMap<>();
    try (ZipFile zf = new ZipFile(path)) {
      final Enumeration<? extends ZipEntry> e = zf.entries();
      while (e.hasMoreElements()) {
        final ZipEntry ze = e.nextElement();
        if (ze.isDirectory()) {
          continue;
        }

        String name = ze.getName();
        if (name.startsWith("WEB-INF/lib/")) {
          extractJar(zf, ze, jars);
        } else if (name.startsWith("WEB-INF/pgm-lib/")) {
          // Some Prolog tools are restricted.
          if (prologCompiler || !name.startsWith("WEB-INF/pgm-lib/prolog-")) {
            extractJar(zf, ze, jars);
          }
        }
      }
    } catch (IOException e) {
      throw new IOException("Cannot obtain libraries from " + path, e);
    }

    if (jars.isEmpty()) {
      return GerritLauncher.class.getClassLoader();
    }

    // The extension API needs to be its own ClassLoader, along
    // with a few of its dependencies. Try to construct this first.
    List<URL> extapi = new ArrayList<>();
    move(jars, "gerrit-extension-api-", extapi);
    move(jars, "guice-", extapi);
    move(jars, "javax.inject-1.jar", extapi);
    move(jars, "aopalliance-1.0.jar", extapi);
    move(jars, "guice-servlet-", extapi);
    move(jars, "tomcat-servlet-api-", extapi);

    ClassLoader parent = ClassLoader.getSystemClassLoader();
    if (!extapi.isEmpty()) {
      parent = new URLClassLoader(extapi.toArray(new URL[extapi.size()]), parent);
    }
    return new URLClassLoader(jars.values().toArray(new URL[jars.size()]), parent);
  }

  private static void extractJar(ZipFile zf, ZipEntry ze, SortedMap<String, URL> jars)
      throws IOException {
    File tmp = createTempFile(safeName(ze), ".jar");
    try (FileOutputStream out = new FileOutputStream(tmp);
        InputStream in = zf.getInputStream(ze)) {
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf, 0, buf.length)) > 0) {
        out.write(buf, 0, n);
      }
    }

    String name = ze.getName();
    jars.put(name.substring(name.lastIndexOf('/'), name.length()), tmp.toURI().toURL());
  }

  private static void move(SortedMap<String, URL> jars, String prefix, List<URL> extapi) {
    SortedMap<String, URL> matches = jars.tailMap(prefix);
    if (!matches.isEmpty()) {
      String first = matches.firstKey();
      if (first.startsWith(prefix)) {
        extapi.add(jars.remove(first));
      }
    }
  }

  private static String safeName(final ZipEntry ze) {
    // Try to derive the name of the temporary file so it
    // doesn't completely suck. Best if we can make it
    // match the name it was in the archive.
    //
    String name = ze.getName();
    if (name.contains("/")) {
      name = name.substring(name.lastIndexOf('/') + 1);
    }
    if (name.contains(".")) {
      name = name.substring(0, name.lastIndexOf('.'));
    }
    if (name.isEmpty()) {
      name = "code";
    }
    return name;
  }

  private static volatile File myArchive;
  private static volatile File myHome;

  private static final Map<Path, FileSystem> zipFileSystems = new HashMap<>();

  /**
   * Locate the JAR/WAR file we were launched from.
   *
   * @return local path of the Gerrit WAR file.
   * @throws FileNotFoundException if the code cannot guess the location.
   */
  public static File getDistributionArchive() throws FileNotFoundException, IOException {
    File result = myArchive;
    if (result == null) {
      synchronized (GerritLauncher.class) {
        result = myArchive;
        if (result != null) {
          return result;
        }
        result = locateMyArchive();
        myArchive = result;
      }
    }
    return result;
  }

  public static synchronized FileSystem getZipFileSystem(Path zip) throws IOException {
    // FileSystems canonicalizes the path, so we should too.
    zip = zip.toRealPath();
    FileSystem zipFs = zipFileSystems.get(zip);
    if (zipFs == null) {
      zipFs = newZipFileSystem(zip);
      zipFileSystems.put(zip, zipFs);
    }
    return zipFs;
  }

  public static FileSystem newZipFileSystem(Path zip) throws IOException {
    return FileSystems.newFileSystem(
        URI.create("jar:" + zip.toUri()), Collections.<String, String>emptyMap());
  }

  private static File locateMyArchive() throws FileNotFoundException {
    final ClassLoader myCL = GerritLauncher.class.getClassLoader();
    final String myName = GerritLauncher.class.getName().replace('.', '/') + ".class";

    final URL myClazz = myCL.getResource(myName);
    if (myClazz == null) {
      throw new FileNotFoundException("Cannot find JAR: no " + myName);
    }

    // ZipFile may have the path of our JAR hiding within itself.
    //
    try {
      JarFile jar = ((JarURLConnection) myClazz.openConnection()).getJarFile();
      File path = new File(jar.getName());
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
    final CodeSource src = GerritLauncher.class.getProtectionDomain().getCodeSource();
    if (src != null) {
      try (InputStream in = src.getLocation().openStream()) {
        final File tmp = createTempFile("gerrit_", ".zip");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
          final byte[] buf = new byte[4096];
          int n;
          while ((n = in.read(buf, 0, buf.length)) > 0) {
            out.write(buf, 0, n);
          }
        }
        return tmp;
      } catch (IOException e) {
        // Nope, that didn't work.
        //
      }
    }

    throw new FileNotFoundException("Cannot find local copy of JAR");
  }

  private static boolean temporaryDirectoryFound;
  private static File temporaryDirectory;

  /**
   * Creates a temporary file within the application's unpack location.
   *
   * <p>The launcher unpacks the nested JAR files into a temporary directory, allowing the classes
   * to be loaded from local disk with standard Java APIs. This method constructs a new temporary
   * file in the same directory.
   *
   * <p>The method first tries to create {@code prefix + suffix} within the directory under the
   * assumption that a given {@code prefix + suffix} combination is made at most once per JVM
   * execution. If this fails (e.g. the named file already exists) a mangled unique name is used and
   * returned instead, with the unique string appearing between the prefix and suffix.
   *
   * <p>Files created by this method will be automatically deleted by the JVM when it terminates. If
   * the returned file is converted into a directory by the caller, the caller must arrange for the
   * contents to be deleted before the directory is.
   *
   * <p>If supported by the underlying operating system, the temporary directory which contains
   * these temporary files is accessible only by the user running the JVM.
   *
   * @param prefix prefix of the file name.
   * @param suffix suffix of the file name.
   * @return the path of the temporary file. The returned object exists in the filesystem as a file;
   *     caller may need to delete and recreate as a directory if a directory was preferred.
   * @throws IOException the file could not be created.
   */
  public static synchronized File createTempFile(String prefix, String suffix) throws IOException {
    if (!temporaryDirectoryFound) {
      final File d = File.createTempFile("gerrit_", "_app", tmproot());
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

  /**
   * Provide path to a working directory
   *
   * @return local path of the working directory or null if cannot be determined
   */
  public static File getHomeDirectory() {
    if (myHome == null) {
      myHome = locateHomeDirectory();
    }
    return myHome;
  }

  private static File tmproot() {
    File tmp;
    String gerritTemp = System.getenv("GERRIT_TMP");
    if (gerritTemp != null && gerritTemp.length() > 0) {
      tmp = new File(gerritTemp);
    } else {
      tmp = new File(getHomeDirectory(), "tmp");
    }
    if (!tmp.exists() && !tmp.mkdirs()) {
      System.err.println("warning: cannot create " + tmp.getAbsolutePath());
      System.err.println("warning: using system temporary directory instead");
      return null;
    }

    // Try to clean up any stale empty directories. Assume any empty
    // directory that is older than 7 days is one of these dead ones
    // that we can clean up.
    //
    final File[] tmpEntries = tmp.listFiles();
    if (tmpEntries != null) {
      final long now = System.currentTimeMillis();
      final long expired = now - MILLISECONDS.convert(7, DAYS);
      for (final File tmpEntry : tmpEntries) {
        if (tmpEntry.isDirectory() && tmpEntry.lastModified() < expired) {
          final String[] all = tmpEntry.list();
          if (all == null || all.length == 0) {
            tmpEntry.delete();
          }
        }
      }
    }

    try {
      return tmp.getCanonicalFile();
    } catch (IOException e) {
      return tmp;
    }
  }

  private static File locateHomeDirectory() {
    // Try to find the user's home directory. If we can't find it
    // return null so the JVM's default temporary directory is used
    // instead. This is probably /tmp or /var/tmp.
    //
    String userHome = System.getProperty("user.home");
    if (userHome == null || "".equals(userHome)) {
      userHome = System.getenv("HOME");
      if (userHome == null || "".equals(userHome)) {
        System.err.println("warning: cannot determine home directory");
        System.err.println("warning: using system temporary directory instead");
        return null;
      }
    }

    // Ensure the home directory exists. If it doesn't, try to make it.
    //
    final File home = new File(userHome);
    if (!home.exists()) {
      if (home.mkdirs()) {
        System.err.println("warning: created " + home.getAbsolutePath());
      } else {
        System.err.println("warning: " + home.getAbsolutePath() + " not found");
        System.err.println("warning: using system temporary directory instead");
        return null;
      }
    }

    // Use $HOME/.gerritcodereview/tmp for our temporary file area.
    //
    final File gerrithome = new File(home, ".gerritcodereview");
    if (!gerrithome.exists() && !gerrithome.mkdirs()) {
      System.err.println("warning: cannot create " + gerrithome.getAbsolutePath());
      System.err.println("warning: using system temporary directory instead");
      return null;
    }
    try {
      return gerrithome.getCanonicalFile();
    } catch (IOException e) {
      return gerrithome;
    }
  }

  /**
   * Locate the path of the {@code eclipse-out} directory in a source tree.
   *
   * @throws FileNotFoundException if the directory cannot be found.
   */
  public static Path getDeveloperEclipseOut() throws FileNotFoundException {
    return resolveInSourceRoot("eclipse-out");
  }

  /**
   * Locate the path of the {@code buck-out} directory in a source tree.
   *
   * @throws FileNotFoundException if the directory cannot be found.
   */
  public static Path getDeveloperBuckOut() throws FileNotFoundException {
    return resolveInSourceRoot("buck-out");
  }

  private static Path resolveInSourceRoot(String name) throws FileNotFoundException {
    // Find ourselves in the classpath, as a loose class file or jar.
    Class<GerritLauncher> self = GerritLauncher.class;
    URL u = self.getResource(self.getSimpleName() + ".class");
    if (u == null) {
      throw new FileNotFoundException("Cannot find class " + self.getName());
    } else if ("jar".equals(u.getProtocol())) {
      String p = u.getPath();
      try {
        u = new URL(p.substring(0, p.indexOf('!')));
      } catch (MalformedURLException e) {
        FileNotFoundException fnfe = new FileNotFoundException("Not a valid jar file: " + u);
        fnfe.initCause(e);
        throw fnfe;
      }
    }
    if (!"file".equals(u.getProtocol())) {
      throw new FileNotFoundException("Cannot extract path from " + u);
    }

    // Pop up to the top-level source folder by looking for .buckconfig.
    Path dir = Paths.get(u.getPath());
    while (!Files.isRegularFile(dir.resolve(".buckconfig"))) {
      Path parent = dir.getParent();
      if (parent == null) {
        throw new FileNotFoundException("Cannot find source root from " + u);
      }
      dir = parent;
    }

    Path ret = dir.resolve(name);
    if (!Files.exists(ret)) {
      throw new FileNotFoundException(name + " not found in source root " + dir);
    }
    return ret;
  }

  private static ClassLoader useDevClasspath() throws MalformedURLException, FileNotFoundException {
    Path out = getDeveloperEclipseOut();
    List<URL> dirs = new ArrayList<>();
    dirs.add(out.resolve("classes").toUri().toURL());
    ClassLoader cl = GerritLauncher.class.getClassLoader();
    for (URL u : ((URLClassLoader) cl).getURLs()) {
      if (includeJar(u)) {
        dirs.add(u);
      }
    }
    return new URLClassLoader(
        dirs.toArray(new URL[dirs.size()]), ClassLoader.getSystemClassLoader().getParent());
  }

  private static boolean includeJar(URL u) {
    String path = u.getPath();
    return path.endsWith(".jar")
        && !path.endsWith("-src.jar")
        && !path.contains("/buck-out/gen/lib/gwt/");
  }

  private GerritLauncher() {}
}
