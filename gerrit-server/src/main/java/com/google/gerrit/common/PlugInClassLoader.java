package com.google.gerrit.common;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class PlugInClassLoader extends ClassLoader {

  private File plugInsDir;

  private HashMap<String, Class<?>> classesCache =
      new HashMap<String, Class<?>>();

  private HashMap<String, Manifest> manifestCache =
      new HashMap<String, Manifest>();

  @Inject
  public PlugInClassLoader(SitePaths sitePaths) {
    super(PlugInClassLoader.class.getClassLoader());

    plugInsDir = new File(sitePaths.lib_dir, "plugins");
  }

  public Manifest getPlugInManifest(String plugInClassName) {
    return manifestCache.get(plugInClassName);
  }

  public Class<?> loadClass(String className) throws ClassNotFoundException {
    Class<?> loadedClass = classesCache.get(className);
    if (loadedClass != null) return loadedClass;


    try {
      loadedClass = loadClassFromPlugIns(className);
    } catch (IOException e) {
      throw new ClassNotFoundException("Class " + className
          + " cannot be loaded from plugIns", e);
    }
    classesCache.put(className, loadedClass);

    return loadedClass;
  }

  private Class<?> loadClassFromPlugIns(String className) throws IOException,
      ClassNotFoundException {

    Class<?> loadedClass;


    try {
      return findSystemClass(className);
    } catch (Exception e) {
      // Class is not in the System classpath
    }

    List<JarFile> plugInsList = getPlugIns();
    for (JarFile jarFile : plugInsList) {
      loadedClass = loadClassFromJar(className, jarFile);
      if (loadedClass != null) return loadedClass;
    }

    throw new ClassNotFoundException("Class " + className
        + " cannot be found in any plug-in jar");
  }



  private Class<?> loadClassFromJar(String className, JarFile jar)
      throws IOException {

    Class<?> classJar = null;

    JarEntry entry = jar.getJarEntry(className.replace('.', '/') + ".class");
    if (entry == null) return null;

    InputStream is = jar.getInputStream(entry);
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try {
      int nextValue = is.read();
      while (-1 != nextValue) {
        byteStream.write(nextValue);
        nextValue = is.read();
      }
    } finally {
      is.close();
    }

    byte[] classByte = byteStream.toByteArray();
    classJar = defineClass(className, classByte, 0, classByte.length, null);

    Manifest manifest = jar.getManifest();
    manifestCache.put(className, manifest);

    return classJar;
  }


  private List<JarFile> getPlugIns() throws IOException {
    ArrayList<JarFile> jarFileList = new ArrayList<JarFile>();
    if (plugInsDir == null || !plugInsDir.exists()) return jarFileList;

    @SuppressWarnings("unchecked")
    Collection<File> jarFiles =
        org.apache.commons.io.FileUtils.listFiles(plugInsDir,
            new String[] {"jar"}, false);
    for (File file : jarFiles) {
      jarFileList.add(new JarFile(file));
    }

    return jarFileList;
  }


  public Class<?> getPlugInClass(String plugInClassName)
      throws ClassNotFoundException {

    Class<?> plugInClass = Class.forName(plugInClassName);
    return plugInClass;
  }

}
