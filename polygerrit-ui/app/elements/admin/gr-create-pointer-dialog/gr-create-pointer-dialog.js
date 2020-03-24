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
import '../../../scripts/bundled-polymer.js';

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-create-pointer-dialog_html.js';
import page from 'page/page.mjs';

const DETAIL_TYPES = {
  branches: 'branches',
  tags: 'tags',
};

/**
 * @appliesMixin Gerrit.BaseUrlMixin
 * @appliesMixin Gerrit.URLEncodingMixin
 * @extends Polymer.Element
 */
class GrCreatePointerDialog extends mixinBehaviors( [
  Gerrit.BaseUrlBehavior,
  Gerrit.URLEncodingBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-create-pointer-dialog'; }

  static get properties() {
    return {
      detailType: String,
      repoName: String,
      hasNewItemName: {
        type: Boolean,
        notify: true,
        value: false,
      },
      itemDetail: String,
      _itemName: String,
      _itemRevision: String,
      _itemAnnotation: String,
    };
  }

  static get observers() {
    return [
      '_updateItemName(_itemName)',
    ];
  }

  _updateItemName(name) {
    this.hasNewItemName = !!name;
  }

  _computeItemUrl(project) {
    if (this.itemDetail === DETAIL_TYPES.branches) {
      return this.getBaseUrl() + '/admin/repos/' +
          this.encodeURL(this.repoName, true) + ',branches';
    } else if (this.itemDetail === DETAIL_TYPES.tags) {
      return this.getBaseUrl() + '/admin/repos/' +
          this.encodeURL(this.repoName, true) + ',tags';
    }
  }

  handleCreateItem() {
    const USE_HEAD = this._itemRevision ? this._itemRevision : 'HEAD';
    if (this.itemDetail === DETAIL_TYPES.branches) {
      return this.$.restAPI.createRepoBranch(this.repoName,
          this._itemName, {revision: USE_HEAD})
          .then(itemRegistered => {
            if (itemRegistered.status === 201) {
              page.show(this._computeItemUrl(this.itemDetail));
            }
          });
    } else if (this.itemDetail === DETAIL_TYPES.tags) {
      return this.$.restAPI.createRepoTag(this.repoName,
          this._itemName,
          {revision: USE_HEAD, message: this._itemAnnotation || null})
          .then(itemRegistered => {
            if (itemRegistered.status === 201) {
              page.show(this._computeItemUrl(this.itemDetail));
            }
          });
    }
  }

  _computeHideItemClass(type) {
    return type === DETAIL_TYPES.branches ? 'hideItem' : '';
  }
}

customElements.define(GrCreatePointerDialog.is, GrCreatePointerDialog);
