/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-list-view/gr-list-view.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-documentation-search_html.js';
import {ListViewBehavior} from '../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.js';

/**
 * @extends Polymer.Element
 */
class GrDocumentationSearch extends mixinBehaviors( [
  ListViewBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-documentation-search'; }

  static get properties() {
    return {
    /**
     * URL params passed from the router.
     */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      _path: {
        type: String,
        readOnly: true,
        value: '/Documentation',
      },
      _documentationSearches: Array,

      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: {
        type: String,
        value: '',
      },
    };
  }

  /** @override */
  attached() {
    super.attached();
    this.dispatchEvent(
        new CustomEvent('title-change', {title: 'Documentation Search'}));
  }

  _paramsChanged(params) {
    this._loading = true;
    this._filter = this.getFilterValue(params);

    return this._getDocumentationSearches(this._filter);
  }

  _getDocumentationSearches(filter) {
    this._documentationSearches = [];
    return this.$.restAPI.getDocumentationSearches(filter)
        .then(searches => {
          // Late response.
          if (filter !== this._filter || !searches) { return; }
          this._documentationSearches = searches;
          this._loading = false;
        });
  }

  _computeSearchUrl(url) {
    if (!url) { return ''; }
    return this.getBaseUrl() + '/' + url;
  }
}

customElements.define(GrDocumentationSearch.is, GrDocumentationSearch);
