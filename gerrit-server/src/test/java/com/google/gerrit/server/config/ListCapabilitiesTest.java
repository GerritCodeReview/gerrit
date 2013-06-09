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

package com.google.gerrit.server.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.config.ListCapabilities.CapabilityInfo;

import org.junit.Test;

import java.util.Map;

public class ListCapabilitiesTest {
  @Test
  public void testList() throws Exception {
    Map<String, CapabilityInfo> m =
        new ListCapabilities().apply(new ConfigResource());
    for (String id : GlobalCapability.getAllNames()) {
      assertTrue("contains " + id, m.containsKey(id));
      assertEquals(id, m.get(id).id);
      assertNotNull(id + " has name", m.get(id).name);
    }
  }
}
