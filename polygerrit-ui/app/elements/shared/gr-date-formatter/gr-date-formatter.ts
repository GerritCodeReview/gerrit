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
import '../gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-date-formatter_html';
import {TooltipMixin} from '../../../mixins/gr-tooltip-mixin/gr-tooltip-mixin';
import {property, customElement} from '@polymer/decorators';
import {
  parseDate,
  fromNow,
  isValidDate,
  isWithinDay,
  isWithinHalfYear,
  formatDate,
  utcOffsetString,
} from '../../../utils/date-util';
import {TimeFormat, DateFormat} from '../../../constants/constants';
import {assertNever} from '../../../utils/common-util';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {Timestamp} from '../../../types/common';

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

interface DateFormatPair {
  short: string;
  full: string;
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-date-formatter': GrDateFormatter;
  }
}

export interface GrDateFormatter {
  $: {
    restAPI: RestApiService & Element;
  };
}

@customElement('gr-date-formatter')
export class GrDateFormatter extends TooltipMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, notify: true})
  dateStr: string | null = null;

  @property({type: Boolean})
  showDateAndTime = false;

  /**
   * When true, the detailed date appears in a GR-TOOLTIP rather than in the
   * native browser tooltip.
   */
  @property({type: Boolean})
  hasTooltip = false;

  /**
   * The title to be used as the native tooltip or by the tooltip behavior.
   */
  @property({
    type: String,
    reflectToAttribute: true,
    computed: '_computeFullDateStr(dateStr, _timeFormat, _dateFormat)',
  })
  title = '';

  /** @type {?{short: string, full: string}} */
  @property({type: Object})
  _dateFormat?: DateFormatPair;

  @property({type: String})
  _timeFormat?: string;

  @property({type: Boolean})
  _relative = false;

  @property({type: Boolean})
  forceRelative = false;

  @property({type: Boolean})
  relativeOptionNoAgo = false;

  constructor() {
    super();
  }

  /** @override */
  attached() {
    super.attached();
    this._loadPreferences();
  }

  _getUtcOffsetString() {
    return utcOffsetString();
  }

  _loadPreferences() {
    return this._getLoggedIn().then(loggedIn => {
      if (!loggedIn) {
        this._timeFormat = TimeFormats.TIME_24;
        this._dateFormat = DateFormats.STD;
        this._relative = this.forceRelative;
        return;
      }
      return Promise.all([this._loadTimeFormat(), this._loadRelative()]);
    });
  }

  _loadTimeFormat() {
    return this._getPreferences().then(preferences => {
      if (!preferences) {
        throw Error('Preferences is not set');
      }
      this._decideTimeFormat(preferences.time_format);
      this._decideDateFormat(preferences.date_format);
    });
  }

  _decideTimeFormat(timeFormat: TimeFormat) {
    switch (timeFormat) {
      case TimeFormat.HHMM_12:
        this._timeFormat = TimeFormats.TIME_12;
        break;
      case TimeFormat.HHMM_24:
        this._timeFormat = TimeFormats.TIME_24;
        break;
      default:
        assertNever(timeFormat, `Invalid time format: ${timeFormat}`);
    }
  }

  _decideDateFormat(dateFormat: DateFormat) {
    switch (dateFormat) {
      case DateFormat.STD:
        this._dateFormat = DateFormats.STD;
        break;
      case DateFormat.US:
        this._dateFormat = DateFormats.US;
        break;
      case DateFormat.ISO:
        this._dateFormat = DateFormats.ISO;
        break;
      case DateFormat.EURO:
        this._dateFormat = DateFormats.EURO;
        break;
      case DateFormat.UK:
        this._dateFormat = DateFormats.UK;
        break;
      default:
        assertNever(dateFormat, `Invalid date format: ${dateFormat}`);
    }
  }

  _loadRelative() {
    return this._getPreferences().then(prefs => {
      // prefs.relative_date_in_change_table is not set when false.
      this._relative =
        this.forceRelative || !!(prefs && prefs.relative_date_in_change_table);
    });
  }

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  }

  _getPreferences() {
    return this.$.restAPI.getPreferences();
  }

  _computeDateStr(
    dateStr?: Timestamp,
    timeFormat?: string,
    dateFormat?: DateFormatPair,
    relative?: boolean,
    showDateAndTime?: boolean
  ) {
    if (!dateStr || !timeFormat || !dateFormat) {
      return '';
    }
    const date = parseDate(dateStr);
    if (!isValidDate(date)) {
      return '';
    }
    if (relative) {
      return fromNow(date, this.relativeOptionNoAgo);
    }
    const now = new Date();
    let format = dateFormat.full;
    if (isWithinDay(now, date)) {
      format = timeFormat;
    } else {
      if (isWithinHalfYear(now, date)) {
        format = dateFormat.short;
      }
      if (this.showDateAndTime || showDateAndTime) {
        format = `${format} ${timeFormat}`;
      }
    }
    return formatDate(date, format);
  }

  _timeToSecondsFormat(timeFormat: string | undefined) {
    return timeFormat === TimeFormats.TIME_12
      ? TimeFormats.TIME_12_WITH_SEC
      : TimeFormats.TIME_24_WITH_SEC;
  }

  _computeFullDateStr(
    dateStr?: Timestamp,
    timeFormat?: string,
    dateFormat?: DateFormatPair
  ) {
    // Polymer 2: check for undefined
    if ([dateStr, timeFormat].includes(undefined) || !dateFormat) {
      return undefined;
    }

    if (!dateStr) {
      return '';
    }
    const date = parseDate(dateStr);
    if (!isValidDate(date)) {
      return '';
    }
    let format = dateFormat.full + ', ';
    format += this._timeToSecondsFormat(timeFormat);
    return formatDate(date, format) + this._getUtcOffsetString();
  }
}
