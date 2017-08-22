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

package com.google.gerrit.gpg;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPublicKey;

/** Utility methods for Bouncy Castle. */
public class BouncyCastleUtil {
  /**
   * Check for Bouncy Castle PGP support.
   *
   * <p>As a side effect, adds {@link BouncyCastleProvider} as a security provider.
   *
   * @return whether Bouncy Castle PGP support is enabled.
   */
  public static boolean havePGP() {
    try {
      Class.forName(PGPPublicKey.class.getName());
      addBouncyCastleProvider();
      return true;
    } catch (NoClassDefFoundError
        | ClassNotFoundException
        | SecurityException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | ClassCastException noBouncyCastle) {
      return false;
    }
  }

  private static void addBouncyCastleProvider()
      throws ClassNotFoundException, SecurityException, NoSuchMethodException,
          InstantiationException, IllegalAccessException, InvocationTargetException {
    Class<?> clazz = Class.forName(BouncyCastleProvider.class.getName());
    Constructor<?> constructor = clazz.getConstructor();
    Security.addProvider((java.security.Provider) constructor.newInstance());
  }

  private BouncyCastleUtil() {}
}
