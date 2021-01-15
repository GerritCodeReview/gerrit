// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.gerrit.server.query.change.OutputStreamQuery.GSON;
import static junit.framework.TestCase.assertEquals;

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDynamicOptionsTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Module;
import java.util.List;
import org.junit.Test;

@NoHttpd
@UseSsh
public class DynamicOptionsIT extends AbstractDynamicOptionsTest {

  @Override
  public Module createSshModule() {
    return new AbstractDynamicOptionsTest.PluginOneSshModule();
  }

  @Test
  public void testDynamicPluginOptions() throws Exception {
    try (AutoCloseable ignored =
        installPlugin("my-plugin", AbstractDynamicOptionsTest.PluginTwoModule.class)) {
      List<String> samples = getSamplesList(adminSshSession.exec("ls-samples"));
      adminSshSession.assertSuccess();
      assertEquals(Lists.newArrayList("sample1", "sample2"), samples);
    }
  }

  protected List<String> getSamplesList(String sshOutput) {
    return GSON.fromJson(sshOutput, new TypeToken<List<String>>() {}.getType());
  }
}
