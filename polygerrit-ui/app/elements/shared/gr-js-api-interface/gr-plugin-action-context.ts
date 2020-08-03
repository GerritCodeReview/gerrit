/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PatchSetNum} from '../../../types/common';


export function GrPluginActionContext(plugin, action, change, revision: PatchSetNum) {
  this.action = action;
  this.plugin = plugin;
  this.change = change;
  this.revision = revision;
  this._popups = [];
}

GrPluginActionContext.prototype.popup = function (element) {
  this._popups.push(this.plugin.deprecated.popup(element));
};

GrPluginActionContext.prototype.hide = function () {
  for (const popupApi of this._popups) {
    popupApi.close();
  }
  this._popups.splice(0);
};

GrPluginActionContext.prototype.refresh = function () {
  window.location.reload();
};

GrPluginActionContext.prototype.textfield = function () {
  return document.createElement('paper-input');
};

GrPluginActionContext.prototype.br = function () {
  return document.createElement('br');
};

GrPluginActionContext.prototype.msg = function (text: string) {
  const label = document.createElement('gr-label');
  dom(label).appendChild(document.createTextNode(text));
  return label;
};

GrPluginActionContext.prototype.div = function (...els) {
  const div = document.createElement('div');
  for (const el of els) {
    dom(div).appendChild(el);
  }
  return div;
};

GrPluginActionContext.prototype.button = function (label: string, callbacks) {
  const onClick = callbacks && callbacks.onclick;
  const button = document.createElement('gr-button');
  dom(button).appendChild(document.createTextNode(label));
  if (onClick) {
    this.plugin.eventHelper(button).onTap(onClick);
  }
  return button;
};

GrPluginActionContext.prototype.checkbox = function () {
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  return checkbox;
};

GrPluginActionContext.prototype.label = function (checkbox, title) {
  return this.div(checkbox, this.msg(title));
};

GrPluginActionContext.prototype.prependLabel = function (title, checkbox) {
  return this.label(checkbox, title);
};

GrPluginActionContext.prototype.call = function (payload, onSuccess) {
  if (!this.action.__url) {
    console.warn(`Unable to ${this.action.method} to ${this.action.__key}!`);
    return;
  }
  this.plugin
    .restApi()
    .send(this.action.method, this.action.__url, payload)
    .then(onSuccess)
    .catch(error => {
      document.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {
            message: `Plugin network error: ${error}`,
          },
        })
      );
    });
};
