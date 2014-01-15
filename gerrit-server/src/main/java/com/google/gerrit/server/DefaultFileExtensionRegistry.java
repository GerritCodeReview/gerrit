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

package com.google.gerrit.server;

import com.google.common.collect.ImmutableMap;

import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil;
import eu.medsea.mimeutil.detector.MimeDetector;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

public class DefaultFileExtensionRegistry extends MimeDetector {
  private static final MimeType INI = newMimeType("text/x-ini", 2);
  private static final MimeType PYTHON = newMimeType("text/x-python", 2);
  private static final MimeType PERL = newMimeType("text/x-perl", 2);
  private static final MimeType LISP = newMimeType("text/x-common-lisp", 2);

  private static final ImmutableMap<String, MimeType> TYPES =
    ImmutableMap.<String,MimeType>builder()
      .put(".gitmodules", INI)
      .put("project.config", INI)
      .put("BUCK", PYTHON)
      .put("defs", newMimeType(PYTHON.toString(), 1))
      .put("py", newMimeType(PYTHON.toString(), 1))
      .put("go", newMimeType("text/x-go", 1))
      .put("cxx", newMimeType("text/x-c++src", 1))
      .put("hxx", newMimeType("text/x-c++hdr", 1))
      .put("scala", newMimeType("text/x-scala", 1))
      .put("pl", PERL)
      .put("pm", PERL)
      .put("rb", newMimeType("text/x-ruby", 2))
      .put("cl", LISP)
      .put("el", LISP)
      .put("lisp", LISP)
      .put("lsp", LISP)
      .put("clj", newMimeType("text/x-clojure", 2))
      .put("groovy", newMimeType("text/x-groovy", 2))
      .build();

  private static MimeType newMimeType(String type, final int specificity) {
    return new MimeType(type) {
      private static final long serialVersionUID = 1L;

      @Override
      public int getSpecificity() {
        return specificity;
      }
    };
  }

  static {
    for (MimeType type : TYPES.values()) {
      MimeUtil.addKnownMimeType(type);
    }
  }

  @Override
  public String getDescription() {
    return getClass().getName();
  }

  @Override
  protected Collection<MimeType> getMimeTypesFileName(String name) {
    int s = name.lastIndexOf('/');
    if (s >= 0) {
      name = name.substring(s + 1);
    }

    MimeType type = TYPES.get(name);
    if (type != null) {
      return Collections.singletonList(type);
    }

    int d = name.lastIndexOf('.');
    if (0 < d) {
      type = TYPES.get(name.substring(d + 1));
      if (type != null) {
        return Collections.singletonList(type);
      }
    }

    return Collections.emptyList();
  }

  @Override
  protected Collection<MimeType> getMimeTypesFile(File file) {
    return getMimeTypesFileName(file.getName());
  }

  @Override
  protected Collection<MimeType> getMimeTypesURL(URL url) {
    return getMimeTypesFileName(url.getPath());
  }

  @Override
  protected Collection<MimeType> getMimeTypesInputStream(InputStream arg0) {
    return Collections.emptyList();
  }

  @Override
  protected Collection<MimeType> getMimeTypesByteArray(byte[] arg0) {
    return Collections.emptyList();
  }
}
