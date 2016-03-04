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
    is: 'gr-ajax',

    /**
     * Fired when a response is received.
     *
     * @event response
     */

    /**
     * Fired when an error is received.
     *
     * @event error
     */

    hostAttributes: {
      hidden: true
    },

    properties: {
      auto: {
        type: Boolean,
        value: false,
      },
      url: String,
      params: {
        type: Object,
        value: function() {
          return {};
        },
      },
      lastError: {
        type: Object,
        notify: true,
      },
      lastResponse: {
        type: Object,
        notify: true,
      },
      loading: {
        type: Boolean,
        notify: true,
      },
    },

    ready: function() {
      // Used for debugging which element a request came from.
      var headers = this.$.xhr.headers;
      headers['x-requesting-element-id'] = this.id || 'gr-ajax (no id)';
      this.$.xhr.headers = headers;
    },

    generateRequest: function() {
      return this.$.xhr.generateRequest();
    },

    _handleResponse: function(e, req) {
      this.fire('response', req, {bubbles: false});
    },

    _handleError: function(e, req) {
      this.fire('error', req, {bubbles: false});
    },
  });
})();
