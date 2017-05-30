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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.AccountInfo.AvatarInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.info.ChangeInfo.LabelInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Displays a table of label and reviewer scores. */
class Labels extends Grid {
  private static final String DATA_ID = "data-id";
  private static final String DATA_VOTE = "data-vote";
  private static final String REMOVE_REVIEWER;
  private static final String REMOVE_VOTE;

  static {
    REMOVE_REVIEWER = DOM.createUniqueId().replace('-', '_');
    REMOVE_VOTE = DOM.createUniqueId().replace('-', '_');
    init(REMOVE_REVIEWER, REMOVE_VOTE);
  }

  private static native void init(String r, String v) /*-{
    $wnd[r] = $entry(function(e) {
      @com.google.gerrit.client.change.Labels::onRemoveReviewer(Lcom/google/gwt/dom/client/NativeEvent;)(e)
    });
    $wnd[v] = $entry(function(e) {
      @com.google.gerrit.client.change.Labels::onRemoveVote(Lcom/google/gwt/dom/client/NativeEvent;)(e)
    });
  }-*/;

  private static void onRemoveReviewer(NativeEvent event) {
    Integer user = getDataId(event);
    if (user != null) {
      final ChangeScreen screen = ChangeScreen.get(event);
      final Change.Id changeId = screen.getPatchSetId().getParentKey();
      ChangeApi.reviewer(Project.NameKey.asStringOrNull(screen.getProject()), changeId.get(), user)
          .delete(
              new GerritCallback<JavaScriptObject>() {
                @Override
                public void onSuccess(JavaScriptObject result) {
                  if (screen.isCurrentView()) {
                    Gerrit.display(PageLinks.toChange(screen.getProject(), changeId));
                  }
                }
              });
    }
  }

  private static void onRemoveVote(NativeEvent event) {
    Integer user = getDataId(event);
    String vote = getVoteId(event);
    if (user != null && vote != null) {
      final ChangeScreen screen = ChangeScreen.get(event);
      final Change.Id changeId = screen.getPatchSetId().getParentKey();
      ChangeApi.vote(
              Project.NameKey.asStringOrNull(screen.getProject()), changeId.get(), user, vote)
          .delete(
              new GerritCallback<JavaScriptObject>() {
                @Override
                public void onSuccess(JavaScriptObject result) {
                  if (screen.isCurrentView()) {
                    Gerrit.display(PageLinks.toChange(screen.getProject(), changeId));
                  }
                }
              });
    }
  }

  private static Integer getDataId(NativeEvent event) {
    Element e = event.getEventTarget().cast();
    while (e != null) {
      String v = e.getAttribute(DATA_ID);
      if (!v.isEmpty()) {
        return Integer.parseInt(v);
      }
      e = e.getParentElement();
    }
    return null;
  }

  private static String getVoteId(NativeEvent event) {
    Element e = event.getEventTarget().cast();
    while (e != null) {
      String v = e.getAttribute(DATA_VOTE);
      if (!v.isEmpty()) {
        return v;
      }
      e = e.getParentElement();
    }
    return null;
  }

  private ChangeScreen.Style style;

  void init(ChangeScreen.Style style) {
    this.style = style;
  }

  void set(ChangeInfo info) {
    List<String> names = new ArrayList<>(info.labels());
    Set<Integer> removable = info.removableReviewerIds();
    Collections.sort(names);

    resize(names.size(), 2);

    for (int row = 0; row < names.size(); row++) {
      String name = names.get(row);
      LabelInfo label = info.label(name);
      setText(row, 0, name);
      if (label.all() != null) {
        setWidget(row, 1, renderUsers(label, removable));
      }
      getCellFormatter().setStyleName(row, 0, style.labelName());
      getCellFormatter().addStyleName(row, 0, getStyleForLabel(label));
    }
  }

  private Widget renderUsers(LabelInfo label, Set<Integer> removable) {
    Map<Integer, List<ApprovalInfo>> m = new HashMap<>(4);
    int approved = 0;
    int rejected = 0;

    for (ApprovalInfo ai : Natives.asList(label.all())) {
      if (ai.value() != 0) {
        List<ApprovalInfo> l = m.get(Integer.valueOf(ai.value()));
        if (l == null) {
          l = new ArrayList<>(label.all().length());
          m.put(Integer.valueOf(ai.value()), l);
        }
        l.add(ai);

        if (isRejected(label, ai)) {
          rejected = ai.value();
        } else if (isApproved(label, ai)) {
          approved = ai.value();
        }
      }
    }

    SafeHtmlBuilder html = new SafeHtmlBuilder();
    for (Integer v : sort(m.keySet(), approved, rejected)) {
      if (!html.isEmpty()) {
        html.br();
      }

      String val = LabelValue.formatValue(v.shortValue());
      html.openSpan();
      html.setAttribute("title", label.valueText(val));
      if (v.intValue() == approved) {
        html.setStyleName(style.label_ok());
      } else if (v.intValue() == rejected) {
        html.setStyleName(style.label_reject());
      }
      html.append(val).append(" ");
      html.append(formatUserList(style, m.get(v), removable, label.name(), null));
      html.closeSpan();
    }
    return html.toBlockWidget();
  }

