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

package com.google.gerrit.server.plugins.actions;

import com.google.gerrit.extensions.annotations.ActionName;
import com.google.inject.Key;

import java.lang.annotation.Annotation;

/** Utilities to support {@link ActionName} construction. */
public class Actions {
  public static Key<Action> key(final String name) {
    return key(named(name));
  }

  public static Key<Action> key(final ActionName name) {
    return Key.get(Action.class, name);
  }

  public static Key<Action> key(final String parent, final String name) {
    return Key.get(Action.class, named(parent + "_" + name));
  }

  /** Create a ActionName annotation for the supplied name. */
  public static ActionName named(final String name) {
    return new ActionName() {
      @Override
      public String value() {
        return name;
      }

      @Override
      public Class<? extends Annotation> annotationType() {
        return ActionName.class;
      }

      @Override
      public int hashCode() {
        // This is specified in java.lang.Annotation.
        return (127 * "value".hashCode()) ^ value().hashCode();
      }

      @Override
      public boolean equals(final Object obj) {
        return obj instanceof ActionName
            && value().equals(((ActionName) obj).value());
      }

      @Override
      public String toString() {
        return "@" + ActionName.class.getName() + "(value=" + value() + ")";
      }
    };
  }

  private Actions() {
  }
}
