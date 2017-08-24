// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class MergeableFileBasedConfigTest {
  @Test
  public void mergeNull() throws Exception {
    MergeableFileBasedConfig cfg = newConfig();
    cfg.setString("foo", null, "bar", "value");
    String expected = "[foo]\n\tbar = value\n";
    assertConfig(cfg, expected);
    cfg.merge(null);
    assertConfig(cfg, expected);
  }

  @Test
  public void mergeFlatConfig() throws Exception {
    MergeableFileBasedConfig cfg = newConfig();
    cfg.setString("foo", null, "bar1", "value1");
    cfg.setString("foo", null, "bar2", "value2");
    cfg.setString("foo", "sub", "bar1", "value1");
    cfg.setString("foo", "sub", "bar2", "value2");

    assertConfig(
        cfg,
        "[foo]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = value2\n"
            + "[foo \"sub\"]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = value2\n");

    Config toMerge = new Config();
    toMerge.setStringList("foo", null, "bar2", ImmutableList.of("merge1", "merge2"));
    toMerge.setStringList("foo", "sub", "bar2", ImmutableList.of("merge1", "merge2"));
    cfg.merge(toMerge);

    assertConfig(
        cfg,
        "[foo]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = merge1\n"
            + "\tbar2 = merge2\n"
            + "[foo \"sub\"]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = merge1\n"
            + "\tbar2 = merge2\n");
  }

  @Test
  public void mergeStackedConfig() throws Exception {
    MergeableFileBasedConfig cfg = newConfig();
    cfg.setString("foo", null, "bar1", "value1");
    cfg.setString("foo", null, "bar2", "value2");
    cfg.setString("foo", "sub", "bar1", "value1");
    cfg.setString("foo", "sub", "bar2", "value2");

    assertConfig(
        cfg,
        "[foo]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = value2\n"
            + "[foo \"sub\"]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = value2\n");

    Config base = new Config();
    Config toMerge = new Config(base);
    base.setStringList("foo", null, "bar2", ImmutableList.of("merge1", "merge2"));
    base.setStringList("foo", "sub", "bar2", ImmutableList.of("merge1", "merge2"));
    cfg.merge(toMerge);

    assertConfig(
        cfg,
        "[foo]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = merge1\n"
            + "\tbar2 = merge2\n"
            + "[foo \"sub\"]\n"
            + "\tbar1 = value1\n"
            + "\tbar2 = merge1\n"
            + "\tbar2 = merge2\n");
  }

  private MergeableFileBasedConfig newConfig() throws Exception {
    File f = File.createTempFile(getClass().getSimpleName(), ".config");
    f.deleteOnExit();
    return new MergeableFileBasedConfig(f, FS.detect());
  }

  private void assertConfig(MergeableFileBasedConfig cfg, String expected) throws Exception {
    assertThat(cfg.toText()).isEqualTo(expected);
    cfg.save();
    assertThat(new String(Files.readAllBytes(cfg.getFile().toPath()), UTF_8)).isEqualTo(expected);
  }
}
