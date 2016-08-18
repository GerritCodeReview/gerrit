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
    is: 'gr-reporting',

    properties: {
      reporter: {
        type: Function,
      },
      _baselines: {
        type: Array,
        value: function() { return {}; },
      }
    },

    get performanceTiming() {
      return window.performance.timing;
    },

    get reporter() {
      return this._eventReporter.bind(this);
    },

    _eventReporter: function(type, category, eventName, eventValue) {
      eventValue = Math.round(eventValue);
      var detail = {
        type: type,
        category: category,
        name: eventName,
        value: eventValue,
      };
      document.dispatchEvent(
          new CustomEvent('timing-report', {detail: detail}));
      console.log(eventName + ': ' + eventValue);
    },

    /**
     * User-perceived app start time, should be reported when the app is ready.
     */
    appStarted: function() {
      var startTime =
          new Date().getTime() - this.performanceTiming.navigationStart;
      this.reporter(
          'timing', 'UI Latency', 'appStarted', startTime);
    },

    /**
     * Page load time, should be reported at any time after navigation.
     */
    pageLoaded: function() {
      var loadTime = this.performanceTiming.loadEventEnd -
          this.performanceTiming.navigationStart;
      this.reporter('timing', 'UI Latency', 'pageLoad', loadTime);
    },

    /**
     * Reset named timer.
     */
    time: function(name) {
      this._baselines[name] = window.performance.now();
    },

    /**
     * Finish named timer and report it to server.
     */
    timeEnd: function(name) {
      var baseTime = this._baselines[name] || 0;
      var time = window.performance.now() - baseTime;
      this.reporter('timing', 'UI Latency', name, time);
      delete this._baselines[name];
    },
  });
})();
