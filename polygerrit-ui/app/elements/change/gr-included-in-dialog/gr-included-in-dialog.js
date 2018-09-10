/**
@license
Copyright (C) 2018 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../styles/shared-styles.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        background-color: var(--dialog-background-color);
        display: block;
        max-height: 80vh;
        overflow-y: auto;
        padding: 4.5em 1em 1em 1em;
      }
      header {
        background-color: var(--dialog-background-color);
        border-bottom: 1px solid var(--border-color);
        left: 0;
        padding: 1em;
        position: absolute;
        right: 0;
        top: 0;
      }
      #title {
        display: inline-block;
        font-size: 1.2rem;
        margin-top: .2em;
      }
      h2 {
        font-size: 1rem;
      }
      #filterInput {
        display: inline-block;
        float: right;
        margin: 0 1em;
        padding: .2em;
      }
      .closeButtonContainer {
        float: right;
      }
      ul {
        margin-bottom: 1em;
      }
      ul li {
        border: 1px solid var(--border-color);
        border-radius: .2em;
        background: var(--chip-background-color);
        display: inline-block;
        margin: 0 .2em .4em .2em;
        padding: .2em .4em;
      }
      .loading.loaded {
        display: none;
      }
    </style>
    <header>
      <h1 id="title">Included In:</h1>
      <span class="closeButtonContainer">
        <gr-button id="closeButton" link="" on-tap="_handleCloseTap">Close</gr-button>
      </span>
      <input id="filterInput" is="iron-input" placeholder="Filter" on-bind-value-changed="_onFilterChanged">
    </header>
    <div class\$="[[_computeLoadingClass(_loaded)]]">Loading...</div>
    <template is="dom-repeat" items="[[_computeGroups(_includedIn, _filterText)]]" as="group">
      <div>
        <h2>[[group.title]]:</h2>
        <ul>
          <template is="dom-repeat" items="[[group.items]]">
            <li>[[item]]</li>
          </template>
        </ul>
      </div>
    </template>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-included-in-dialog',

  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  properties: {
    /** @type {?} */
    changeNum: {
      type: Object,
      observer: '_resetData',
    },
    /** @type {?} */
    _includedIn: Object,
    _loaded: {
      type: Boolean,
      value: false,
    },
    _filterText: {
      type: String,
      value: '',
    },
  },

  loadData() {
    if (!this.changeNum) { return; }
    this._filterText = '';
    return this.$.restAPI.getChangeIncludedIn(this.changeNum).then(
        configs => {
          if (!configs) { return; }
          this._includedIn = configs;
          this._loaded = true;
        });
  },

  _resetData() {
    this._includedIn = null;
    this._loaded = false;
  },

  _computeGroups(includedIn, filterText) {
    if (!includedIn) { return []; }

    const filter = item => !filterText.length ||
        item.toLowerCase().indexOf(filterText.toLowerCase()) !== -1;

    const groups = [
      {title: 'Branches', items: includedIn.branches.filter(filter)},
      {title: 'Tags', items: includedIn.tags.filter(filter)},
    ];
    if (includedIn.external) {
      for (const externalKey of Object.keys(includedIn.external)) {
        groups.push({
          title: externalKey,
          items: includedIn.external[externalKey].filter(filter),
        });
      }
    }
    return groups.filter(g => g.items.length);
  },

  _handleCloseTap(e) {
    e.preventDefault();
    this.fire('close', null, {bubbles: false});
  },

  _computeLoadingClass(loaded) {
    return loaded ? 'loading loaded' : 'loading';
  },

  _onFilterChanged() {
    this.debounce('filter-change', () => {
      this._filterText = this.$.filterInput.bindValue;
    }, 100);
  }
});
