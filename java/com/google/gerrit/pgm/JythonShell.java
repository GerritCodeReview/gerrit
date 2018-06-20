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

package com.google.gerrit.pgm;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.launcher.GerritLauncher;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Properties;

public class JythonShell {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String STARTUP_RESOURCE = "com/google/gerrit/pgm/Startup.py";
  private static final String STARTUP_FILE = "Startup.py";

  private Class<?> console;
  private Class<?> pyObject;
  private Class<?> pySystemState;
  private Object shell;
  private ArrayList<String> injectedVariables;

  public JythonShell() {
    Properties env = new Properties();
    // Let us inspect private class members
    env.setProperty("python.security.respectJavaAccessibility", "false");

    File home = GerritLauncher.getHomeDirectory();
    if (home != null) {
      env.setProperty("python.cachedir", new File(home, "jythoncache").getPath());
    }

    // For package introspection and "import com.google" to work,
    // Jython needs to inspect actual .jar files (not just classloader)
    StringBuilder classPath = new StringBuilder();
    final ClassLoader cl = getClass().getClassLoader();
    if (cl instanceof java.net.URLClassLoader) {
      @SuppressWarnings("resource")
      URLClassLoader ucl = (URLClassLoader) cl;
      for (URL u : ucl.getURLs()) {
        if ("file".equals(u.getProtocol())) {
          if (classPath.length() > 0) {
            classPath.append(java.io.File.pathSeparatorChar);
          }
          classPath.append(u.getFile());
        }
      }
    }
    env.setProperty("java.class.path", classPath.toString());

    console = findClass("org.python.util.InteractiveConsole");
    pyObject = findClass("org.python.core.PyObject");
    pySystemState = findClass("org.python.core.PySystemState");

    runMethod(
        pySystemState,
        pySystemState,
        "initialize",
        new Class<?>[] {Properties.class, Properties.class},
        new Object[] {null, env});

    try {
      shell = console.getConstructor(new Class<?>[] {}).newInstance();
      logger.atInfo().log("Jython shell instance created.");
    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException e) {
      throw noInterpreter(e);
    }
    injectedVariables = new ArrayList<>();
    set("Shell", this);
  }

  protected Object runMethod0(
      Class<?> klazz, Object instance, String name, Class<?>[] sig, Object[] args)
      throws InvocationTargetException {
    try {
      Method m;
      m = klazz.getMethod(name, sig);
      return m.invoke(instance, args);
    } catch (NoSuchMethodException
        | IllegalAccessException
        | IllegalArgumentException
        | SecurityException e) {
      throw cannotStart(e);
    }
  }

  protected Object runMethod(
      Class<?> klazz, Object instance, String name, Class<?>[] sig, Object[] args) {
    try {
      return runMethod0(klazz, instance, name, sig, args);
    } catch (InvocationTargetException e) {
      throw cannotStart(e);
    }
  }

  protected Object runInterpreter(String name, Class<?>[] sig, Object[] args) {
    return runMethod(console, shell, name, sig, args);
  }

  protected String getDefaultBanner() {
    return (String) runInterpreter("getDefaultBanner", new Class<?>[] {}, new Object[] {});
  }

  protected void printInjectedVariable(String id) {
    runInterpreter(
        "exec",
        new Class<?>[] {String.class},
        new Object[] {"print '\"%s\" is \"%s\"' % (\"" + id + "\", " + id + ")"});
  }

  public void run() {
    for (String key : injectedVariables) {
      printInjectedVariable(key);
    }
    reload();
    runInterpreter(
        "interact",
        new Class<?>[] {String.class, pyObject},
        new Object[] {
          getDefaultBanner()
              + " running for Gerrit "
              + com.google.gerrit.common.Version.getVersion(),
          null,
        });
  }

  public void set(String key, Object content) {
    runInterpreter("set", new Class<?>[] {String.class, Object.class}, new Object[] {key, content});
    injectedVariables.add(key);
  }

  private static Class<?> findClass(String klazzname) {
    try {
      return Class.forName(klazzname);
    } catch (ClassNotFoundException e) {
      throw noShell("Class " + klazzname + " not found", e);
    }
  }

  public void reload() {
    execResource(STARTUP_RESOURCE);
    execFile(GerritLauncher.getHomeDirectory(), STARTUP_FILE);
  }

  protected void execResource(String p) {
    try (InputStream in = JythonShell.class.getClassLoader().getResourceAsStream(p)) {
      if (in != null) {
        execStream(in, "resource " + p);
      } else {
        logger.atSevere().log("Cannot load resource %s", p);
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(e.getMessage());
    }
  }

  protected void execFile(File parent, String p) {
    try {
      File script = new File(parent, p);
      if (script.canExecute()) {
        runMethod0(
            console,
            shell,
            "execfile",
            new Class<?>[] {String.class},
            new Object[] {script.getAbsolutePath()});
      } else {
        logger.atInfo().log(
            "User initialization file %s is not found or not executable", script.getAbsolutePath());
      }
    } catch (InvocationTargetException e) {
      logger.atSevere().withCause(e).log("Exception occurred while loading file %s", p);
    } catch (SecurityException e) {
      logger.atSevere().withCause(e).log("SecurityException occurred while loading file %s", p);
    }
  }

  protected void execStream(InputStream in, String p) {
    try {
      runMethod0(
          console,
          shell,
          "execfile",
          new Class<?>[] {InputStream.class, String.class},
          new Object[] {in, p});
    } catch (InvocationTargetException e) {
      logger.atSevere().withCause(e).log("Exception occurred while loading %s", p);
    }
  }

  private static UnsupportedOperationException noShell(String m, Throwable why) {
    final String prefix = "Cannot create Jython shell: ";
    final String postfix = "\n     (You might need to install jython.jar in the lib directory)";
    return new UnsupportedOperationException(prefix + m + postfix, why);
  }

  private static UnsupportedOperationException noInterpreter(Throwable why) {
    final String msg = "Cannot create Python interpreter";
    return noShell(msg, why);
  }

  private static UnsupportedOperationException cannotStart(Throwable why) {
    final String msg = "Cannot start Jython shell";
    return new UnsupportedOperationException(msg, why);
  }
}
