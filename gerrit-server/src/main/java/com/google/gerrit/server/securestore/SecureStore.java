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
import java.util.Objects;

/**
 * Abstract class for providing new SecureStore implementation for Gerrit.
 *
 * SecureStore is responsible for storing sensitive data like passwords in a
 * secure manner.
 *
 * It is implementator responsibility to encrypt and store values somewhere.
 *
 * To deploy new SecureStore one need to provide a jar file with one class that
 * extends {@code SecureStore}, put it on Gerrit server. Then run:
 *
 * `java -jar gerrit.war switch-secure-store -d $gerrit_site
 *       --switch-secure-store $path_to_new_secure_store.jar`
 *
 * on stopped Gerrit instance.
 */
public abstract class SecureStore {
  /**
   * Describes {@link SecureStore} entry
   */
  public static class EntryKey {
    private final String name;
    private final String section;
    private final String subsection;

    /**
     * Creates EntryKey
     */
    public EntryKey(String section, String subsection, String name) {
      this.name = name;
      this.section = section;
      this.subsection = subsection;
    }

    /**
     * @return name of SecureStore entry key
     */
    public String getName() {
      return name;
    }

    /**
     * @return name of section in SecureStore
     */
    public String getSection() {
      return section;
    }

    /**
     * @return name of subsection of SecureStore
     */
    public String getSubsection() {
      return subsection;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj instanceof EntryKey) {
        EntryKey o = (EntryKey) obj;
        return Objects.equals(name, o.name)
            && Objects.equals(section, o.section)
            && Objects.equals(subsection, o.subsection);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, section, subsection);
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
    if (values != null) {
      return values[0];
    }
    return null;
  }

  /**
   * Extract list of values from SecureStore and decrypt every value in that
   * list or {@code null} when property was not found
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
   * from SecureStore
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
