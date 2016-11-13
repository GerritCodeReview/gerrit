// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.pgm.rules;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.Version;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlecode.prolog_cafe.compiler.Compiler;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Helper class for Rulec: does the actual prolog -> java src -> class -> jar work Finds rules.pl in
 * refs/meta/config branch Creates rules-(sha1 of rules.pl).jar in (site-path)/cache/rules
 */
public class PrologCompiler implements Callable<PrologCompiler.Status> {
  public interface Factory {
    PrologCompiler create(Repository git);
  }

  public enum Status {
    NO_RULES,
    COMPILED
  }

  private final Path ruleDir;
  private final Repository git;

  @Inject
  PrologCompiler(
      @GerritServerConfig Config config, SitePaths site, @Assisted Repository gitRepository) {
    Path cacheDir = site.resolve(config.getString("cache", null, "directory"));
    ruleDir = cacheDir != null ? cacheDir.resolve("rules") : null;
    git = gitRepository;
  }

  @Override
  public Status call() throws IOException, CompileException {
    ObjectId metaConfig = git.resolve(RefNames.REFS_CONFIG);
    if (metaConfig == null) {
      return Status.NO_RULES;
    }

    ObjectId rulesId = git.resolve(metaConfig.name() + ":rules.pl");
    if (rulesId == null) {
      return Status.NO_RULES;
    }

    if (ruleDir == null) {
      throw new CompileException("Caching not enabled");
    }
    Files.createDirectories(ruleDir);

    File tempDir = File.createTempFile("GerritCodeReview_", ".rulec");
    if (!tempDir.delete() || !tempDir.mkdir()) {
      throw new IOException("Cannot create " + tempDir);
    }
    try {
      // Try to make the directory accessible only by this process.
      // This may help to prevent leaking rule data to outsiders.
      tempDir.setReadable(true, true);
      tempDir.setWritable(true, true);
      tempDir.setExecutable(true, true);

      compileProlog(rulesId, tempDir);
      compileJava(tempDir);

      Path jarPath = ruleDir.resolve("rules-" + rulesId.getName() + ".jar");
      List<String> classFiles = getRelativePaths(tempDir, ".class");
      createJar(jarPath, classFiles, tempDir, metaConfig, rulesId);

      return Status.COMPILED;
    } finally {
      deleteAllFiles(tempDir);
    }
  }

  /** Creates a copy of rules.pl and compiles it into Java sources. */
  private void compileProlog(ObjectId prolog, File tempDir) throws IOException, CompileException {
    File tempRules = copyToTempFile(prolog, tempDir);
    try {
      Compiler comp = new Compiler();
      comp.prologToJavaSource(tempRules.getPath(), tempDir.getPath());
    } finally {
      tempRules.delete();
    }
  }

  private File copyToTempFile(ObjectId blobId, File tempDir)
      throws IOException, FileNotFoundException, MissingObjectException {
    // Any leak of tmp caused by this method failing will be cleaned
    // up by our caller when tempDir is recursively deleted.
    File tmp = File.createTempFile("rules", ".pl", tempDir);
    try (FileOutputStream out = new FileOutputStream(tmp)) {
      git.open(blobId).copyTo(out);
    }
    return tmp;
  }

