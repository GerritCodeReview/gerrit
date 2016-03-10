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
      this._fetchPreferences();
    },

    _fetchPreferences: function() {
      this.$.restAPI.getPreferences().then(function(preferences) {
        this.timeFormat = preferences && preferences.time_format;
      }.bind(this));
    },

    _computeDateStr: function(dateStr, timeFormat) {
      if (!dateStr) { return ''; }
      var date = moment(dateStr + 'Z');
      if (!date.isValid()) { return ''; }
      var now = new Date();
      var diff = -date.diff(now);
      var format = 'MMM DD, YYYY';
      if (diff < Duration.DAY && date.day() == now.getDay()) {
        // Within 24 hours and on the same day:
        if (this.timeFormat == 'HHMM_12') { // '2:14 PM'
          format = 'h:mm A';
        } else { // '14:14'
          format = 'H:mm';
        }
      } else if ((date.day() != now.getDay() || diff >= Duration.DAY) &&
                 diff < 180 * Duration.DAY) {
        // From one to six months:
        // 'Aug 29'
        format = 'MMM DD';
      }
      return date.format(format);
    },
  });
})();
