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

import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-agreements-list_html.js';
import {getBaseUrl} from '../../../utils/url-util.js';

/**
 * @extends PolymerElement
 */
class GrAgreementsList extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-agreements-list'; }

  static get properties() {
    return {
      _agreements: Array,
    };
  }

  /** @override */
  attached() {
    super.attached();
    this.loadData();
  }

  loadData() {
    return this.$.restAPI.getAccountAgreements().then(agreements => {
      this._agreements = agreements;
    });
  }

  getUrl() {
    return getBaseUrl() + '/settings/new-agreement';
  }

  getUrlBase(item) {
    return getBaseUrl() + '/' + item;
  }
}

customElements.define(GrAgreementsList.is, GrAgreementsList);
