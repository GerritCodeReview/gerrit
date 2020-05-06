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

/**
 * GrChangeReplyInterface, provides a set of handy methods on reply dialog.
 */
export class GrChangeReplyInterface {
  constructor(plugin) {
    this.plugin = plugin;
    this._sharedApiEl = Plugin._sharedAPIElement;
  }

  get _el() {
    return this._sharedApiEl.getElement(
        this._sharedApiEl.Element.REPLY_DIALOG);
  }

  getLabelValue(label) {
    return this._el.getLabelValue(label);
  }

  setLabelValue(label, value) {
    this._el.setLabelValue(label, value);
  }

  send(opt_includeComments) {
    this._el.send(opt_includeComments);
  }

  addReplyTextChangedCallback(handler) {
    const hookApi = this.plugin.hook('reply-text');
    const registeredHandler = e => handler(e.detail.value);
    hookApi.onAttached(el => {
      if (!el.content) { return; }
      el.content.addEventListener('value-changed', registeredHandler);
    });
    hookApi.onDetached(el => {
      if (!el.content) { return; }
      el.content.removeEventListener('value-changed', registeredHandler);
    });
  }

  addLabelValuesChangedCallback(handler) {
    const hookApi = this.plugin.hook('reply-label-scores');
    const registeredHandler = e => handler(e.detail);
    hookApi.onAttached(el => {
      if (!el.content) { return; }
      el.content.addEventListener('labels-changed', registeredHandler);
    });

    hookApi.onDetached(el => {
      if (!el.content) { return; }
      el.content.removeEventListener('labels-changed', registeredHandler);
    });
  }

  showMessage(message) {
    return this._el.setPluginMessage(message);
  }
}
