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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import java.util.List;
import java.util.regex.Matcher;
import org.eclipse.jgit.revwalk.FooterLine;

public class TrackingFooters {
  protected List<TrackingFooter> trackingFooters;

  public TrackingFooters(List<TrackingFooter> trFooters) {
    trackingFooters = trFooters;
  }

  public List<TrackingFooter> getTrackingFooters() {
    return trackingFooters;
  }

  public boolean isEmpty() {
    return trackingFooters.isEmpty();
  }

  public ListMultimap<String, String> extract(List<FooterLine> lines) {
    ListMultimap<String, String> r = MultimapBuilder.hashKeys().arrayListValues().build();
    if (lines == null) {
      return r;
    }

    for (FooterLine footer : lines) {
      for (TrackingFooter config : trackingFooters) {
        if (footer.matches(config.footerKey())) {
          Matcher m = config.match().matcher(footer.getValue());
          while (m.find()) {
            String id = m.groupCount() > 0 ? m.group(1) : m.group();
            if (!id.isEmpty()) {
              r.put(config.system(), id);
            }
          }
        }
      }
    }
    return r;
  }
}
