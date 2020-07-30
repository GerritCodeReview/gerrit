/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

const Duration = {
  HOUR: 1000 * 60 * 60,
  DAY: 1000 * 60 * 60 * 24,
};

export function parseDate(dateStr: string) {
  // Timestamps are given in UTC and have the format
  // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
  // nanoseconds.
  // Munge the date into an ISO 8061 format and parse that.
  return new Date(dateStr.replace(' ', 'T') + 'Z');
}

export function isValidDate(date: Date) {
  return date instanceof Date;
}

// similar to fromNow from moment.js
export function fromNow(date: Date) {
  const now = new Date();
  const secondsAgo = Math.round((now.valueOf() - date.valueOf()) / 1000);
  if (secondsAgo <= 44) return 'just now';
  if (secondsAgo <= 89) return 'a minute ago';
  const minutesAgo = Math.round(secondsAgo / 60);
  if (minutesAgo <= 44) return `${minutesAgo} minutes ago`;
  if (minutesAgo <= 89) return 'an hour ago';
  const hoursAgo = Math.round(minutesAgo / 60);
  if (hoursAgo <= 21) return `${hoursAgo} hours ago`;
  if (hoursAgo <= 35) return 'a day ago';
  const daysAgo = Math.round(hoursAgo / 24);
  if (daysAgo <= 25) return `${daysAgo} days ago`;
  if (daysAgo <= 45) return `a month ago`;
  const monthsAgo = Math.round(daysAgo / 30);
  if (daysAgo <= 319) return `${monthsAgo} months ago`;
  if (daysAgo <= 547) return `a year ago`;
  const yearsAgo = Math.round(daysAgo / 365);
  return `${yearsAgo} years ago`;
}

/**
 * Return true if date is within 24 hours and on the same day.
 */
export function isWithinDay(now: Date, date: Date) {
  const diff = now.valueOf() - date.valueOf();
  return diff < Duration.DAY && date.getDay() === now.getDay();
}

/**
 * Returns true if date is from one to six months.
 */
export function isWithinHalfYear(now: Date, date: Date) {
  const diff = now.valueOf() - date.valueOf();
  return diff < 180 * Duration.DAY;
}
interface Options {
  month?: string;
  year?: string;
  day?: string;
  hour?: string;
  hour12?: boolean;
  minute?: string;
  second?: string;
}

export function formatDate(date: Date, format: string) {
  const options: Options = {};
  if (format.includes('MM')) {
    if (format.includes('MMM')) {
      options.month = 'short';
    } else {
      options.month = '2-digit';
    }
  }
  if (format.includes('YY')) {
    if (format.includes('YYYY')) {
      options.year = 'numeric';
    } else {
      options.year = '2-digit';
    }
  }

  if (format.includes('DD')) {
    options.day = '2-digit';
  }

  if (format.includes('HH')) {
    options.hour = '2-digit';
    options.hour12 = false;
  }

  if (format.includes('h')) {
    options.hour = 'numeric';
    options.hour12 = true;
  }

  if (format.includes('mm')) {
    options.minute = '2-digit';
  }

  if (format.includes('ss')) {
    options.second = '2-digit';
  }
  let locale = 'en-US';
  // Workaround for Chrome 80, en-US is using h24 (midnight is 24:00),
  // en-GB is using h23 (midnight is 00:00)
  if (format.includes('HH')) {
    locale = 'en-GB';
  }

  const dtf = new Intl.DateTimeFormat(locale, options);
  const parts = dtf.formatToParts(date).filter(o => o.type != 'literal')
      .reduce((acc, o) => {
        acc[o.type] = o.value;
        return acc;
      }, {});
  if (format.includes('YY')) {
    if (format.includes('YYYY')) {
      format = format.replace('YYYY', parts.year);
    } else {
      format = format.replace('YY', parts.year);
    }
  }

  if (format.includes('DD')) {
    format = format.replace('DD', parts.day);
  }

  if (format.includes('HH')) {
    format = format.replace('HH', parts.hour);
  }

  if (format.includes('h')) {
    format = format.replace('h', parts.hour);
  }

  if (format.includes('mm')) {
    format = format.replace('mm', parts.minute);
  }

  if (format.includes('ss')) {
    format = format.replace('ss', parts.second);
  }

  if (format.includes('A')) {
    if (parts.dayperiod) {
      // Workaround for chrome 70 and below
      format = format.replace('A', parts.dayperiod.toUpperCase());
    } else {
      format = format.replace('A', parts.dayPeriod.toUpperCase());
    }
  }
  if (format.includes('MM')) {
    if (format.includes('MMM')) {
      format = format.replace('MMM', parts.month);
    } else {
      format = format.replace('MM', parts.month);
    }
  }
  return format;
}

export function utcOffsetString() {
  const now = new Date();
  const tzo = -now.getTimezoneOffset();
  const pad = num => {
    const norm = Math.floor(Math.abs(num));
    return (norm < 10 ? '0' : '') + norm;
  };
  return ` UTC${tzo >= 0 ? '+' : '-'}${pad(tzo / 60)}:${pad(tzo%60)}`;
}