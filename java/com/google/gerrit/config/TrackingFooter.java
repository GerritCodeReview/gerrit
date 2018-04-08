// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.config;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;

/** Tracking entry in the configuration file */
public class TrackingFooter {
  private final FooterKey key;
  private final Pattern match;
  private final String system;

  public TrackingFooter(String f, String m, String s) throws PatternSyntaxException {
    f = f.trim();
    if (f.endsWith(":")) {
      f = f.substring(0, f.length() - 1);
    }
    this.key = new FooterKey(f);
    this.match = Pattern.compile(m.trim());
    this.system = s.trim();
  }

  /** {@link FooterKey} to match in the commit message */
  public FooterKey footerKey() {
    return key;
  }

  /** Regex for parsing out external tracking id from {@link FooterLine} */
  public Pattern match() {
    return match;
  }

  /** Name of the remote tracking system */
  public String system() {
    return system;
  }

  @Override
  public String toString() {
    return "footer = " + key.getName() + ", match = " + match.pattern() + ", system = " + system;
  }
}
