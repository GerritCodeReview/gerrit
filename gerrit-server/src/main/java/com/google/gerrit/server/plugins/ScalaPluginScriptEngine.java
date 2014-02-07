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
package com.google.gerrit.server.plugins;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;

import scala.Char;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.immutable.Seq;
import scala.collection.mutable.Map;
import scala.reflect.internal.util.BatchSourceFile;
import scala.reflect.internal.util.SourceFile;
import scala.reflect.io.AbstractFile;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static scala.collection.JavaConversions.*;

public class ScalaPluginScriptEngine {

  private final IMain scalaEngine;
  private final ScalaClassLoader classLoader;

  public class ScalaClassLoader extends ClassLoader {
    private Map<String, AbstractFile> scalaClasses;

    public ScalaClassLoader(final IMain scalaEngine) {
      super(ScalaClassLoader.class.getClassLoader());
      scalaClasses =
          scalaEngine.virtualDirectory()
              .scala$reflect$io$VirtualDirectory$$files();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      Option<AbstractFile> classFile = scalaClasses.get(name + ".class");
      if (classFile.isEmpty()) {
        throw new ClassNotFoundException("Cannot find Scala class " + name);
      }

      byte[] ba;
      try {
        ba = classFile.get().toByteArray();
        return defineClass(name, ba, 0, ba.length);
      } catch (IOException e) {
        throw new ClassNotFoundException("Cannot open Scala class file "
            + classFile.get(), e);
      }
    }

    public Set<String> getAllLoadedClassNames() {
      Set<String> classNames = Sets.newHashSet();
      for (Iterator<String> keysIter = scalaClasses.keys().iterator(); keysIter
          .hasNext();) {
        String classFileName = keysIter.next();
        classNames
            .add(StringUtils.substringBeforeLast(classFileName, ".class"));
      }
      return classNames;
    }
  }

  @Inject
  public ScalaPluginScriptEngine() {
    Settings settings = new Settings();
    settings.usejavacp().tryToSetFromPropertyValue("true");
    scalaEngine = new IMain(settings);
    classLoader = new ScalaClassLoader(scalaEngine);
  }

  public Set<Class<?>> eval(File scalaFile) throws IOException, ClassNotFoundException {
    Set<Class<?>> classes = Sets.newHashSet();

    SourceFile sourceFile = new BatchSourceFile(scalaFile.getName(), readScalaFile(scalaFile));
    if (!scalaEngine.compileSources(asScalaBuffer(Arrays.asList(sourceFile)).toList())) {
      throw new IOException("Invalid Scala file " + scalaFile);
    }

    for (String className : classLoader.getAllLoadedClassNames()) {
      Class<?> clazz = classLoader.loadClass(className);
      classes.add(clazz);
    }
    return classes;
  }

  private Seq<Object> readScalaFile(File scalaFile) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(scalaFile));
    StringBuilder scalaCode = new StringBuilder();
    try {
      String line;
      while(null != (line = reader.readLine())) {
        scalaCode.append(line);
        scalaCode.append("\n");
      }
    } finally {
       reader.close();
    }

    List<Object> chars = new ArrayList<Object>();
    for (char c : scalaCode.toString().toCharArray()) {
      chars.add(Char.unbox(c));
    }

    return asScalaBuffer(chars).toList();
  }

  public ScalaClassLoader getClassLoader() {
    return classLoader;
  }
}
