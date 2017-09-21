// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.account;

/** Expands user name to a local email address, usually by adding a domain. */
public interface EmailExpander {
  boolean canExpand(String user);

  String expand(String user);

  class None implements EmailExpander {
    public static final None INSTANCE = new None();

    public static boolean canHandle(String fmt) {
      return fmt == null || fmt.isEmpty();
    }

    private None() {}

    @Override
    public boolean canExpand(String user) {
      return false;
    }

    @Override
    public String expand(String user) {
      return null;
    }
  }

  class Simple implements EmailExpander {
    private static final String PLACEHOLDER = "{0}";

    public static boolean canHandle(String fmt) {
      return fmt != null && fmt.contains(PLACEHOLDER);
    }

    private final String lhs;
    private final String rhs;

    public Simple(String fmt) {
      final int p = fmt.indexOf(PLACEHOLDER);
      lhs = fmt.substring(0, p);
      rhs = fmt.substring(p + PLACEHOLDER.length());
    }

    @Override
    public boolean canExpand(String user) {
      return !user.contains(" ");
    }

    @Override
    public String expand(String user) {
      return lhs + user + rhs;
    }
  }
}
