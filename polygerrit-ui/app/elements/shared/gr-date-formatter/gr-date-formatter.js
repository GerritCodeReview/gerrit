/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '../../../scripts/bundled-polymer.js';

import '../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-date-formatter_html.js';
import {util} from '../../../scripts/util.js';

const Duration = {
  HOUR: 1000 * 60 * 60,
  DAY: 1000 * 60 * 60 * 24,
};

const TimeFormats = {
  TIME_12: 'h:mm A', // 2:14 PM
  TIME_12_WITH_SEC: 'h:mm:ss A', // 2:14:00 PM
  TIME_24: 'HH:mm', // 14:14
  TIME_24_WITH_SEC: 'HH:mm:ss', // 14:14:00
};

const DateFormats = {
  STD: {
    short: 'MMM DD', // Aug 29
    full: 'MMM DD, YYYY', // Aug 29, 1997
  },
  US: {
    short: 'MM/DD', // 08/29
    full: 'MM/DD/YY', // 08/29/97
  },
  ISO: {
    short: 'MM-DD', // 08-29
    full: 'YYYY-MM-DD', // 1997-08-29
  },
  EURO: {
    short: 'DD. MMM', // 29. Aug
    full: 'DD.MM.YYYY', // 29.08.1997
  },
  UK: {
    short: 'DD/MM', // 29/08
    full: 'DD/MM/YYYY', // 29/08/1997
  },
};

/**
 * @appliesMixin Gerrit.TooltipMixin
 * @extends Polymer.Element
 */
class GrDateFormatter extends mixinBehaviors( [
  Gerrit.TooltipBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-date-formatter'; }

  static get properties() {
    return {
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
        computed: '_computeFullDateStr(dateStr, _timeFormat, _dateFormat)',
      },

      /** @type {?{short: string, full: string}} */
      _dateFormat: Object,
      _timeFormat: String, // No default value to prevent flickering.
      _relative: Boolean, // No default value to prevent flickering.
    };
  }

  /** @override */
  attached() {
    super.attached();
    this._loadPreferences();
  }

  _getUtcOffsetString() {
    return ' UTC' + moment().format('Z');
  }

  _loadPreferences() {
    return this._getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        this._timeFormat = TimeFormats.TIME_24;
        this._dateFormat = DateFormats.STD;
        this._relative = false;
        return;
      }
      return Promise.all([
        this._loadTimeFormat(),
        this._loadRelative(),
      ]);
    });
  }

  _loadTimeFormat() {
    return this._getPreferences().then(preferences => {
      const timeFormat = preferences && preferences.time_format;
      const dateFormat = preferences && preferences.date_format;
      this._decideTimeFormat(timeFormat);
      this._decideDateFormat(dateFormat);
    });
  }

  _decideTimeFormat(timeFormat) {
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
  }

  _decideDateFormat(dateFormat) {
    switch (dateFormat) {
      case 'STD':
        this._dateFormat = DateFormats.STD;
        break;
      case 'US':
        this._dateFormat = DateFormats.US;
        break;
      case 'ISO':
        this._dateFormat = DateFormats.ISO;
        break;
      case 'EURO':
        this._dateFormat = DateFormats.EURO;
        break;
      case 'UK':
        this._dateFormat = DateFormats.UK;
        break;
      default:
        throw Error('Invalid date format: ' + dateFormat);
    }
  }

  _loadRelative() {
    return this._getPreferences().then(prefs => {
      // prefs.relative_date_in_change_table is not set when false.
      this._relative = !!(prefs && prefs.relative_date_in_change_table);
    });
  }

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  }

  _getPreferences() {
    return this.$.restAPI.getPreferences();
  }

  /**
   * Return true if date is within 24 hours and on the same day.
   */
  _isWithinDay(now, date) {
    const diff = -date.diff(now);
    return diff < Duration.DAY && date.day() === now.getDay();
  }

  /**
   * Returns true if date is from one to six months.
   */
  _isWithinHalfYear(now, date) {
    const diff = -date.diff(now);
    return (date.day() !== now.getDay() || diff >= Duration.DAY) &&
      diff < 180 * Duration.DAY;
  }

  _computeDateStr(
      dateStr, timeFormat, dateFormat, relative, showDateAndTime
  ) {
    if (!dateStr || !timeFormat || !dateFormat) { return ''; }
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
    let format = dateFormat.full;
    if (this._isWithinDay(now, date)) {
      format = timeFormat;
    } else {
      if (this._isWithinHalfYear(now, date)) {
        format = dateFormat.short;
      }
      if (this.showDateAndTime) {
        format = `${format} ${timeFormat}`;
      }
    }
    return date.format(format);
  }

  _timeToSecondsFormat(timeFormat) {
    return timeFormat === TimeFormats.TIME_12 ?
      TimeFormats.TIME_12_WITH_SEC :
      TimeFormats.TIME_24_WITH_SEC;
  }

  _computeFullDateStr(dateStr, timeFormat, dateFormat) {
    // Polymer 2: check for undefined
    if ([
      dateStr,
      timeFormat,
      dateFormat,
    ].some(arg => arg === undefined)) {
      return undefined;
    }

    if (!dateStr) { return ''; }
    const date = moment(util.parseDate(dateStr));
    if (!date.isValid()) { return ''; }
    let format = dateFormat.full + ', ';
    format += this._timeToSecondsFormat(timeFormat);
    return date.format(format) + this._getUtcOffsetString();
  }
}

customElements.define(GrDateFormatter.is, GrDateFormatter);
