/**
@license
Copyright (C) 2017 The Android Open Source Project

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

import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/gr-subpage-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-copy-clipboard/gr-copy-clipboard.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-select/gr-select.js';

const INTERNAL_GROUP_REGEX = /^[\da-f]{40}$/;

const OPTIONS = {
  submitFalse: {
    value: false,
    label: 'False',
  },
  submitTrue: {
    value: true,
    label: 'True',
  },
};

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-subpage-styles">
      h3.edited:after {
        color: var(--deemphasized-text-color);
        content: ' *';
      }
      .inputUpdateBtn {
        margin-top: .3em;
      }
    </style>
    <style include="gr-form-styles"></style>
    <main class="gr-form-styles read-only">
      <div id="loading" class\$="[[_computeLoadingClass(_loading)]]">
        Loading...
      </div>
      <div id="loadedContent" class\$="[[_computeLoadingClass(_loading)]]">
        <h1 id="Title">[[_groupName]]</h1>
        <h2 id="configurations">General</h2>
        <div id="form">
          <fieldset>
            <h3 id="groupUUID">Group UUID</h3>
            <fieldset>
              <gr-copy-clipboard text="[[_groupConfig.id]]"></gr-copy-clipboard>
            </fieldset>
            <h3 id="groupName" class\$="[[_computeHeaderClass(_rename)]]">
              Group Name
            </h3>
            <fieldset>
              <span class="value">
                <gr-autocomplete id="groupNameInput" text="{{_groupConfig.name}}" disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"></gr-autocomplete>
              </span>
              <span class="value" disabled\$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button id="inputUpdateNameBtn" on-tap="_handleSaveName" disabled="[[!_rename]]">
                  Rename Group</gr-button>
              </span>
            </fieldset>
            <h3 class\$="[[_computeHeaderClass(_owner)]]">
              Owners
            </h3>
            <fieldset>
              <span class="value">
                <gr-autocomplete text="{{_groupConfig.owner}}" query="[[_query]]" disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                </gr-autocomplete>
              </span>
              <span class="value" disabled\$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button on-tap="_handleSaveOwner" disabled="[[!_owner]]">
                  Change Owners</gr-button>
              </span>
            </fieldset>
            <h3 class\$="[[_computeHeaderClass(_description)]]">
              Description
            </h3>
            <fieldset>
              <div>
                <iron-autogrow-textarea class="description" autocomplete="on" bind-value="{{_groupConfig.description}}" disabled="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]"></iron-autogrow-textarea>
              </div>
              <span class="value" disabled\$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button on-tap="_handleSaveDescription" disabled="[[!_description]]">
                  Save Description
                </gr-button>
              </span>
            </fieldset>
            <h3 id="options" class\$="[[_computeHeaderClass(_options)]]">
              Group Options
            </h3>
            <fieldset id="visableToAll">
              <section>
                <span class="title">
                  Make group visible to all registered users
                </span>
                <span class="value">
                  <gr-select id="visibleToAll" bind-value="{{_groupConfig.options.visible_to_all}}">
                    <select disabled\$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                      <template is="dom-repeat" items="[[_submitTypes]]">
                        <option value="[[item.value]]">[[item.label]]</option>
                      </template>
                    </select>
                  </gr-select>
                </span>
              </section>
              <span class="value" disabled\$="[[_computeGroupDisabled(_groupOwner, _isAdmin, _groupIsInternal)]]">
                <gr-button on-tap="_handleSaveOptions" disabled="[[!_options]]">
                  Save Group Options
                </gr-button>
              </span>
            </fieldset>
          </fieldset>
        </div>
      </div>
    </main>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-group',

  /**
   * Fired when the group name changes.
   *
   * @event name-changed
   */

  properties: {
    groupId: Number,
    _rename: {
      type: Boolean,
      value: false,
    },
    _groupIsInternal: Boolean,
    _description: {
      type: Boolean,
      value: false,
    },
    _owner: {
      type: Boolean,
      value: false,
    },
    _options: {
      type: Boolean,
      value: false,
    },
    _loading: {
      type: Boolean,
      value: true,
    },
    /** @type {?} */
    _groupConfig: Object,
    _groupName: Object,
    _groupOwner: {
      type: Boolean,
      value: false,
    },
    _submitTypes: {
      type: Array,
      value() {
        return Object.values(OPTIONS);
      },
    },
    _query: {
      type: Function,
      value() {
        return this._getGroupSuggestions.bind(this);
      },
    },
    _isAdmin: {
      type: Boolean,
      value: false,
    },
  },

  observers: [
    '_handleConfigName(_groupConfig.name)',
    '_handleConfigOwner(_groupConfig.owner)',
    '_handleConfigDescription(_groupConfig.description)',
    '_handleConfigOptions(_groupConfig.options.visible_to_all)',
  ],

  attached() {
    this._loadGroup();
  },

  _loadGroup() {
    if (!this.groupId) { return; }

    const promises = [];

    const errFn = response => {
      this.fire('page-error', {response});
    };

    return this.$.restAPI.getGroupConfig(this.groupId, errFn)
        .then(config => {
          if (!config || !config.name) { return Promise.resolve(); }

          this._groupName = config.name;
          this._groupIsInternal = !!config.id.match(INTERNAL_GROUP_REGEX);

          promises.push(this.$.restAPI.getIsAdmin().then(isAdmin => {
            this._isAdmin = isAdmin ? true : false;
          }));

          promises.push(this.$.restAPI.getIsGroupOwner(config.name)
              .then(isOwner => {
                this._groupOwner = isOwner ? true : false;
              }));

          // If visible to all is undefined, set to false. If it is defined
          // as false, setting to false is fine. If any optional values
          // are added with a default of true, then this would need to be an
          // undefined check and not a truthy/falsy check.
          if (!config.options.visible_to_all) {
            config.options.visible_to_all = false;
          }
          this._groupConfig = config;

          this.fire('title-change', {title: config.name});

          return Promise.all(promises).then(() => {
            this._loading = false;
          });
        });
  },

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  },

  _isLoading() {
    return this._loading || this._loading === undefined;
  },

  _handleSaveName() {
    return this.$.restAPI.saveGroupName(this.groupId, this._groupConfig.name)
        .then(config => {
          if (config.status === 200) {
            this._groupName = this._groupConfig.name;
            this.fire('name-changed', {name: this._groupConfig.name,
              external: this._groupIsExtenral});
            this._rename = false;
          }
        });
  },

  _handleSaveOwner() {
    return this.$.restAPI.saveGroupOwner(this.groupId,
        this._groupConfig.owner).then(config => {
          this._owner = false;
        });
  },

  _handleSaveDescription() {
    return this.$.restAPI.saveGroupDescription(this.groupId,
        this._groupConfig.description).then(config => {
          this._description = false;
        });
  },

  _handleSaveOptions() {
    let options;
    // The value is in string so we have to convert it to a boolean.
    if (this._groupConfig.options.visible_to_all) {
      options = {visible_to_all: true};
    } else if (!this._groupConfig.options.visible_to_all) {
      options = {visible_to_all: false};
    }
    return this.$.restAPI.saveGroupOptions(this.groupId,
        options).then(config => {
          this._options = false;
        });
  },

  _handleConfigName() {
    if (this._isLoading()) { return; }
    this._rename = true;
  },

  _handleConfigOwner() {
    if (this._isLoading()) { return; }
    this._owner = true;
  },

  _handleConfigDescription() {
    if (this._isLoading()) { return; }
    this._description = true;
  },

  _handleConfigOptions() {
    if (this._isLoading()) { return; }
    this._options = true;
  },

  _computeHeaderClass(configChanged) {
    return configChanged ? 'edited' : '';
  },

  _getGroupSuggestions(input) {
    return this.$.restAPI.getSuggestedGroups(input)
        .then(response => {
          const groups = [];
          for (const key in response) {
            if (!response.hasOwnProperty(key)) { continue; }
            groups.push({
              name: key,
              value: response[key],
            });
          }
          return groups;
        });
  },

  _computeGroupDisabled(owner, admin, groupIsInternal) {
    return groupIsInternal && (admin || owner) ? false : true;
  }
});
