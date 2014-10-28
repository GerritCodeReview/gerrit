//Copyright (C) 2013 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.client.change;

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.PluginSafePopupPanel;

class EditFileAction {
 private final PatchSet.Id id;
 private final String content;
 private final String file;
 private final ChangeScreen2.Style style;
 private final Widget relativeTo;

 private EditFileBox editBox;
 private PopupPanel popup;

 EditFileAction(
     PatchSet.Id id,
     String content,
     String file,
     ChangeScreen2.Style style,
     Widget relativeTo) {
   this.id = id;
   this.content = content;
   this.file = file;
   this.style = style;
   this.relativeTo = relativeTo;
 }

 public void onEdit() {
   if (popup != null) {
     popup.hide();
     return;
   }

   if (editBox == null) {
     editBox = new EditFileBox(
         id,
         content,
         file);
   }

   final PluginSafePopupPanel p = new PluginSafePopupPanel(true);
   p.setStyleName(style.replyBox());
   p.addCloseHandler(new CloseHandler<PopupPanel>() {
     @Override
     public void onClose(CloseEvent<PopupPanel> event) {
       if (popup == p) {
         popup = null;
       }
     }
   });
   p.add(editBox);
   p.showRelativeTo(relativeTo);
   GlobalKey.dialog(p);
   popup = p;
 }
}
