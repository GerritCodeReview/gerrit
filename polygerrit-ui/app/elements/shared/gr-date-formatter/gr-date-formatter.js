/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  const Duration = {
    HOUR: 1000 * 60 * 60,
    DAY: 1000 * 60 * 60 * 24,
  };

  const TimeFormats = {
    TIME_12: 'h:mm A', // 2:14 PM
    TIME_12_WITH_SEC: 'h:mm:ss A', // 2:14:00 PM
    TIME_24: 'HH:mm', // 14:14
    TIME_24_WITH_SEC: 'HH:mm:ss', // 14:14:00
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
      showDateAndTime: {
        type: Boolean,
        value: false,
      },

      /**
       * When true, the detailed date appears in a GR-TOOLTIP rather than in the
       * native browser tooltip.
       */
      hasTooltip: Boolean,

      /**
       * The title to be used as the native tooltip or by the tooltip behavior.
       */
      title: {
        type: String,
        reflectToAttribute: true,
        computed: '_computeFullDateStr(dateStr, _timeFormat)',
      },

      _timeFormat: String, // No default value to prevent flickering.
      _relative: Boolean, // No default value to prevent flickering.
    },

    behaviors: [
      Gerrit.TooltipBehavior,
    ],

    attached() {
      this._loadPreferences();
    },

    _getUtcOffsetString() {
      return ' UTC' + moment().format('Z');
    },

    _loadPreferences() {
      return this._getLoggedIn().then(loggedIn => {
        if (!loggedIn) {
          this._timeFormat = TimeFormats.TIME_24;
          this._relative = false;
          return;
        }
        return Promise.all([
          this._loadTimeFormat(),
          this._loadRelative(),
        ]);
      });
    },

    _loadTimeFormat() {
      return this._getPreferences().then(preferences => {
        const timeFormat = preferences && preferences.time_format;
        switch (timeFormat) {
          case 'HHMM_12':
            this._timeFormat = TimeFormats.TIME_12;
            break;
          case 'HHMM_24':
            this._timeFormat = TimeFormats.TIME_24;
            break;
          default:
            throw Error('Invalid time format: ' + timeFormat);
        }
      });
    },

    _loadRelative() {
      return this._getPreferences().then(prefs => {
        // prefs.relative_date_in_change_table is not set when false.
        this._relative = !!(prefs && prefs.relative_date_in_change_table);
      });
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _getPreferences() {
      return this.$.restAPI.getPreferences();
    },

    /**
     * Return true if date is within 24 hours and on the same day.
     */
    _isWithinDay(now, date) {
      const diff = -date.diff(now);
      return diff < Duration.DAY && date.day() === now.getDay();
    },

    /**
     * Returns true if date is from one to six months.
     */
    _isWithinHalfYear(now, date) {
      const diff = -date.diff(now);
      return (date.day() !== now.getDay() || diff >= Duration.DAY) &&
          diff < 180 * Duration.DAY;
    },

    _computeDateStr(dateStr, timeFormat, relative, showDateAndTime) {
      if (!dateStr) { return ''; }
      const date = moment(util.parseDate(dateStr));
      if (!date.isValid()) { return ''; }
      if (relative) {
        const dateFromNow = date.fromNow();
        if (dateFromNow === 'a few seconds ago') {
          return 'just now';
        } else {
          return dateFromNow;
        }
      }
      const now = new Date();
      let format = TimeFormats.MONTH_DAY_YEAR;
      if (this._isWithinDay(now, date)) {
        format = timeFormat;
      } else {
        if (this._isWithinHalfYear(now, date)) {
          format = TimeFormats.MONTH_DAY;
        }
        if (this.showDateAndTime) {
          format = `${format} ${timeFormat}`;
        }
      }
      return date.format(format);
    },

    _timeToSecondsFormat(timeFormat) {
      return timeFormat === TimeFormats.TIME_12 ?
          TimeFormats.TIME_12_WITH_SEC :
          TimeFormats.TIME_24_WITH_SEC;
    },

    _computeFullDateStr(dateStr, timeFormat) {
      // Polymer 2: check for undefined
      if ([
        dateStr,
        timeFormat,
      ].some(arg => arg === undefined)) {
        return undefined;
      }

      if (!dateStr) { return ''; }
      const date = moment(util.parseDate(dateStr));
      if (!date.isValid()) { return ''; }
      let format = TimeFormats.MONTH_DAY_YEAR + ', ';
      format += this._timeToSecondsFormat(timeFormat);
      return date.format(format) + this._getUtcOffsetString();
    },
  });
})();
