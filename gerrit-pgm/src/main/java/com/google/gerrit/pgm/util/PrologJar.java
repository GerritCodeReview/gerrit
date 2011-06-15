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

package com.google.gerrit.pgm.util;

import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.compiler.Compiler;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Helper class for Rulec: does the actual prolog -> java src -> class -> jar work
 * Finds rules.pl in refs/meta/config branch
 * Creates rules-(sha1 of rules.pl).jar in (site-path)/cache/rules
 */
public class PrologJar {

  private File cacheDir;
  private Repository git;

  public PrologJar (Config config, SitePaths site, Repository git) {
    this.git = git;
    String s = config.getString("cache", null, "directory");
    cacheDir = site.resolve(config.getString("cache", null, "directory"));
  }

  /** @return true if it succeeds, false otherwise */
  public boolean run() throws IOException, CompileException{
    ObjectId rulesId;
    rulesId = git.resolve(GitRepositoryManager.REF_CONFIG + ":rules.pl");
    if (rulesId == null) {
      System.err.println("error: no rules.pl in " + git.getDirectory());
      return false;
    }

    if (cacheDir == null) {
      System.err.println("error: caching not enabled on server");
      return false;
    }
    File ruleDir = new File(cacheDir.getPath() + "/rules");
    ruleDir.mkdir();
    File tempDir = File.createTempFile("GerritCodeReview_", ".rulec");
    if (!tempDir.delete() || !tempDir.mkdir())
      throw new IOException("Cannot create temporary directory " + tempDir);

    try {
      compileProlog(rulesId, tempDir);

      compileJava(tempDir);

      File jarFile = new File(ruleDir.getPath() + "/rules-" + rulesId.getName() + ".jar");
      List<String> tempFiles = getRelativePaths(tempDir, ".class");
      createJarArchive(jarFile, tempFiles, tempDir);

      return true;
    } finally {
      deleteAllFiles(tempDir);
    }
  }

  /** Creates temp ver of rules.pl from byte[] and compiles it into java src */
  private void compileProlog(ObjectId prolog, File tempDir)
      throws IOException, CompileException {
    File tempRules = File.createTempFile("rules", ".pl", tempDir);
    Compiler comp = new Compiler();
    OutputStream out = new FileOutputStream(tempRules);
    try {
      git.open(prolog).copyTo(out);
      comp.prologToJavaSource(tempRules.getPath(), tempDir.getPath());
    } finally {
      out.close();
      tempRules.delete();
    }
  }

  /** Compile java src into java .class files */
  private void compileJava(File tempDir) throws IOException{
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics =
      new DiagnosticCollector<JavaFileObject>();
    StandardJavaFileManager fileManager =
      compiler.getStandardFileManager(diagnostics, null, null);
    try {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager
              .getJavaFileObjectsFromFiles(getAllFiles(tempDir, ".java"));
      ArrayList<String> options = new ArrayList<String>();
      options.add("-d");
      options.add(tempDir.getPath());
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, diagnostics, options, null,
              compilationUnits);
      if (!task.call()) {
        throw new IOException("errors in java source compilation");
      }
    } finally {
      fileManager.close();
    }
  }

  /** Takes compiled prolog .class files, puts them into the jar file */
  private void createJarArchive(File archiveFile, List<String> toBeJared, File tempDir)
      throws IOException{
    long now = System.currentTimeMillis();
    byte buffer[] = new byte[10240];
    File tmpjar = File.createTempFile(".rulec_", ".jar", archiveFile.getParentFile());
    FileOutputStream stream = new FileOutputStream(tmpjar);
    JarOutputStream out = new JarOutputStream(stream, new Manifest());
    try {
      for (String path : toBeJared) {
        JarEntry jarAdd = new JarEntry(path);
        File f = new File(tempDir, path);
        jarAdd.setTime(now);
        out.putNextEntry(jarAdd);
        if (f.isFile()) {
          FileInputStream in = new FileInputStream(f);
          try {
            while (true) {
              int nRead = in.read(buffer, 0, buffer.length);
              if (nRead <= 0) {
                break;
              }
              out.write(buffer, 0, nRead);
            }
          } finally {
            in.close();
          }
        }
        out.closeEntry();
      }

      if (!tmpjar.renameTo(archiveFile)) {
        throw new IOException("Cannot replace archiveFile");
      }
    } finally {
      out.close();
      tmpjar.delete();
    }
  }

  private List<File> getAllFiles(File dir, String prefix) {
    ArrayList<File> fileList = new ArrayList<File>();
    for (File f : dir.listFiles()) {
      if (f.getName().endsWith(prefix)) {
        fileList.add(f);
      }
      if (f.isDirectory()) {
        fileList.addAll(getAllFiles(f, prefix));
      }
    }

    return fileList;
  }

  private List<String> getRelativePaths(File dir, String prefix) {
    return getRelativePaths(dir, prefix, "");
  }

  private List<String> getRelativePaths(File dir, String prefix, String path) {
    ArrayList<String> pathList = new ArrayList<String>();
    for (File f : dir.listFiles()) {
      if (f.getName().endsWith(prefix)) {
        pathList.add(path + f.getName());
      }
      if (f.isDirectory()) {
        pathList.addAll(getRelativePaths(f, prefix, path + f.getName() + "/"));
      }
    }

    return pathList;
  }

  private void deleteAllFiles(File dir) {
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        deleteAllFiles(f);
      } else {
        f.delete();
      }
    }
    dir.delete();
  }
}