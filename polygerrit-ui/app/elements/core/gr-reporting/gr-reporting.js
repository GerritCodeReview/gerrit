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

  var APP_STARTED = 'App Started';
  var PAGE_LOADED = 'Page Loaded';
  var TIMING_EVENT = 'timing-report';
  var DEFAULT_CATEGORY = 'UI Latency';
  var DEFAULT_TYPE = 'timing';

  Polymer({
    is: 'gr-reporting',

    properties: {
      _baselines: {
        type: Array,
        value: function() { return {}; },
      }
    },

    get performanceTiming() {
      return window.performance.timing;
    },

    get now() {
      return Math.round(10 * window.performance.now()) / 10;
    },

    reporter: function(type, category, eventName, eventValue) {
      eventValue = eventValue;
      var detail = {
        type: type,
        category: category,
        name: eventName,
        value: eventValue,
      };
      document.dispatchEvent(
          new CustomEvent(TIMING_EVENT, {detail: detail}));
      console.log(eventName + ': ' + eventValue);
    },

    /**
     * User-perceived app start time, should be reported when the app is ready.
     */
    appStarted: function() {
      var startTime =
          new Date().getTime() - this.performanceTiming.navigationStart;
      this.reporter(
          DEFAULT_TYPE, DEFAULT_CATEGORY, APP_STARTED, startTime);
    },

    /**
     * Page load time, should be reported at any time after navigation.
     */
    pageLoaded: function() {
      var loadTime = this.performanceTiming.loadEventEnd -
          this.performanceTiming.navigationStart;
      this.reporter(DEFAULT_TYPE, DEFAULT_CATEGORY, PAGE_LOADED, loadTime);
    },

    /**
     * Reset named timer.
     */
    time: function(name) {
      this._baselines[name] = this.now();
    },

    /**
     * Finish named timer and report it to server.
     */
    timeEnd: function(name) {
      var baseTime = this._baselines[name] || 0;
      var time = this.now() - baseTime;
      this.reporter(DEFAULT_TYPE, DEFAULT_CATEGORY, name, time);
      delete this._baselines[name];
    },
  });
})();
