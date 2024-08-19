/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

public class GetPatchIT extends AbstractDaemonTest {
  @Test
  public void patchFormatsAreEquivalent() throws Exception {
    interface TestVariant {
      String getUrlParam();

      String decode(ByteBuffer buf);
    }

    TestVariant[] variants = {
      // Plain text
      new TestVariant() {
        @Override
        public String getUrlParam() {
          return "?raw";
        }

        @Override
        public String decode(ByteBuffer buf) {
          // Simply utf-8 decode it
          return RawParseUtils.decode(buf.array(), buf.arrayOffset(), buf.limit());
        }
      },
      // Base64 obfuscation
      new TestVariant() {
        @Override
        public String getUrlParam() {
          return "";
        }

        @Override
        public String decode(ByteBuffer buf) {
          var asUtf8Base64 = RawParseUtils.decode(buf.array(), buf.arrayOffset(), buf.limit());
          var decoded = BaseEncoding.base64().decode(asUtf8Base64);
          return RawParseUtils.decode(decoded);
        }
      },
      // ZIP obfuscation
      new TestVariant() {
        @Override
        public String getUrlParam() {
          return "?zip";
        }

        @Override
        public String decode(ByteBuffer buf) {
          var unzipper =
              new ZipInputStream(
                  new ByteArrayInputStream(buf.array(), buf.arrayOffset(), buf.limit()));
          var entries = new HashMap<String, String>();

          try {
            ZipEntry nextEntry = unzipper.getNextEntry();
            while (nextEntry != null) {
              var name = nextEntry.getName();
              entries.put(name, RawParseUtils.decode(unzipper.readAllBytes()));

              nextEntry = unzipper.getNextEntry();
            }
          } catch (IOException ioe) {
            throw new RuntimeException("got io exception doing in-memory io, wat", ioe);
          }

          assertThat(entries.size()).isEqualTo(1);

          var content = entries.values().stream().findFirst();
          assertThat(content.isPresent()).isTrue();

          return content.get();
        }
      },
    };

    String fileName = "a_new_file.txt";
    String fileContent = "First line\nSecond line\n";
    PushOneCommit.Result result = createChange("Add a file", fileName, fileContent);
    String triplet = project.get() + "~master~" + result.getChangeId();

    String patch = null;
    for (var variant : variants) {
      var apiResult =
          userRestSession.get("/changes/" + triplet + "/revisions/1/patch" + variant.getUrlParam());
      apiResult.assertOK();
      var resultingPatch = variant.decode(apiResult.getRawContent());

      if (patch == null) {
        patch = resultingPatch;
      }
      assertThat(resultingPatch).isEqualTo(patch);
    }
  }
}