  private static List<Integer> sort(Set<Integer> keySet, int a, int b) {
    List<Integer> r = new ArrayList<>(keySet);
    Collections.sort(r);
    if (keySet.contains(a)) {
      r.remove(Integer.valueOf(a));
      r.add(0, a);
    } else if (keySet.contains(b)) {
      r.remove(Integer.valueOf(b));
      r.add(0, b);
    }
    return r;
  }

  private static boolean isApproved(LabelInfo label, ApprovalInfo ai) {
    return label.approved() != null && label.approved()._accountId() == ai._accountId();
  }

  private static boolean isRejected(LabelInfo label, ApprovalInfo ai) {
    return label.rejected() != null && label.rejected()._accountId() == ai._accountId();
  }

  private String getStyleForLabel(LabelInfo label) {
    switch (label.status()) {
      case OK:
        return style.label_ok();
      case NEED:
        return style.label_need();
      case REJECT:
      case IMPOSSIBLE:
        return style.label_reject();
      default:
      case MAY:
        return style.label_may();
    }
  }

  static SafeHtml formatUserList(
      ChangeScreen.Style style,
      Collection<? extends AccountInfo> in,
      Set<Integer> removable,
      String label,
      Map<Integer, VotableInfo> votable) {
    List<AccountInfo> users = new ArrayList<>(in);
    Collections.sort(
        users,
        new Comparator<AccountInfo>() {
          @Override
          public int compare(AccountInfo a, AccountInfo b) {
            String as = name(a);
            String bs = name(b);
            if (as.isEmpty()) {
              return 1;
            } else if (bs.isEmpty()) {
              return -1;
            }
            return as.compareTo(bs);
          }

          private String name(AccountInfo a) {
            if (a.name() != null) {
              return a.name();
            } else if (a.email() != null) {
              return a.email();
            }
            return "";
          }
        });

    SafeHtmlBuilder html = new SafeHtmlBuilder();
    Iterator<? extends AccountInfo> itr = users.iterator();
    while (itr.hasNext()) {
      AccountInfo ai = itr.next();
      AvatarInfo img = ai.avatar(AvatarInfo.DEFAULT_SIZE);
      String name;
      if (ai.name() != null) {
        name = ai.name();
      } else if (ai.email() != null) {
        name = ai.email();
      } else {
        name = Integer.toString(ai._accountId());
      }

      String votableCategories = "";
      if (votable != null) {
        VotableInfo vi = votable.get(ai._accountId());
        if (vi != null) {
          Set<String> s = vi.votableLabels();
          if (!s.isEmpty()) {
            StringBuilder sb = new StringBuilder(Util.C.votable());
            sb.append(" ");
            for (Iterator<String> it = vi.votableLabels().iterator(); it.hasNext(); ) {
              sb.append(it.next());
              if (it.hasNext()) {
                sb.append(", ");
              }
            }
            votableCategories = sb.toString();
          }
        }
      }
      html.openSpan()
          .setAttribute("role", "listitem")
          .setAttribute(DATA_ID, ai._accountId())
          .setAttribute("title", getTitle(ai, votableCategories))
          .setStyleName(style.label_user());
      if (label != null) {
        html.setAttribute(DATA_VOTE, label);
      }
      if (img != null) {
        html.openElement("img").setStyleName(style.avatar()).setAttribute("src", img.url());
        if (img.width() > 0) {
          html.setAttribute("width", img.width());
        }
        if (img.height() > 0) {
          html.setAttribute("height", img.height());
        }
        html.closeSelf();
      }
      html.append(name);
      if (removable.contains(ai._accountId())) {
        html.openElement("button");
        if (label != null) {
          html.setAttribute("title", Util.M.removeVote(label))
              .setAttribute("onclick", REMOVE_VOTE + "(event)");
        } else {
          html.setAttribute("title", Util.M.removeReviewer(name))
              .setAttribute("onclick", REMOVE_REVIEWER + "(event)");
        }
        html.append("×").closeElement("button");
      }
      html.closeSpan();
      if (itr.hasNext()) {
        html.append(' ');
      }
    }
    return html;
  }

  private static String getTitle(AccountInfo ai, String votableCategories) {
    String title = ai.email() != null ? ai.email() : "";
    if (!votableCategories.isEmpty()) {
      if (!title.isEmpty()) {
        title += " ";
      }
      title += votableCategories;
    }
    return title;
  }
}
