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
    is: 'gr-diff-highlight',

    properties: {
      comments: Object,
      enabled: {
        type: Boolean,
        observer: '_enabledChanged',
      },
      loggedIn: Boolean,
      _diffBuilder: Object,
      _diffElement: Object,
      _enabledListeners: {
        type: Object,
        value: function() {
          return {
            'down': '_handleDown',
          };
        },
      },
    },

    get diffBuilder() {
      if (!this._diffBuilder) {
        this._diffBuilder = Polymer.dom(this).querySelector('gr-diff-builder');
      }
      return this._diffBuilder;
    },

    get diffElement() {
      if (!this._diffElement) {
        this._diffElement = Polymer.dom(this).querySelector('#diffTable');
      }
      return this._diffElement;
    },

    detached: function() {
      this.enabled = false;
    },

    _enabledChanged: function() {
      for (var eventName in this._enabledListeners) {
        var methodName = this._enabledListeners[eventName];
        if (this.enabled) {
          this.listen(this, eventName, methodName);
        } else {
          this.unlisten(this, eventName, methodName);
        };
      };
    },

    isRangeSelected: function() {
      return !!this.$$('gr-selection-action-box');
    },

    _handleDown: function(e) {
      var actionBox = this.$$('gr-selection-action-box');
      if (actionBox && !actionBox.contains(e.target)) {
        this._removeActionBox();
      }
    },
  });
})();
