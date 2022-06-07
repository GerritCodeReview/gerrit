/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Timestamp} from '../types/common';
import '../test/common-test-setup-karma';
import {
  isValidDate,
  parseDate,
  fromNow,
  isWithinDay,
  isWithinHalfYear,
  formatDate,
  wasYesterday,
} from './date-util';

suite('date-util tests', () => {
  suite('parseDate', () => {
    test('parseDate server date', () => {
      const parsed = parseDate('2015-09-15 20:34:00.000000000' as Timestamp);
      assert.equal('2015-09-15T20:34:00.000Z', parsed.toISOString());
    });
  });

  suite('isValidDate', () => {
    test('date is valid', () => {
      assert.isTrue(isValidDate(new Date()));
    });
    test('broken date is invalid', () => {
      assert.isFalse(isValidDate(new Date('xxx')));
    });
  });

  suite('fromNow', () => {
    test('test all variants', () => {
      const fakeNow = new Date('May 08 2020 12:00:00');
      sinon.useFakeTimers(fakeNow.getTime());
      assert.equal('just now', fromNow(new Date('May 08 2020 11:59:30')));
      assert.equal('1 minute ago', fromNow(new Date('May 08 2020 11:59:00')));
      assert.equal('5 minutes ago', fromNow(new Date('May 08 2020 11:55:00')));
      assert.equal('1 hour ago', fromNow(new Date('May 08 2020 11:00:00')));
      assert.equal(
        '1 hour 5 min ago',
        fromNow(new Date('May 08 2020 10:55:00'))
      );
      assert.equal('3 hours ago', fromNow(new Date('May 08 2020 9:00:00')));
      assert.equal('1 day ago', fromNow(new Date('May 07 2020 12:00:00')));
      assert.equal('1 day 2 hr ago', fromNow(new Date('May 07 2020 10:00:00')));
      assert.equal('3 days ago', fromNow(new Date('May 05 2020 12:00:00')));
      assert.equal('1 month ago', fromNow(new Date('Apr 05 2020 12:00:00')));
      assert.equal('2 months ago', fromNow(new Date('Mar 05 2020 12:00:00')));
      assert.equal('1 year ago', fromNow(new Date('May 05 2019 12:00:00')));
      assert.equal('10 years ago', fromNow(new Date('May 05 2010 12:00:00')));
    });
    test('rounding error', () => {
      const fakeNow = new Date('May 08 2020 12:00:00');
      sinon.useFakeTimers(fakeNow.getTime());
      assert.equal('2 hours ago', fromNow(new Date('May 08 2020 9:30:00')));
    });
  });

  suite('isWithinDay', () => {
    test('basics works', () => {
      assert.isTrue(
        isWithinDay(
          new Date('May 08 2020 12:00:00'),
          new Date('May 08 2020 02:00:00')
        )
      );
      assert.isFalse(
        isWithinDay(
          new Date('May 08 2020 12:00:00'),
          new Date('May 07 2020 12:00:00')
        )
      );
    });
  });

  suite('wasYesterday', () => {
    test('less 24 hours', () => {
      assert.isFalse(
        wasYesterday(
          new Date('May 08 2020 12:00:00'),
          new Date('May 08 2020 02:00:00')
        )
      );
      assert.isTrue(
        wasYesterday(
          new Date('May 08 2020 12:00:00'),
          new Date('May 07 2020 12:00:00')
        )
      );
    });
    test('more 24 hours', () => {
      assert.isTrue(
        wasYesterday(
          new Date('May 08 2020 12:00:00'),
          new Date('May 07 2020 2:00:00')
        )
      );
      assert.isFalse(
        wasYesterday(
          new Date('May 08 2020 12:00:00'),
          new Date('May 06 2020 14:00:00')
        )
      );
    });
  });

  suite('isWithinHalfYear', () => {
    test('basics works', () => {
      assert.isTrue(
        isWithinHalfYear(
          new Date('May 08 2020 12:00:00'),
          new Date('Feb 08 2020 12:00:00')
        )
      );
      assert.isFalse(
        isWithinHalfYear(
          new Date('May 08 2020 12:00:00'),
          new Date('Nov 07 2019 12:00:00')
        )
      );
    });
  });

  suite('formatDate', () => {
    test('works for standard format', () => {
      const stdFormat = 'MMM DD, YYYY';
      assert.equal(
        'May 08, 2020',
        formatDate(new Date('May 08 2020 12:00:00'), stdFormat)
      );
      assert.equal(
        'Feb 28, 2020',
        formatDate(new Date('Feb 28 2020 12:00:00'), stdFormat)
      );

      const time24Format = 'HH:mm:ss';
      assert.equal(
        'Feb 28, 2020 12:01:12',
        formatDate(
          new Date('Feb 28 2020 12:01:12'),
          stdFormat + ' ' + time24Format
        )
      );
    });
    test('works for euro format', () => {
      const euroFormat = 'DD.MM.YYYY';
      assert.equal(
        '01.12.2019',
        formatDate(new Date('Dec 01 2019 12:00:00'), euroFormat)
      );
      assert.equal(
        '20.01.2002',
        formatDate(new Date('Jan 20 2002 12:00:00'), euroFormat)
      );

      const time24Format = 'HH:mm:ss';
      assert.equal(
        '28.02.2020 00:01:12',
        formatDate(
          new Date('Feb 28 2020 00:01:12'),
          euroFormat + ' ' + time24Format
        )
      );
    });
    test('works for iso format', () => {
      const isoFormat = 'YYYY-MM-DD';
      assert.equal(
        '2015-01-01',
        formatDate(new Date('Jan 01 2015 12:00:00'), isoFormat)
      );
      assert.equal(
        '2013-07-03',
        formatDate(new Date('Jul 03 2013 12:00:00'), isoFormat)
      );

      const timeFormat = 'h:mm:ss A';
      assert.equal(
        '2013-07-03 5:00:00 AM',
        formatDate(
          new Date('Jul 03 2013 05:00:00'),
          isoFormat + ' ' + timeFormat
        )
      );
      assert.equal(
        '2013-07-03 5:00:00 PM',
        formatDate(
          new Date('Jul 03 2013 17:00:00'),
          isoFormat + ' ' + timeFormat
        )
      );
    });
    test('h:mm:ss A shows correctly midnight and midday', () => {
      const timeFormat = 'h:mm A';
      assert.equal(
        '12:14 PM',
        formatDate(new Date('Jul 03 2013 12:14:00'), timeFormat)
      );
      assert.equal(
        '12:15 AM',
        formatDate(new Date('Jul 03 2013 00:15:00'), timeFormat)
      );
    });
  });
});
