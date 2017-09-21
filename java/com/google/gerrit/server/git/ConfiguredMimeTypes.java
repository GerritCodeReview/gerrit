// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfiguredMimeTypes {
  private static final Logger log = LoggerFactory.getLogger(ConfiguredMimeTypes.class);

  private static final String MIMETYPE = "mimetype";
  private static final String KEY_PATH = "path";

  private final List<TypeMatcher> matchers;

  ConfiguredMimeTypes(String projectName, Config rc) {
    Set<String> types = rc.getSubsections(MIMETYPE);
    if (types.isEmpty()) {
      matchers = Collections.emptyList();
    } else {
      matchers = new ArrayList<>();
      for (String typeName : types) {
        for (String path : rc.getStringList(MIMETYPE, typeName, KEY_PATH)) {
          try {
            add(typeName, path);
          } catch (PatternSyntaxException | InvalidPatternException e) {
            log.warn(
                String.format(
                    "Ignoring invalid %s.%s.%s = %s in project %s: %s",
                    MIMETYPE, typeName, KEY_PATH, path, projectName, e.getMessage()));
          }
        }
      }
    }
  }

  private void add(String typeName, String path)
      throws PatternSyntaxException, InvalidPatternException {
    if (path.startsWith("^")) {
      matchers.add(new ReType(typeName, path));
    } else {
      matchers.add(new FnType(typeName, path));
    }
  }

  public String getMimeType(String path) {
    for (TypeMatcher m : matchers) {
      if (m.matches(path)) {
        return m.type;
      }
    }
    return null;
  }

  private abstract static class TypeMatcher {
    final String type;

    TypeMatcher(String type) {
      this.type = type;
    }

    abstract boolean matches(String path);
  }

  private static class FnType extends TypeMatcher {
    private final FileNameMatcher matcher;

    FnType(String type, String pattern) throws InvalidPatternException {
      super(type);
      this.matcher = new FileNameMatcher(pattern, null);
    }

    @Override
    boolean matches(String input) {
      FileNameMatcher m = new FileNameMatcher(matcher);
      m.append(input);
      return m.isMatch();
    }
  }

  private static class ReType extends TypeMatcher {
    private final Pattern re;

    ReType(String type, String pattern) throws PatternSyntaxException {
      super(type);
      this.re = Pattern.compile(pattern);
    }

    @Override
    boolean matches(String input) {
      return re.matcher(input).matches();
    }
  }
}
