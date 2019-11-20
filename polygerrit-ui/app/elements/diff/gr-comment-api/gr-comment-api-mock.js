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
(function() {
  'use strict';

  class CommentApiMock extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
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
      return this._reloadComments().then(() => {
        return e.detail.resolve();
      });
    }

    _reloadComments() {
      return this.$.commentAPI.loadAll(this._changeNum)
          .then(comments => {
            this._changeComments = this.$.commentAPI._changeComments;
          });
    }
  }

  customElements.define(CommentApiMock.is, CommentApiMock);
})();
