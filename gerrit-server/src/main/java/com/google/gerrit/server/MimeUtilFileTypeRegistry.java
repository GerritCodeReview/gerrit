// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.util.HostPlatform;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import eu.medsea.mimeutil.MimeException;
import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class MimeUtilFileTypeRegistry implements FileTypeRegistry {
  private static final String KEY_SAFE = "safe";
  private static final String SECTION_MIMETYPE = "mimetype";
  private static final Logger log =
      LoggerFactory.getLogger(MimeUtilFileTypeRegistry.class);

  private final Config cfg;
  private MimeUtil2 mimeUtil;

  @Inject
  MimeUtilFileTypeRegistry(@GerritServerConfig final Config gsc) {
    cfg = gsc;
    mimeUtil = new MimeUtil2();
    register("eu.medsea.mimeutil.detector.ExtensionMimeDetector");
    register("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
    if (HostPlatform.isWin32()) {
      register("eu.medsea.mimeutil.detector.WindowsRegistryMimeDetector");
    }
  }

  private void register(String name) {
    mimeUtil.registerMimeDetector(name);
  }


  /**
   * Get specificity of mime types with generic types forced to low values
   *
   * "application/octet-stream" is forced to -1.
   * "text/plain" is forced to 0.
   * All other mime types return the specificity reported by mimeType itself.
   *
   * @param mimeType The mimeType to get the corrected specificity for.
   * @return The corrected specificity.
   */
  private int getCorrectedMimeSpecificity(MimeType mimeType) {
    // Although the documentation of MimeType's getSpecificity claims that for
    // example "application/octet-stream" always has a specificity of 0, it
    // effectively returns 1 for us. This causes problems when trying to get
    // the correct mime type via sorting. For example in
    // [application/octet-stream, image/x-icon] both mime types come with
    // specificity 1 for us. Hence, getMimeType below may end up using
    // application/octet-stream instead of the more specific image/x-icon.
    // Therefore, we have to force the specificity of generic types below the
    // default of 1.
    //
    final String mimeTypeStr = mimeType.toString();
    if (mimeTypeStr.equals("application/octet-stream")) {
      return -1;
    }
    if (mimeTypeStr.equals("text/plain")) {
      return 0;
    }
    return mimeType.getSpecificity();
  }

  @SuppressWarnings("unchecked")
  public MimeType getMimeType(final String path, final byte[] content) {
    Set<MimeType> mimeTypes = new HashSet<MimeType>();
    if (content != null && content.length > 0) {
      try {
        mimeTypes.addAll(mimeUtil.getMimeTypes(content));
      } catch (MimeException e) {
        log.warn("Unable to determine MIME type from content", e);
      }
    }
    try {
      mimeTypes.addAll(mimeUtil.getMimeTypes(path));
    } catch (MimeException e) {
      log.warn("Unable to determine MIME type from path", e);
    }

    if (isUnknownType(mimeTypes)) {
      return MimeUtil2.UNKNOWN_MIME_TYPE;
    }

    final List<MimeType> types = new ArrayList<MimeType>(mimeTypes);
    Collections.sort(types, new Comparator<MimeType>() {
      @Override
      public int compare(MimeType a, MimeType b) {
        return getCorrectedMimeSpecificity(b) - getCorrectedMimeSpecificity(a);
      }
    });
    return types.get(0);
  }

  public boolean isSafeInline(final MimeType type) {
    if (MimeUtil2.UNKNOWN_MIME_TYPE.equals(type)) {
      // Most browsers perform content type sniffing when they get told
      // a generic content type. This is bad, so assume we cannot send
      // the file inline.
      //
      return false;
    }

    final boolean any = isSafe(cfg, "*/*", false);
    final boolean genericMedia = isSafe(cfg, type.getMediaType() + "/*", any);
    return isSafe(cfg, type.toString(), genericMedia);
  }

  private static boolean isSafe(Config cfg, String type, boolean def) {
    return cfg.getBoolean(SECTION_MIMETYPE, type, KEY_SAFE, def);
  }

  private static boolean isUnknownType(Collection<MimeType> mimeTypes) {
    if (mimeTypes.isEmpty()) {
      return true;
    }
    return mimeTypes.size() == 1
        && mimeTypes.contains(MimeUtil2.UNKNOWN_MIME_TYPE);
  }
}
