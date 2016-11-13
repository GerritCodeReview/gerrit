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

package com.google.gerrit.server.mime;

import com.google.common.collect.ImmutableMap;
import eu.medsea.mimeutil.MimeException;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil;
import eu.medsea.mimeutil.detector.MimeDetector;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads mime types from {@code mime-types.properties} at specificity of 2. */
public class DefaultFileExtensionRegistry extends MimeDetector {
  private static final Logger log = LoggerFactory.getLogger(DefaultFileExtensionRegistry.class);
  private static final ImmutableMap<String, MimeType> TYPES;

  static {
    Properties prop = new Properties();
    try (InputStream in =
        DefaultFileExtensionRegistry.class.getResourceAsStream("mime-types.properties")) {
      prop.load(in);
    } catch (IOException e) {
      log.warn("Cannot load mime-types.properties", e);
    }

    ImmutableMap.Builder<String, MimeType> b = ImmutableMap.builder();
    for (Map.Entry<Object, Object> e : prop.entrySet()) {
      MimeType type = new FileExtensionMimeType((String) e.getValue());
      b.put((String) e.getKey(), type);
      MimeUtil.addKnownMimeType(type);
    }
    TYPES = b.build();
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

  private static final class FileExtensionMimeType extends MimeType {
    private static final long serialVersionUID = 1L;

    FileExtensionMimeType(String mimeType) throws MimeException {
      super(mimeType);
    }

    @Override
    public int getSpecificity() {
      return 2;
    }
  }
}
