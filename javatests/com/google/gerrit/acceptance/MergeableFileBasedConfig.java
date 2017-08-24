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

package com.google.gerrit.acceptance;

import com.google.common.collect.Lists;
import java.io.File;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

/** A file based Config that can merge another Config instance. */
public class MergeableFileBasedConfig extends FileBasedConfig {
  public MergeableFileBasedConfig(File cfgLocation, FS fs) {
    super(cfgLocation, fs);
  }

  /**
   * Merge another Config into this Config.
   *
   * <p>In case a configuration parameter exists both in this instance and in the merged instance
   * then the value in this instance will simply replaced by the value from the merged instance.
   *
   * @param s Config to merge into this instance
   */
  public void merge(Config s) {
    if (s == null) {
      return;
    }
    for (String section : s.getSections()) {
      for (String subsection : s.getSubsections(section)) {
        for (String name : s.getNames(section, subsection)) {
          setStringList(
              section,
              subsection,
              name,
              Lists.newArrayList(s.getStringList(section, subsection, name)));
        }
      }

      for (String name : s.getNames(section, true)) {
        setStringList(
            section, null, name, Lists.newArrayList(s.getStringList(section, null, name)));
      }
    }
  }
}
