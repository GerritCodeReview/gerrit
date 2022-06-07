import {Timestamp} from '../types/common';

/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const Duration = {
  HOUR: 1000 * 60 * 60,
  DAY: 1000 * 60 * 60 * 24,
};

export function parseDate(dateStr: Timestamp) {
  // Timestamps are given in UTC and have the format
  // "'yyyy-mm-dd hh:mm:ss.fffffffff'" where "'ffffffffff'" represents
  // nanoseconds.
  // Munge the date into an ISO 8061 format and parse that.
  return new Date(dateStr.replace(' ', 'T') + 'Z');
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function isValidDate(date: any): date is Date {
  return date instanceof Date && !isNaN(date.valueOf());
}

// similar to fromNow from moment.js
export function fromNow(date: Date, noAgo = false) {
  return durationString(date, new Date(), noAgo);
}

// similar to fromNow from moment.js
export function durationString(from: Date, to: Date, noAgo = false) {
  const ago = noAgo ? '' : ' ago';
  const secondsAgo = Math.floor((to.valueOf() - from.valueOf()) / 1000);
  if (secondsAgo <= 59) {
    if (noAgo) return `${secondsAgo} seconds`;
    return 'just now';
  }
  if (secondsAgo <= 119) return `1 minute${ago}`;
  const minutesAgo = Math.floor(secondsAgo / 60);
  if (minutesAgo <= 59) return `${minutesAgo} minutes${ago}`;
  if (minutesAgo === 60) return `1 hour${ago}`;
  if (minutesAgo <= 119) return `1 hour ${minutesAgo - 60} min${ago}`;
  const hoursAgo = Math.floor(minutesAgo / 60);
  if (hoursAgo <= 23) return `${hoursAgo} hours${ago}`;
  if (hoursAgo === 24) return `1 day${ago}`;
  if (hoursAgo <= 47) return `1 day ${hoursAgo - 24} hr${ago}`;
  const daysAgo = Math.floor(hoursAgo / 24);
  if (daysAgo <= 30) return `${daysAgo} days${ago}`;
  if (daysAgo <= 60) return `1 month${ago}`;
  const monthsAgo = Math.floor(daysAgo / 30);
  if (monthsAgo <= 11) return `${monthsAgo} months${ago}`;
  if (monthsAgo === 12) return `1 year${ago}`;
  if (monthsAgo <= 24) return `1 year ${monthsAgo - 12} m${ago}`;
  const yearsAgo = Math.floor(daysAgo / 365);
  return `${yearsAgo} years${ago}`;
}

/**
 * Return true if date is within 24 hours and on the same day.
 */
export function isWithinDay(now: Date, date: Date) {
  const diff = now.valueOf() - date.valueOf();
  return diff < Duration.DAY && date.getDay() === now.getDay();
}

export function wasYesterday(now: Date, date: Date) {
  const diff = now.valueOf() - date.valueOf();
  // return true if date is withing 24 hours and not on the same day
  if (diff < Duration.DAY && date.getDay() !== now.getDay()) return true;

  // move now to yesterday
  now.setDate(now.getDate() - 1);
  return isWithinDay(now, date);
}

/**
 * Returns true if date is from one to six months.
 */
export function isWithinHalfYear(now: Date, date: Date) {
  const diff = now.valueOf() - date.valueOf();
  return diff < 180 * Duration.DAY;
}

// TODO(dmfilippov): TS-Fix review this type. All fields here must be optional,
// but this require some changes in the code. During JS->TS migration
// we want to avoid code changes where possible, so for simplicity we
// define it with almost all fields mandatory
interface DateTimeFormatParts {
  year: string;
  month: string;
  day: string;
  hour: string;
  minute: string;
  second: string;
  dayPeriod: string;
  dayperiod?: string;
  // Object can have other properties, but our code doesn't use it
  [key: string]: string | undefined;
}

export function formatDate(date: Date, format: string) {
  const options: Intl.DateTimeFormatOptions = {};
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
  const parts = dtf
    .formatToParts(date)
    .filter(o => o.type !== 'literal')
    .reduce((acc, o: Intl.DateTimeFormatPart) => {
      acc[o.type] = o.value;
      return acc;
    }, {} as DateTimeFormatParts);
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
  const pad = (num: number) => {
    const norm = Math.floor(Math.abs(num));
    return (norm < 10 ? '0' : '') + norm.toString();
  };
  return ` UTC${tzo >= 0 ? '+' : '-'}${pad(tzo / 60)}:${pad(tzo % 60)}`;
}
