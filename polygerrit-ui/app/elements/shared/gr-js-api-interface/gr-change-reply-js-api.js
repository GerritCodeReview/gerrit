// Copyright (C) 2016 The Android Open Source Project
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
(function(window) {
  'use strict';

  /**
   * Don't add new API methods to GrChangeReplyInterfaceOld.
   * All new API should be added to GrChangeReplyInterface.
   * @deprecated
   */
  class GrChangeReplyInterfaceOld {
    constructor(el) {
      this._el = el;
    }

    getLabelValue(label) {
      return this._el.getLabelValue(label);
    }

    setLabelValue(label, value) {
      this._el.setLabelValue(label, value);
    }

    send(opt_includeComments) {
      return this._el.send(opt_includeComments);
    }
  }

  class GrChangeReplyInterface extends GrChangeReplyInterfaceOld {
    constructor(plugin, el) {
      super(el);
      this.plugin = plugin;
    }

    addReplyTextChangedCallback(handler) {
      this.plugin.getDomHook('reply-text').then(el => {
        if (!el.content) { return; }
        el.content.addEventListener('value-changed', e => {
          handler(e.detail.value);
        });
      });
    }
  }

  window.GrChangeReplyInterface = GrChangeReplyInterface;
})(window);
