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

  var TimeFormats = {
    TIME_12: 'h:mm A', // 2:14 PM
    TIME_24: 'H:mm', // 14:14
    MONTH_DAY: 'MMM DD', // Aug 29
    MONTH_DAY_YEAR: 'MMM DD, YYYY', // Aug 29, 1997
  };

  Polymer({
    is: 'gr-date-formatter',

    properties: {
      dateStr: {
        type: String,
        value: null,
        notify: true,
      },

      _timeFormat: String,
    },

    attached: function() {
      this._getTimeFormat().then(function(timeFormat) {
        this._timeFormat = timeFormat;
      }.bind(this));
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _getPreferences: function() {
      return this.$.restAPI.getPreferences();
    },

    _getTimeFormat: function() {
      return this._getLoggedIn().then(function(loggedIn) {
        if (!loggedIn) { return 'HHMM_24'; }

        return this._getPreferences().then(function(preferences) {
          return preferences && preferences.time_format;
        });
      }.bind(this));
    },

    /**
     * Return true if date is within 24 hours and on the same day.
     */
    _isWithinDay: function(now, date) {
      var diff = -date.diff(now);
      return diff < Duration.DAY && date.day() === now.getDay();
    },

    /**
     * Returns true if date is from one to six months.
     */
    _isWithinHalfYear: function(now, date) {
      var diff = -date.diff(now);
      return (date.day() !== now.getDay() || diff >= Duration.DAY) &&
          diff < 180 * Duration.DAY;
    },

    _computeDateStr: function(dateStr, timeFormat) {
      if (!dateStr) { return ''; }
      var date = moment(util.parseDate(dateStr));
      if (!date.isValid()) { return ''; }
      var now = new Date();
      var format = TimeFormats.MONTH_DAY_YEAR;
      if (this._isWithinDay(now, date)) {
        if (timeFormat === 'HHMM_12') {
          format = TimeFormats.TIME_12;
        } else if (timeFormat === 'HHMM_24') {
          format = TimeFormats.TIME_24;
        } else {
          throw Error('Invalid time format: ' + timeFormat);
        }
      } else if (this._isWithinHalfYear(now, date)) {
        format = TimeFormats.MONTH_DAY;
      }
      return date.format(format);
    },
  });
})();
