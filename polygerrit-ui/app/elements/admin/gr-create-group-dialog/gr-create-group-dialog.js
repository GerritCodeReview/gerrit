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

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../../@polymer/iron-input/iron-input.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-form-styles">
      :host {
        display: inline-block;
      }
      input {
        width: 20em;
      }
    </style>
    <div class="gr-form-styles">
      <div id="form">
        <section>
          <span class="title">Group name</span>
          <input is="iron-input" id="groupNameInput" bind-value="{{_name}}">
        </section>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-create-group-dialog',

  properties: {
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
  },

  observers: [
    '_updateGroupName(_name)',
  ],

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  _computeGroupUrl(groupId) {
    return this.getBaseUrl() + '/admin/groups/' +
        this.encodeURL(groupId, true);
  },

  _updateGroupName(name) {
    this.hasNewGroupName = !!name;
  },

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
});
