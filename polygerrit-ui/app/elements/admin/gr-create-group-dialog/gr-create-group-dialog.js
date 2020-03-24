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

import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-create-group-dialog_html.js';
import page from 'page/page.mjs';
import {BaseUrlBehavior} from '../../../behaviors/base-url-behavior/base-url-behavior.js';

/**
 * @appliesMixin Gerrit.BaseUrlMixin
 * @appliesMixin Gerrit.URLEncodingMixin
 * @extends Polymer.Element
 */
class GrCreateGroupDialog extends mixinBehaviors( [
  BaseUrlBehavior,
  Gerrit.URLEncodingBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-create-group-dialog'; }

  static get properties() {
    return {
      params: Object,
      hasNewGroupName: {
        type: Boolean,
        notify: true,
        value: false,
      },
      _name: Object,
      _groupCreated: {
        type: Boolean,
        value: false,
      },
    };
  }

  static get observers() {
    return [
      '_updateGroupName(_name)',
    ];
  }

  _computeGroupUrl(groupId) {
    return this.getBaseUrl() + '/admin/groups/' +
        this.encodeURL(groupId, true);
  }

  _updateGroupName(name) {
    this.hasNewGroupName = !!name;
  }

  handleCreateGroup() {
    return this.$.restAPI.createGroup({name: this._name})
        .then(groupRegistered => {
          if (groupRegistered.status !== 201) { return; }
          this._groupCreated = true;
          return this.$.restAPI.getGroupConfig(this._name)
              .then(group => {
                page.show(this._computeGroupUrl(group.group_id));
              });
        });
  }
}

customElements.define(GrCreateGroupDialog.is, GrCreateGroupDialog);
