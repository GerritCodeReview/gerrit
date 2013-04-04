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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.data.GerritConfig.CommentLink;
import com.google.gwtexpui.safehtml.client.FindReplace;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommentLinkProcessor {
  public static SafeHtml apply(SafeHtml buf) {
    try {
      return buf.replaceAll(Gerrit.getConfig().getCommentLinks());

    } catch (RuntimeException err) {
      // One or more of the patterns isn't valid on this browser.
      // Try to filter the list down and remove the invalid ones.

      List<FindReplace> all = Gerrit.getConfig().getCommentLinks();
      List<CommentLink> ser = Gerrit.getConfig().getSerializableCommentLinks();

      List<FindReplace> safe = new ArrayList<FindReplace>(all.size());
      List<CommentLink> safeSer = new ArrayList<CommentLink>(safe.size());

      List<PatternError> bad = new ArrayList<PatternError>();
      for (int i = 0; i < all.size(); i++) {
        FindReplace r = all.get(i);
        try {
          buf.replaceAll(Collections.singletonList(r));
          safe.add(r);
          safeSer.add(ser.get(i));
        } catch (RuntimeException why) {
          bad.add(new PatternError(r, why.getMessage()));
        }
      }

      if (!bad.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        msg.append("Invalid commentlink pattern(s):");
        for (PatternError e : bad) {
          msg.append("\n");
          msg.append("\"");
          msg.append(e.pattern.pattern().getSource());
          msg.append("\": ");
          msg.append(e.errorMessage);
        }
        Gerrit.SYSTEM_SVC.clientError(msg.toString(),
            new AsyncCallback<VoidResult>() {
              @Override
              public void onFailure(Throwable caught) {
              }

              @Override
              public void onSuccess(VoidResult result) {
              }
            });
      }

      try {
        Gerrit.getConfig().setSerializableCommentLinks(safeSer);
        return buf.replaceAll(safe);
      } catch (RuntimeException err2) {
        // To heck with it. The patterns passed individually above but
        // failed as a group? Just render without.
        //
        Gerrit.getConfig().setSerializableCommentLinks(null);
        return buf;
      }
    }
  }

  private static class PatternError {
    FindReplace pattern;
    String errorMessage;

    PatternError(FindReplace r, String w) {
      pattern = r;
      errorMessage = w;
    }
  }

  private CommentLinkProcessor() {
  }
}
