/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';

export class CommentApiMock extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static setDomModuleTemplate(template) {
    if (!CommentApiMock._domModule) {
      CommentApiMock._domModule = document.createElement('dom-module');
      CommentApiMock._domModule.register(CommentApiMock.is);
    }
    CommentApiMock._domModule.innerHTML = template;
  }

  static resetDomModuleTemplate() {
    this._domModule.innerHTML = '';
  }

  static get is() { return 'comment-api-mock'; }

  static get properties() {
    return {
      _changeComments: Object,
    };
  }

  loadComments() {
    return this._reloadComments();
  }

  /**
   * For the purposes of the mock, _reloadDrafts is not included because its
   * response is the same type as reloadComments, just makes less API
   * requests. Since this is for test purposes/mocked data anyway, keep this
   * file simpler by just using _reloadComments here instead.
   */
  _reloadDraftsWithCallback(e) {
    return this._reloadComments().then(() => e.detail.resolve());
  }

  _reloadComments() {
    return this.$.commentAPI.loadAll(this._changeNum)
        .then(comments => {
          this._changeComments = this.$.commentAPI._changeComments;
        });
  }
}

export function createCommentApiMockWithTemplateElement(tagName, template) {
  const elementClass = class extends CommentApiMock {
    static get is() { return tagName; }

    static get template() { return template; }
  };
  customElements.define(tagName, elementClass);
  return elementClass;
}

customElements.define(CommentApiMock.is, CommentApiMock);
