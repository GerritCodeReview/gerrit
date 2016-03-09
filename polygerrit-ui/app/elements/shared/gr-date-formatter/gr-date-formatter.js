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

  var Duration = {
    HOUR: 1000 * 60 * 60,
    DAY: 1000 * 60 * 60 * 24,
  };

  var ShortMonthNames = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct',
    'Nov', 'Dec',
  ];

  Polymer({
    is: 'gr-date-formatter',

    properties: {
      dateStr: {
        type: String,
        value: null,
        notify: true
      },
      timeFormat: {
        type: String,
        value: 'HHMM_24',
        notify: true,
      }
    },

    attached: function() {
      var promise = this.$.restAPI.getPrefs();
      if (!promise) { return; }
      promise.then(function(preferences) {
        this.timeFormat = preferences && preferences.time_format;
      }.bind(this));
    },

    _computeDateStr: function(dateStr, timeFormat) {
      return this._dateStr(this._parseDateStr(dateStr), timeFormat);
    },

    _parseDateStr: function(dateStr) {
      if (!dateStr) { return null; }
      return util.parseDate(dateStr);
    },

    _timeToStr12: function(t) {
      var pm = t.getHours() >= 12;
      var hours = t.getHours();
      if (hours == 0) {
        hours = 12;
      } else if (hours > 12) {
        hours = t.getHours() - 12;
      }
      var minutes = t.getMinutes() < 10 ? '0' + t.getMinutes() :
          t.getMinutes();
      return hours + ':' + minutes + (pm ? ' PM' : ' AM');
    },

    _timeToStr24: function(t) {
      var minutes = t.getMinutes() < 10 ? '0' + t.getMinutes() :
          t.getMinutes();
      return t.getHours() + ':' + minutes;
    },

    _dateStr: function(t, timeFormat) {
      if (!t) { return ''; }
      var now = new Date();
      var diff = now.getTime() - t.getTime();
      if (diff < Duration.DAY && t.getDay() == now.getDay()) {
        // Within 24 hours and on the same day:
        if (this.timeFormat == 'HHMM_12') { // '2:14 PM'
          return this._timeToStr12(t);
        } else { // '14:14'
          return this._timeToStr24(t);
        }
      } else if ((t.getDay() != now.getDay() || diff >= Duration.DAY) &&
                 diff < 180 * Duration.DAY) {
        // From one to six months:
        // 'Aug 29'
        return ShortMonthNames[t.getMonth()] + ' ' + t.getDate();
      } else if (diff >= 180 * Duration.DAY) {
        // More than six months:
        // 'Aug 29, 1997'
        return ShortMonthNames[t.getMonth()] + ' ' + t.getDate() + ', ' +
            t.getFullYear();
      }
      return '';
    },
  });
})();
