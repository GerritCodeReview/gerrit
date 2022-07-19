/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-tooltip-content/gr-tooltip-content';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  parseDate,
  fromNow,
  isValidDate,
  isWithinDay,
  isWithinHalfYear,
  formatDate,
  utcOffsetString,
  wasYesterday,
} from '../../../utils/date-util';
import {TimeFormat, DateFormat} from '../../../constants/constants';
import {assertNever} from '../../../utils/common-util';
import {Timestamp} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';

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

@customElement('gr-date-formatter')
export class GrDateFormatter extends LitElement {
  @property({type: String})
  dateStr: string | undefined = undefined;

  @property({type: Boolean})
  showDateAndTime = false;

  /**
   * When true, the detailed date appears in a GR-TOOLTIP rather than in the
   * native browser tooltip.
   */
  @property({type: Boolean})
  withTooltip = false;

  @property({type: Boolean})
  showYesterday = false;

  @property({type: Object})
  dateFormat?: DateFormatPair;

  @property({type: String})
  timeFormat?: string;

  @property({type: Boolean})
  relative = false;

  @property({type: Boolean})
  forceRelative = false;

  @property({type: Boolean})
  relativeOptionNoAgo = false;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      css`
        host {
          color: inherit;
          display: inline;
        }
      `,
    ];
  }

  override render() {
    if (!this.withTooltip) {
      return this.renderDateString();
    }

    const fullDateStr = this.computeFullDateStr();
    if (!fullDateStr) {
      return this.renderDateString();
    }
    return html`
      <gr-tooltip-content has-tooltip title=${fullDateStr}>
        ${this.renderDateString()}
      </gr-tooltip-content>
    `;
  }

  private renderDateString() {
    return html` <span>${this.computeDateStr()}</span>`;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadPreferences();
  }

  // private but used by tests
  _getUtcOffsetString() {
    return utcOffsetString();
  }

  // private but used by tests
  async loadPreferences() {
    const loggedIn = await this.restApiService.getLoggedIn();
    if (!loggedIn) {
      this.timeFormat = TimeFormats.TIME_24;
      this.dateFormat = DateFormats.STD;
      this.relative = this.forceRelative;
      return;
    }
    await Promise.all([this.loadTimeFormat(), this.loadRelative()]);
  }

  // private but used in gr/file-list_test.ts
  async loadTimeFormat() {
    const preferences = await this.restApiService.getPreferences();
    if (!preferences) {
      throw Error('Preferences is not set');
    }
    this.decideTimeFormat(preferences.time_format);
    this.decideDateFormat(preferences.date_format);
  }

  private decideTimeFormat(timeFormat: TimeFormat) {
    switch (timeFormat) {
      case TimeFormat.HHMM_12:
        this.timeFormat = TimeFormats.TIME_12;
        break;
      case TimeFormat.HHMM_24:
        this.timeFormat = TimeFormats.TIME_24;
        break;
      default:
        assertNever(timeFormat, `Invalid time format: ${timeFormat}`);
    }
  }

  private decideDateFormat(dateFormat: DateFormat) {
    switch (dateFormat) {
      case DateFormat.STD:
        this.dateFormat = DateFormats.STD;
        break;
      case DateFormat.US:
        this.dateFormat = DateFormats.US;
        break;
      case DateFormat.ISO:
        this.dateFormat = DateFormats.ISO;
        break;
      case DateFormat.EURO:
        this.dateFormat = DateFormats.EURO;
        break;
      case DateFormat.UK:
        this.dateFormat = DateFormats.UK;
        break;
      default:
        assertNever(dateFormat, `Invalid date format: ${dateFormat}`);
    }
  }

  private async loadRelative() {
    const prefs = await this.restApiService.getPreferences();
    this.relative =
      this.forceRelative || Boolean(prefs?.relative_date_in_change_table);
  }

  private computeDateStr() {
    if (!this.dateStr || !this.timeFormat || !this.dateFormat) {
      return '';
    }
    const date = parseDate(this.dateStr as Timestamp);
    if (!isValidDate(date)) {
      return '';
    }
    if (this.relative) {
      return fromNow(date, this.relativeOptionNoAgo);
    }
    const now = new Date();
    let format = this.dateFormat.full;
    if (isWithinDay(now, date)) {
      format = this.timeFormat;
    } else if (this.showYesterday && wasYesterday(now, date)) {
      return `Yesterday at ${formatDate(date, this.timeFormat)}`;
    } else {
      if (isWithinHalfYear(now, date)) {
        format = this.dateFormat.short;
      }
      if (this.showDateAndTime || this.showDateAndTime) {
        format = `${format} ${this.timeFormat}`;
      }
    }
    return formatDate(date, format);
  }

  private computeFullDateStr() {
    if (
      [this.dateStr, this.timeFormat].includes(undefined) ||
      !this.dateFormat
    ) {
      return undefined;
    }

    if (!this.dateStr) {
      return '';
    }
    const date = parseDate(this.dateStr as Timestamp);
    if (!isValidDate(date)) {
      return '';
    }
    let format = this.dateFormat.full + ', ';
    format +=
      this.timeFormat === TimeFormats.TIME_12
        ? TimeFormats.TIME_12_WITH_SEC
        : TimeFormats.TIME_24_WITH_SEC;
    return formatDate(date, format) + this._getUtcOffsetString();
  }
}
