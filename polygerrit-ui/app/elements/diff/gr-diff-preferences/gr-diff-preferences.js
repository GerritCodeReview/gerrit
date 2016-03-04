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
(function() {
  'use strict';

  Polymer({
    is: 'gr-diff-preferences',

    /**
     * Fired when the user presses the save button.
     *
     * @event save
     */

    /**
     * Fired when the user presses the cancel button.
     *
     * @event cancel
     */

    properties: {
      prefs: {
        type: Object,
        notify: true,
        value: function() { return {}; },
      },
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
    },

    observers: [
      '_prefsChanged(prefs.*)',
    ],

    _prefsChanged: function(changeRecord) {
      var prefs = changeRecord.base;
      this.$.contextSelect.value = prefs.context;
      this.$.showTabsInput.checked = prefs.show_tabs;
    },

    _handleContextSelectChange: function(e) {
      var selectEl = Polymer.dom(e).rootTarget;
      this.set('prefs.context', parseInt(selectEl.value, 10));
    },

    _handleShowTabsTap: function(e) {
      this.set('prefs.show_tabs', Polymer.dom(e).rootTarget.checked);
    },

    _handleSave: function() {
      this.fire('save', null, {bubbles: false});
    },

    _handleCancel: function() {
      this.fire('cancel', null, {bubbles: false});
    },
  });
})();
