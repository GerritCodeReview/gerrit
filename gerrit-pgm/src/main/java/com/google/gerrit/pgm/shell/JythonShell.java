// Copyright (C) 2012, Marcin Cieslak <saper@saper.info>
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

package com.google.gerrit.pgm.shell;

import com.google.gerrit.launcher.GerritLauncher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Properties;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

public class JythonShell implements InteractiveShell {

  private static final Logger log =
      LoggerFactory.getLogger(JythonShell.class);

  private static final String STARTUP_RESOURCE = "com/google/gerrit/pgm/Startup.py";
  private static final String STARTUP_FILE = "Startup.py";

  private Class console;
  private Class pyObject;
  private Class pySystemState;
  private Object initialize;
  private Object interp;
  private ArrayList <String> injectedVariables;

  public JythonShell() {
    final String CONSOLE  = "org.python.util.InteractiveConsole";
    final String PYOBJECT = "org.python.core.PyObject";
    final String PYSYSTEMSTATE = "org.python.core.PySystemState";
    final String CLASSPATH = "java.class.path";

    final String ACCESSIBILITY = "python.security.respectJavaAccessibility";
    final String CACHEDIR = "python.cachedir";
    final String CACHEDIR_SKIP = "python.cachedir.skip";
    Properties post = new Properties();
    Method initialize;

    // Let us inspect private class members
    post.setProperty(ACCESSIBILITY, "false");

    File home = GerritLauncher.getHomeDirectory();
    if (home != null) { 
      post.setProperty(CACHEDIR, home + java.io.File.separator + "jythoncache");
    }

    /*
     * For package introspection and "import com.google" to work,
     * Jython needs to inspect actual .jar files (not just classloader)
     */
    StringBuilder classPath = new StringBuilder();
    final ClassLoader cl = getClass().getClassLoader(); 
    if (cl instanceof java.net.URLClassLoader) {
      URLClassLoader ucl = (URLClassLoader)cl;
      for (URL u : ucl.getURLs()) {
        if ("file".equals(u.getProtocol())) {
          if (classPath.length() > 0) {
            classPath.append(java.io.File.pathSeparatorChar);
          }
          classPath.append(u.getFile());
        }
      }
    }
    post.setProperty(CLASSPATH, classPath.toString());

    console = findClass(CONSOLE);
    pyObject = findClass(PYOBJECT);
    pySystemState = findClass(PYSYSTEMSTATE);
    
    runMethod(pySystemState, pySystemState, "initialize", 
      new Class[]  { Properties.class, Properties.class },
      new Object[] { null, post }
    );

    try {
      interp = console.newInstance();
      log.info("Jython shell instance created.");
    } catch (InstantiationException e) {
      throw noInterpreter(e);
    } catch (IllegalAccessException e) {
      throw noInterpreter(e);
    }
    injectedVariables = new ArrayList<String>();
    set("Shell", this);
  }
 
  protected Object runMethod0(Class klazz, Object instance, String name, Class[] sig, Object[] args)
      throws InvocationTargetException {
    Method interpMethod;
    try {
      interpMethod = klazz.getMethod(name, sig);
      return interpMethod.invoke(instance, args);
    } catch(NoSuchMethodException e) {
      throw cannotStart(e);
    } catch(SecurityException e) {
      throw cannotStart(e);
    } catch(IllegalArgumentException e) {
      throw cannotStart(e);
    } catch(IllegalAccessException e) {
      throw cannotStart(e);
    }
  }

  protected Object runMethod(Class klazz, Object instance, String name, Class[] sig, Object[] args) {
    try {
      return runMethod0(klazz, instance, name, sig, args);
    } catch(InvocationTargetException e) {
      throw cannotStart(e);
    }
  }

  protected Object runInterpreter(String name, Class[] sig, Object[] args) {
    return runMethod(console, interp, name, sig, args);
  }

  protected String getDefaultBanner() {
    return (String)runInterpreter("getDefaultBanner",
                  new Class[] { }, new Object[] { });
  }

  protected void printInjectedVariable(String id) {
    runInterpreter("exec",
      new Class[]  { String.class },
      new Object[] { "print '\"%s\" is \"%s\"' % (\"" + id + "\", " + id + ")" }
    );
  }

  public void run() {
    for ( String key : injectedVariables ) {
      printInjectedVariable(key);
    }
    reload();
    runInterpreter("interact",
      new Class[]  { String.class, pyObject },
      new Object[] { getDefaultBanner() +
        " running for Gerrit " + com.google.gerrit.common.Version.getVersion(),
        null });
  }

  public void set(String key, Object content) {
    runInterpreter("set",
      new Class[]  { String.class, Object.class },
      new Object[] { key, content }
    );
    injectedVariables.add(key);
  }

  private static Class findClass(String klazzname) {
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

  protected void execResource(final String p) {
    InputStream in = JythonShell.class.getClassLoader().getResourceAsStream(p);
    if (in != null) {
      execStream(in, "resource " + p);
    } else {
      log.error("Cannot load resource " + p);
    }
  }

  protected void execFile(final File parent, final String p) {
    try {
      File script = new File(parent, p);
      if (script.canExecute()) {
        runMethod0(console, interp, "execfile",
          new Class[] { String.class },
          new Object[] { script.getAbsolutePath() }
        );
      } else {
        log.info("User initialization file " + script.getAbsolutePath() + " is not found or not executable");
      }
    } catch (InvocationTargetException e) {
      log.error("Exception occured while loading file " + p + " : ", e);
    } catch (SecurityException e) {
      log.error("SecurityException occured while loading file " + p + " : ", e);
    }
  }

  protected void execStream(final InputStream in, final String p) {
    try { 
      runMethod0(console, interp, "execfile",
        new Class[] { InputStream.class, String.class },
        new Object[] { in, p }
      );
    } catch (InvocationTargetException e) {
      log.error("Exception occured while loading " + p + " : ", e);
    }
  }

  private static UnsupportedOperationException noShell(final String m, Throwable why) {
    final String prefix = "Cannot create Jython shell: ";
    final String postfix = "\n     (You might need to install jython.jar in the lib directory)";
    return new UnsupportedOperationException(prefix + m + postfix, why);
  }
  private static UnsupportedOperationException noEnvironment(Throwable why) {
    final String msg = "Cannot prepare Python environment";
    return noShell(msg, why);
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
