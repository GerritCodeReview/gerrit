/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-tooltip-content/gr-tooltip-content';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  formatDate,
  fromNow,
  isValidDate,
  isWithinDay,
  isWithinHalfYear,
  parseDate,
  utcOffsetString,
  wasYesterday,
} from '../../../utils/date-util';
import {DateFormat, TimeFormat} from '../../../constants/constants';
import {assertNever} from '../../../utils/common-util';
import {PreferencesInfo, Timestamp} from '../../../types/common';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';
import {subscribe} from '../../lit/subscription-controller';

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

  @property({type: Boolean})
  forceRelative = false;

  @property({type: Boolean})
  relativeOptionNoAgo = false;

  @state()
  dateFormat?: DateFormatPair;

  @state()
  timeFormat?: string;

  @state()
  relative = false;

  private readonly getUserModel = resolve(this, userModelToken);

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

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => this.setPreferences(prefs)
    );
  }

  // private but used by tests
  setPreferences(prefs: PreferencesInfo) {
    this.decideDateFormat(prefs.date_format);
    this.decideTimeFormat(prefs.time_format);
    this.relative =
      this.forceRelative || Boolean(prefs?.relative_date_in_change_table);
  }

  override render() {
    if (!this.withTooltip) return this.renderDateString();
    const tooltip = this.computeFullDateStr();
    if (!tooltip) return this.renderDateString();

    return html`
      <gr-tooltip-content has-tooltip title=${tooltip}>
        ${this.renderDateString()}
      </gr-tooltip-content>
    `;
  }

  private renderDateString() {
    return html` <span>${this.computeDateStr()}</span>`;
  }

  // private but used by tests
  getUtcOffsetString() {
    return utcOffsetString();
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
      if (this.showDateAndTime) {
        format = `${format} ${this.timeFormat}`;
      }
    }
    return formatDate(date, format);
  }

  private computeFullDateStr(): string {
    if (!this.dateStr) return '';
    if (!this.timeFormat) return '';
    if (!this.dateFormat) return '';
    const date = parseDate(this.dateStr as Timestamp);
    if (!isValidDate(date)) return '';
    const timeFormat =
      this.timeFormat === TimeFormats.TIME_12
        ? TimeFormats.TIME_12_WITH_SEC
        : TimeFormats.TIME_24_WITH_SEC;
    const format = `dddd, ${this.dateFormat.full}, ${timeFormat}`;
    return formatDate(date, format) + this.getUtcOffsetString();
  }
}
