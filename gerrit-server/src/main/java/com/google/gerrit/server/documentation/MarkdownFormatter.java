// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.documentation;

import static org.pegdown.Extensions.ALL;
import static org.pegdown.Extensions.HARDWRAPS;

import org.eclipse.jgit.util.RawParseUtils;
import org.pegdown.PegDownProcessor;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class MarkdownFormatter {
  public byte[] getHtmlFromMarkdown(byte[] data, String charEnc)
      throws UnsupportedEncodingException {
    return new PegDownProcessor(ALL & ~(HARDWRAPS))
        .markdownToHtml(RawParseUtils.decode(
            Charset.forName(charEnc),
            data))
        .getBytes(charEnc);
  }
  // TODO: Add a cache
}
