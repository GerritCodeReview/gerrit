// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.gerrit.pgm.util;

import com.google.gerrit.server.git.GitRepositoryManager;

import com.googlecode.prolog_cafe.compiler.CompileException;
import com.googlecode.prolog_cafe.compiler.Compiler;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
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

  private File sitePath;

  private Repository git;

  public PrologJar(File sitePath, Repository git) {
    super();
    this.sitePath = sitePath;
    this.git = git;
  }

  /** @return true if it succeeds, false otherwise */
  public boolean run() {
    ObjectId rulesId;
    try {
      rulesId = git.resolve(GitRepositoryManager.REF_CONFIG + ":rules.pl");
    } catch (IOException err) {
      err.printStackTrace();
      return false;
    }
    if (rulesId == null) {
      System.err.println("error: rules.pl cannot be resolved in refs/meta/config");
      return false;
    }

    //navigate to (sitePath)/cache
    File cacheDir = new File(sitePath.getPath() + "/cache");
    if (!cacheDir.exists()) {
      System.err.println("error: caching not enabled on server");
      return false;
    }
    File ruleDir = new File(cacheDir.getPath() + "/rules");
    ruleDir.mkdir();
    File tempDir = new File(ruleDir.getPath() + "/temp");
    tempDir.mkdir();

    try {
      compileProlog(rulesId, ruleDir, tempDir);
    } catch (CompileException err) {
      err.printStackTrace();
      return false;
    } catch (IOException err) {
      err.printStackTrace();
      return false;
    }

    try {
      compileJava(tempDir);
    } catch (IOException err) {
      err.printStackTrace();
      return false;
    }

    File jarFile = new File(ruleDir.getPath() + "/rules-" + rulesId.getName() + ".jar");
    List<File> tempFiles = getAllClassFiles(tempDir.listFiles());
    try {
      createJarArchive(jarFile, tempFiles, tempDir);
    } catch (IOException err) {
      err.printStackTrace();
      return false;
    }

    deleteAllFiles(tempDir);
    return true;
  }

  /** Creates temp ver of rules.pl from byte[] and compiles it into java src */
  private void compileProlog(ObjectId prolog, File ruleDir, File tempDir)
      throws IOException, CompileException {
    File tempRules = File.createTempFile("rules", ".pl", ruleDir);
    Compiler comp = new Compiler();
    ObjectLoader ruleLdr = git.open(prolog);
    byte[] bytes = ruleLdr.getBytes();
    BufferedReader br = new BufferedReader(new InputStreamReader(
        new ByteArrayInputStream(bytes), Charset.forName("UTF8")));
    BufferedWriter bw = new BufferedWriter(new FileWriter(tempRules));
    while (br.ready()) {
      bw.write(br.read());
    }
    bw.close();
    br.close();
    comp.prologToJavaSource(tempRules.getPath(), tempDir.getPath());
    tempRules.delete();
  }

  /** Compile java src into java .class files */
  private boolean compileJava(File tempDir) throws IOException{
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics =
      new DiagnosticCollector<JavaFileObject>();
    StandardJavaFileManager fileManager =
      compiler.getStandardFileManager(diagnostics, null, null);
    Iterable<? extends JavaFileObject> compilationUnits =
      fileManager.getJavaFileObjectsFromFiles(Arrays.asList(tempDir.listFiles()));
    ArrayList<String> options = new ArrayList<String>();
    options.add("-d");
    options.add(tempDir.getPath());
    JavaCompiler.CompilationTask task =
      compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
    boolean success = task.call();
    fileManager.close();
    return success;
  }

  /** Takes compiled prolog .class files, puts them into the jar file */
  private void createJarArchive(File archiveFile, List<File> toBeJared, File tempDir)
      throws IOException{
    byte buffer[] = new byte[10240];
    FileOutputStream stream = new FileOutputStream(archiveFile);
    JarOutputStream out = new JarOutputStream(stream, new Manifest());

    for (File f : toBeJared) {
      if (f == null || !f.exists())
        continue;
      String relativePath = f.getPath().replace(tempDir.getPath() + "/", "");
      //jars require directory paths end with "/"
      if (f.isDirectory()) {
        relativePath += "/";
      }
      JarEntry jarAdd = new JarEntry(relativePath);
      jarAdd.setTime(f.lastModified());
      out.putNextEntry(jarAdd);
      if (f.isFile()) {
        FileInputStream in = new FileInputStream(f);
        while (true) {
          int nRead = in.read(buffer, 0, buffer.length);
          if (nRead <= 0) {
            break;
          }
          out.write(buffer, 0, nRead);
        }
        in.close();
      }
      out.closeEntry();
    }
    out.close();
    stream.close();
  }

  private List<File> getAllClassFiles(File[] files) {
    ArrayList<File> fileList = new ArrayList<File>();
    for (File f : files) {
      if (f.getName().endsWith(".class") || f.isDirectory()) {
        fileList.add(f);
      }
      if (f.isDirectory()) {
        fileList.addAll(getAllClassFiles(f.listFiles()));
      }
    }

    return fileList;
  }

  private void deleteAllFiles(File dir) {
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        deleteAllFiles(f);
        f.delete();
      } else {
        f.delete();
      }
    }
    dir.delete();
  }
}