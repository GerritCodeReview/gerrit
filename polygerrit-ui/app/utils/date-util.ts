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

export function dateToTimestamp(date: Date): Timestamp {
  return date.toISOString().replace('T', ' ').replace('Z', '') as Timestamp;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function isValidDate(date: any): date is Date {
  return date instanceof Date && !isNaN(date.valueOf());
}

// similar to fromNow from moment.js
export function relative(date: Date, affix = false) {
  const now = new Date();
  if (now > date) {
    return fromNow(date, affix);
  } else {
    return toNow(date, affix);
  }
}

// similar to fromNow from moment.js
export function fromNow(date: Date, noAgo = false) {
  return durationString(date, new Date(), '', noAgo ? '' : 'ago');
}

// similar to toNow from moment.js
export function toNow(date: Date, noIn = false) {
  return durationString(new Date(), date, noIn ? '' : 'in', '');
}

// similar to fromNow from moment.js
export function durationString(
  from: Date,
  to: Date,
  prefix: string = '',
  suffix: string = ''
) {
  prefix = prefix ? `${prefix.trim()} ` : '';
  suffix = suffix ? ` ${suffix.trim()}` : '';
  const seconds = Math.floor((to.valueOf() - from.valueOf()) / 1000);
  if (seconds <= 59) {
    if (!prefix && !suffix) return `${seconds} seconds`;
    return 'just now';
  }
  if (seconds <= 119) return `${prefix}1 minute${suffix}`;
  const minutes = Math.floor(seconds / 60);
  if (minutes <= 59) return `${prefix}${minutes} minutes${suffix}`;
  if (minutes === 60) return `${prefix}1 hour${suffix}`;
  if (minutes <= 119) return `${prefix}1 hour ${minutes - 60} min${suffix}`;
  const hours = Math.floor(minutes / 60);
  if (hours <= 23) return `${prefix}${hours} hours${suffix}`;
  if (hours === 24) return `${prefix}1 day${suffix}`;
  if (hours <= 47) return `${prefix}1 day ${hours - 24} hr${suffix}`;
  const days = Math.floor(hours / 24);
  if (days <= 30) return `${prefix}${days} days${suffix}`;
  if (days <= 60) return `${prefix}1 month${suffix}`;
  const months = Math.floor(days / 30);
  if (months <= 11) return `${prefix}${months} months${suffix}`;
  if (months === 12) return `${prefix}1 year${suffix}`;
  if (months <= 24) return `${prefix}1 year ${months - 12} m${suffix}`;
  const years = Math.floor(days / 365);
  return `${prefix}${years} years${suffix}`;
}

/**
 * Return true if date is within 24 hours and on the same day.
 */
export function isWithinDay(now: Date, date: Date) {
  const diff = Math.abs(now.valueOf() - date.valueOf());
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
  const diff = Math.abs(now.valueOf() - date.valueOf());
  return diff < 180 * Duration.DAY;
}

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DateTimeFormat/formatToParts
interface DateTimeFormatParts {
  year?: string;
  month?: string;
  day?: string;
  hour?: string;
  minute?: string;
  second?: string;
  // AM or PM
  dayPeriod?: string;
  weekday?: string;
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

  if (format.includes('ddd')) {
    if (format.includes('dddd')) {
      options.weekday = 'long';
    } else {
      options.weekday = 'short';
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

  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DateTimeFormat/formatToParts
  const dtfParts = new Intl.DateTimeFormat(locale, options).formatToParts(date);
  const parts: DateTimeFormatParts = {};
  for (const entry of dtfParts) {
    switch (entry.type) {
      case 'year':
        parts.year = entry.value;
        break;
      case 'month':
        parts.month = entry.value;
        break;
      case 'day':
        parts.day = entry.value;
        break;
      case 'hour':
        parts.hour = entry.value;
        break;
      case 'minute':
        parts.minute = entry.value;
        break;
      case 'second':
        parts.second = entry.value;
        break;
      case 'dayPeriod':
        parts.dayPeriod = entry.value;
        break;
      case 'weekday':
        parts.weekday = entry.value;
        break;
    }
  }
  if (parts.year && format.includes('YY')) {
    if (format.includes('YYYY')) {
      format = format.replace('YYYY', parts.year);
    } else {
      format = format.replace('YY', parts.year);
    }
  }

  if (parts.day && format.includes('DD')) {
    format = format.replace('DD', parts.day);
  }

  if (parts.hour && format.includes('HH')) {
    format = format.replace('HH', parts.hour);
  }

  if (parts.hour && format.includes('h')) {
    format = format.replace('h', parts.hour);
  }

  if (parts.minute && format.includes('mm')) {
    format = format.replace('mm', parts.minute);
  }

  if (parts.second && format.includes('ss')) {
    format = format.replace('ss', parts.second);
  }

  if (parts.dayPeriod && format.includes('A')) {
    format = format.replace('A', parts.dayPeriod.toUpperCase());
  }

  // Month and weekday must be last, because they will yield characters that
  // could be interpreted as format strings, e.g. `h` in `Thursday` would
  // otherwise be replaced by "hours".

  if (parts.month && format.includes('MM')) {
    if (format.includes('MMM')) {
      format = format.replace('MMM', parts.month);
    } else {
      format = format.replace('MM', parts.month);
    }
  }

  if (parts.weekday && format.includes('ddd')) {
    if (format.includes('dddd')) {
      format = format.replace('dddd', parts.weekday);
    } else {
      format = format.replace('ddd', parts.weekday);
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
