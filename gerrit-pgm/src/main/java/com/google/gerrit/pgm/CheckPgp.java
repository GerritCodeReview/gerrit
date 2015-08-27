// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.inject.Injector;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPublicKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Map;
import java.util.TreeMap;

public class CheckPgp extends SiteProgram {
  public CheckPgp() {
  }

  @Override
  public int run() throws Exception {
    Injector dbInjector =
        createDbInjector(DataSourceProvider.Context.MULTI_USER);
    LifecycleManager lifecycle = new LifecycleManager();
    lifecycle.add(dbInjector);
    lifecycle.start();
    try {
      SitePaths sitePaths = dbInjector.getInstance(SitePaths.class);
      Map<String, Long> files = new TreeMap<>();
      try (DirectoryStream<Path> stream =
          Files.newDirectoryStream(sitePaths.lib_dir.toPath())) {
        for (Path p : stream) {
          files.put(p.getFileName().toString(), Files.size(p));
        }
      }
      System.err.println("Contents of " + sitePaths.lib_dir + ": ");
      for (Map.Entry<String, Long> e : files.entrySet()) {
        System.err.format("%s %d\n", e.getKey(), e.getValue());
      }
      boolean have = havePGP();
      System.err.println("\nHave PGP? " + have);
      return have ? 0 : 127;
    } finally {
      lifecycle.stop();
    }
  }

  private static boolean havePGP() {
    try {
      Class.forName(PGPPublicKey.class.getName());
      System.err.println("Loaded PGPPublicKey.class successfully");
      addBouncyCastleProvider();
      System.err.println("Added Bouncy Castle Provider class successfully.");
      return true;
    } catch (NoClassDefFoundError | ClassNotFoundException | SecurityException
        | NoSuchMethodException | InstantiationException
        | IllegalAccessException | InvocationTargetException
        | ClassCastException noBouncyCastle) {
      noBouncyCastle.printStackTrace();
      return false;
    }
  }

  private static void addBouncyCastleProvider() throws ClassNotFoundException,
          SecurityException, NoSuchMethodException, InstantiationException,
          IllegalAccessException, InvocationTargetException {
    Class<?> clazz = Class.forName(BouncyCastleProvider.class.getName());
    Constructor<?> constructor = clazz.getConstructor();
    Security.addProvider((java.security.Provider) constructor.newInstance());
  }
}
