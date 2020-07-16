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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.lib.Config;

@AutoValue
public abstract class ConfiguredMimeTypes {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String MIMETYPE = "mimetype";
  private static final String KEY_PATH = "path";

  public abstract ImmutableList<TypeMatcher> matchers();

  public static ConfiguredMimeTypes create(String projectName, Config rc) {
    Set<String> types = rc.getSubsections(MIMETYPE);
    ImmutableList.Builder<TypeMatcher> matchers = ImmutableList.builder();
    if (!types.isEmpty()) {
      for (String typeName : types) {
        for (String path : rc.getStringList(MIMETYPE, typeName, KEY_PATH)) {
          try {
            if (path.startsWith("^")) {
              matchers.add(new ReType(typeName, path));
            } else {
              matchers.add(new FnType(typeName, path));
            }
          } catch (PatternSyntaxException | InvalidPatternException e) {
            logger.atWarning().log(
                "Ignoring invalid %s.%s.%s = %s in project %s: %s",
                MIMETYPE, typeName, KEY_PATH, path, projectName, e.getMessage());
          }
        }
      }
    }
    return new AutoValue_ConfiguredMimeTypes(matchers.build());
  }

  public static ConfiguredMimeTypes create(ImmutableList<TypeMatcher> matchers) {
    return new AutoValue_ConfiguredMimeTypes(matchers);
  }

  @Nullable
  public String getMimeType(String path) {
    for (TypeMatcher m : matchers()) {
      if (m.matches(path)) {
        return m.type;
      }
    }
    return null;
  }

  public abstract static class TypeMatcher {
    private final String type;
    private final String pattern;

    private TypeMatcher(String type, String pattern) {
      this.type = type;
      this.pattern = pattern;
    }

    public String getPattern() {
      return pattern;
    }

    public String getType() {
      return type;
    }

    protected abstract boolean matches(String path);
  }

  public static class FnType extends TypeMatcher {
    private final FileNameMatcher matcher;

    public FnType(String type, String pattern) throws InvalidPatternException {
      super(type, pattern);
      this.matcher = new FileNameMatcher(pattern, null);
    }

    @Override
    protected boolean matches(String input) {
      FileNameMatcher m = new FileNameMatcher(matcher);
      m.append(input);
      return m.isMatch();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof FnType)) {
        return false;
      }
      FnType other = (FnType) o;
      return Objects.equals(other.getType(), getType())
          && Objects.equals(other.getPattern(), getPattern());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getType(), getPattern());
    }
  }

  public static class ReType extends TypeMatcher {
    private final Pattern re;

    public ReType(String type, String pattern) throws PatternSyntaxException {
      super(type, pattern);
      this.re = Pattern.compile(pattern);
    }

    @Override
    protected boolean matches(String input) {
      return re.matcher(input).matches();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ReType)) {
        return false;
      }
      ReType other = (ReType) o;
      return Objects.equals(other.getType(), getType())
          && Objects.equals(other.getPattern(), getPattern());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getType(), getPattern());
    }
  }
}
