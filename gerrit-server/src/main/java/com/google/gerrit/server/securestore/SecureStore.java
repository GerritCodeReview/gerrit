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

package com.google.gerrit.server.securestore;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Abstract class for providing new SecureStore implementation for Gerrit.
 *
 * SecureStore is responsible for storing sensitive data like passwords in a
 * secure manner.
 *
 * It is implementator's responsibility to encrypt and store values.
 *
 * To deploy new SecureStore one needs to provide a jar file with explicitly one
 * class that extends {@code SecureStore} and put it in Gerrit server. Then run:
 *
 * `java -jar gerrit.war SwitchSecureStore -d $gerrit_site --new-secure-store-lib
 *  $path_to_new_secure_store.jar`
 *
 * on stopped Gerrit instance.
 */
public abstract class SecureStore {
  /**
   * Describes {@link SecureStore} entry
   */
  public static class EntryKey {
    public final String name;
    public final String section;
    public final String subsection;

    /**
     * Creates EntryKey.
     *
     * @param section
     * @param subsection
     * @param name
     */
    public EntryKey(String section, String subsection, String name) {
      this.name = name;
      this.section = section;
      this.subsection = subsection;
    }
  }

  /**
   * Extract decrypted value of stored property from SecureStore or {@code null}
   * when property was not found.
   *
   * @param section
   * @param subsection
   * @param name
   * @return decrypted String value or {@code null} if not found
   */
  public final String get(String section, String subsection, String name) {
    String[] values = getList(section, subsection, name);
    if (values != null && values.length > 0) {
      return values[0];
    }
    return null;
  }

  /**
   * Extract list of values from SecureStore and decrypt every value in that
   * list or {@code null} when property was not found.
   *
   * @param section
   * @param subsection
   * @param name
   * @return decrypted list of string values or {@code null}
   */
  public abstract String[] getList(String section, String subsection, String name);

  /**
   * Store single value in SecureStore.
   *
   * This method is responsible for encrypting value and storing it.
   *
   * @param section
   * @param subsection
   * @param name
   * @param value plain text value
   */
  public final void set(String section, String subsection, String name, String value) {
    setList(section, subsection, name, Lists.newArrayList(value));
  }

  /**
   * Store list of values in SecureStore.
   *
   * This method is responsible for encrypting all values in the list and storing them.
   *
   * @param section
   * @param subsection
   * @param name
   * @param values list of plain text values
   */
  public abstract void setList(String section, String subsection, String name, List<String> values);

  /**
   * Remove value for given {@code section}, {@code subsection} and {@code name}
   * from SecureStore.
   *
   * @param section
   * @param subsection
   * @param name
   */
  public abstract void unset(String section, String subsection, String name);

  /**
   * @return list of stored entries.
   */
  public abstract Iterable<EntryKey> list();
}