  /** Compile java src into java .class files */
  private void compileJava(File tempDir) throws IOException, CompileException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new CompileException("JDK required (running inside of JRE)");
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, null)) {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(getAllFiles(tempDir, ".java"));
      ArrayList<String> options = new ArrayList<>();
      String classpath = getMyClasspath();
      if (classpath != null) {
        options.add("-classpath");
        options.add(classpath);
      }
      options.add("-d");
      options.add(tempDir.getPath());
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
      if (!task.call()) {
        Locale myLocale = Locale.getDefault();
        StringBuilder msg = new StringBuilder();
        msg.append("Cannot compile to Java bytecode:");
        for (Diagnostic<? extends JavaFileObject> err : diagnostics.getDiagnostics()) {
          msg.append('\n');
          msg.append(err.getKind());
          msg.append(": ");
          if (err.getSource() != null) {
            msg.append(err.getSource().getName());
          }
          msg.append(':');
          msg.append(err.getLineNumber());
          msg.append(": ");
          msg.append(err.getMessage(myLocale));
        }
        throw new CompileException(msg.toString());
      }
    }
  }

  private String getMyClasspath() {
    StringBuilder cp = new StringBuilder();
    appendClasspath(cp, getClass().getClassLoader());
    return 0 < cp.length() ? cp.toString() : null;
  }

  private void appendClasspath(StringBuilder cp, ClassLoader classLoader) {
    if (classLoader.getParent() != null) {
      appendClasspath(cp, classLoader.getParent());
    }
    if (classLoader instanceof URLClassLoader) {
      for (URL url : ((URLClassLoader) classLoader).getURLs()) {
        if ("file".equals(url.getProtocol())) {
          if (0 < cp.length()) {
            cp.append(File.pathSeparatorChar);
          }
          cp.append(url.getPath());
        }
      }
    }
  }

  /** Takes compiled prolog .class files, puts them into the jar file. */
  private void createJar(
      Path archiveFile, List<String> toBeJared, File tempDir, ObjectId metaConfig, ObjectId rulesId)
      throws IOException {
    long now = TimeUtil.nowMs();
    Manifest mf = new Manifest();
    mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mf.getMainAttributes().putValue("Built-by", "Gerrit Code Review " + Version.getVersion());
    if (git.getDirectory() != null) {
      mf.getMainAttributes().putValue("Source-Repository", git.getDirectory().getPath());
    }
    mf.getMainAttributes().putValue("Source-Commit", metaConfig.name());
    mf.getMainAttributes().putValue("Source-Blob", rulesId.name());

    Path tmpjar = Files.createTempFile(archiveFile.getParent(), ".rulec_", ".jar");
    try (OutputStream stream = Files.newOutputStream(tmpjar);
        JarOutputStream out = new JarOutputStream(stream, mf)) {
      byte[] buffer = new byte[10240];
      // TODO: fixify this loop
      for (String path : toBeJared) {
        JarEntry jarAdd = new JarEntry(path);
        File f = new File(tempDir, path);
        jarAdd.setTime(now);
        out.putNextEntry(jarAdd);
        if (f.isFile()) {
          try (FileInputStream in = new FileInputStream(f)) {
            while (true) {
              int nRead = in.read(buffer, 0, buffer.length);
              if (nRead <= 0) {
                break;
              }
              out.write(buffer, 0, nRead);
            }
          }
        }
        out.closeEntry();
      }
    }

    try {
      Files.move(tmpjar, archiveFile);
    } catch (IOException e) {
      throw new IOException("Cannot replace " + archiveFile, e);
    }
  }

  private List<File> getAllFiles(File dir, String extension) throws IOException {
    ArrayList<File> fileList = new ArrayList<>();
    getAllFiles(dir, extension, fileList);
    return fileList;
  }

  private void getAllFiles(File dir, String extension, List<File> fileList) throws IOException {
    for (File f : listFiles(dir)) {
      if (f.getName().endsWith(extension)) {
        fileList.add(f);
      }
      if (f.isDirectory()) {
        getAllFiles(f, extension, fileList);
      }
    }
  }

  private List<String> getRelativePaths(File dir, String extension) throws IOException {
    ArrayList<String> pathList = new ArrayList<>();
    getRelativePaths(dir, extension, "", pathList);
    return pathList;
  }

  private static void getRelativePaths(
      File dir, String extension, String path, List<String> pathList) throws IOException {
    for (File f : listFiles(dir)) {
      if (f.getName().endsWith(extension)) {
        pathList.add(path + f.getName());
      }
      if (f.isDirectory()) {
        getRelativePaths(f, extension, path + f.getName() + "/", pathList);
      }
    }
  }

  private static void deleteAllFiles(File dir) throws IOException {
    for (File f : listFiles(dir)) {
      if (f.isDirectory()) {
        deleteAllFiles(f);
      } else {
        f.delete();
      }
    }
    dir.delete();
  }

  private static File[] listFiles(File dir) throws IOException {
    File[] files = dir.listFiles();
    if (files == null) {
      throw new IOException("Failed to list directory: " + dir);
    }
    return files;
  }
}
