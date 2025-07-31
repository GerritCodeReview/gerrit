/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-date-formatter';
import {GrDateFormatter} from './gr-date-formatter';
import {parseDate} from '../../../utils/date-util';
import {assert, fixture, html} from '@open-wc/testing';
import {query, queryAndAssert} from '../../../test/test-utils';
import {GrTooltipContent} from '../gr-tooltip-content/gr-tooltip-content';
import {Timestamp} from '../../../api/rest-api';
import {
  createDefaultPreferences,
  DateFormat,
  TimeFormat,
} from '../../../constants/constants';
import {PreferencesInfo} from '../../../types/common';

const basicTemplate = html`
  <gr-date-formatter withTooltip dateStr="2015-09-24 23:30:17.033000000">
  </gr-date-formatter>
`;

const lightTemplate = html`
  <gr-date-formatter dateStr="2015-09-24 23:30:17.033000000">
  </gr-date-formatter>
`;

suite('gr-date-formatter tests', () => {
  let element: GrDateFormatter;

  /**
   * Parse server-formatted date and normalize into current timezone.
   */
  function normalizedDate(dateStr: Timestamp) {
    const d = parseDate(dateStr);
    d.setMinutes(d.getMinutes() + d.getTimezoneOffset());
    return d;
  }

  function setPrefs(prefs: Partial<PreferencesInfo>) {
    element.setPreferences({...createDefaultPreferences(), ...prefs});
  }

  async function testDates(
    nowStr: string,
    dateStr: string,
    expected: string,
    expectedWithDateAndTime: string,
    expectedTooltip: string
  ) {
    // Normalize and convert the date to mimic server response.
    const normalizedDateStr = normalizedDate(dateStr as Timestamp)
      .toJSON()
      .replace('T', ' ')
      .slice(0, -1);
    sinon.useFakeTimers(normalizedDate(nowStr as Timestamp).getTime());
    element.dateStr = normalizedDateStr;
    await element.updateComplete;
    const span = queryAndAssert<HTMLSpanElement>(element, 'span');
    const tooltip = queryAndAssert<GrTooltipContent>(
      element,
      'gr-tooltip-content'
    );
    assert.equal(span.textContent?.trim(), expected);
    assert.equal(tooltip.title, expectedTooltip);
    element.showDateAndTime = true;
    await element.updateComplete;
    assert.equal(span.textContent?.trim(), expectedWithDateAndTime);
  }

  suite('STD + 24 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.STD, time_format: TimeFormat.HHMM_24});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '15:34',
        '15:34',
        'Wednesday, Jul 29, 2015, 15:34:14'
      );
    });

    test('Within 24 hours on different days', async () => {
      await testDates(
        '2015-07-29 03:34:14.985000000',
        '2015-07-28 20:25:14.985000000',
        'Jul 28',
        'Jul 28 20:25',
        'Tuesday, Jul 28, 2015, 20:25:14'
      );
    });

    test('Within one week', async () => {
      await testDates(
        '2015-07-22 03:34:14.985000000',
        '2015-07-29 02:25:14.985000000',
        'Jul 29',
        'Jul 29 02:25',
        'Wednesday, Jul 29, 2015, 02:25:14'
      );
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-06-15 03:25:14.985000000',
        'Jun 15',
        'Jun 15 03:25',
        'Monday, Jun 15, 2015, 03:25:14'
      );
    });

    test('More than six months', async () => {
      await testDates(
        '2015-09-15 20:34:00.000000000',
        '2015-01-15 03:25:00.000000000',
        'Jan 15, 2015',
        'Jan 15, 2015 03:25',
        'Thursday, Jan 15, 2015, 03:25:00'
      );
    });
  });

  suite('US + 24 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.US, time_format: TimeFormat.HHMM_24});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '15:34',
        '15:34',
        'Wednesday, 07/29/15, 15:34:14'
      );
    });

    test('Within 24 hours on different days', async () => {
      await testDates(
        '2015-07-29 03:34:14.985000000',
        '2015-07-28 20:25:14.985000000',
        '07/28',
        '07/28 20:25',
        'Tuesday, 07/28/15, 20:25:14'
      );
    });

    test('Within one week', async () => {
      await testDates(
        '2015-07-22 03:34:14.985000000',
        '2015-07-29 02:25:14.985000000',
        '07/29',
        '07/29 02:25',
        'Wednesday, 07/29/15, 02:25:14'
      );
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-06-15 03:25:14.985000000',
        '06/15',
        '06/15 03:25',
        'Monday, 06/15/15, 03:25:14'
      );
    });
  });

  suite('ISO + 24 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.ISO, time_format: TimeFormat.HHMM_24});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '15:34',
        '15:34',
        'Wednesday, 2015-07-29, 15:34:14'
      );
    });

    test('Within 24 hours on different days', async () => {
      await testDates(
        '2015-07-29 03:34:14.985000000',
        '2015-07-28 20:25:14.985000000',
        '07-28',
        '07-28 20:25',
        'Tuesday, 2015-07-28, 20:25:14'
      );
    });

    test('Within one week', async () => {
      await testDates(
        '2015-07-22 03:34:14.985000000',
        '2015-07-29 02:25:14.985000000',
        '07-29',
        '07-29 02:25',
        'Wednesday, 2015-07-29, 02:25:14'
      );
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-06-15 03:25:14.985000000',
        '06-15',
        '06-15 03:25',
        'Monday, 2015-06-15, 03:25:14'
      );
    });
  });

  suite('EURO + 24 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.EURO, time_format: TimeFormat.HHMM_24});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '15:34',
        '15:34',
        'Wednesday, 29.07.2015, 15:34:14'
      );
    });

    test('Within 24 hours on different days', async () => {
      await testDates(
        '2015-07-29 03:34:14.985000000',
        '2015-07-28 20:25:14.985000000',
        '28. Jul',
        '28. Jul 20:25',
        'Tuesday, 28.07.2015, 20:25:14'
      );
    });

    test('Within one week', async () => {
      await testDates(
        '2015-07-22 03:34:14.985000000',
        '2015-07-29 02:25:14.985000000',
        '29. Jul',
        '29. Jul 02:25',
        'Wednesday, 29.07.2015, 02:25:14'
      );
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-06-15 03:25:14.985000000',
        '15. Jun',
        '15. Jun 03:25',
        'Monday, 15.06.2015, 03:25:14'
      );
    });
  });

  suite('UK + 24 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.UK, time_format: TimeFormat.HHMM_24});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '15:34',
        '15:34',
        'Wednesday, 29/07/2015, 15:34:14'
      );
    });

    test('Within 24 hours on different days', async () => {
      await testDates(
        '2015-07-29 03:34:14.985000000',
        '2015-07-28 20:25:14.985000000',
        '28/07',
        '28/07 20:25',
        'Tuesday, 28/07/2015, 20:25:14'
      );
    });

    test('Within one week', async () => {
      await testDates(
        '2015-07-22 03:34:14.985000000',
        '2015-07-29 02:25:14.985000000',
        '29/07',
        '29/07 02:25',
        'Wednesday, 29/07/2015, 02:25:14'
      );
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-06-15 03:25:14.985000000',
        '15/06',
        '15/06 03:25',
        'Monday, 15/06/2015, 03:25:14'
      );
    });

    test('More than six months', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2016-08-15 03:25:14.985000000',
        '15/08/2016',
        '15/08/2016 03:25',
        'Monday, 15/08/2016, 03:25:14'
      );
    });
  });

  suite('STD + 12 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.STD, time_format: TimeFormat.HHMM_12});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '3:34 PM',
        '3:34 PM',
        'Wednesday, Jul 29, 2015, 3:34:14 PM'
      );
    });
  });

  suite('US + 12 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.US, time_format: TimeFormat.HHMM_12});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '3:34 PM',
        '3:34 PM',
        'Wednesday, 07/29/15, 3:34:14 PM'
      );
    });
  });

  suite('ISO + 12 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.ISO, time_format: TimeFormat.HHMM_12});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '3:34 PM',
        '3:34 PM',
        'Wednesday, 2015-07-29, 3:34:14 PM'
      );
    });
  });

  suite('EURO + 12 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.EURO, time_format: TimeFormat.HHMM_12});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '3:34 PM',
        '3:34 PM',
        'Wednesday, 29.07.2015, 3:34:14 PM'
      );
    });
  });

  suite('UK + 12 hours time format preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({date_format: DateFormat.UK, time_format: TimeFormat.HHMM_12});
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '3:34 PM',
        '3:34 PM',
        'Wednesday, 29/07/2015, 3:34:14 PM'
      );
    });
  });

  suite('relative date preference', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({
        date_format: DateFormat.STD,
        time_format: TimeFormat.HHMM_12,
        relative_date_in_change_table: true,
      });
      sinon.stub(element, 'getUtcOffsetString').returns('');
    });

    test('Within 24 hours on same day (Past)', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 15:34:14.985000000',
        '5 hours ago',
        '5 hours ago',
        'Wednesday, Jul 29, 2015, 3:34:14 PM'
      );
    });

    test('Within 24 hours on same day (Future)', async () => {
      await testDates(
        '2015-07-29 20:34:14.985000000',
        '2015-07-29 23:34:14.985000000',
        'in 3 hours',
        'in 3 hours',
        'Wednesday, Jul 29, 2015, 11:34:14 PM'
      );
    });

    test('More than six months (Past)', async () => {
      await testDates(
        '2015-09-15 20:34:00.000000000',
        '2015-01-15 03:25:00.000000000',
        '8 months ago',
        '8 months ago',
        'Thursday, Jan 15, 2015, 3:25:00 AM'
      );
    });

    test('More than six months (Future)', async () => {
      await testDates(
        '2015-01-15 20:34:00.000000000',
        '2015-09-15 03:25:00.000000000',
        'in 8 months',
        'in 8 months',
        'Tuesday, Sep 15, 2015, 3:25:00 AM'
      );
    });
  });

  suite('logged in', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({
        date_format: DateFormat.US,
        time_format: TimeFormat.HHMM_12,
        relative_date_in_change_table: true,
      });
    });

    test('Preferences are respected', () => {
      assert.equal(element.timeFormat, 'h:mm A');
      assert.equal(element.dateFormat?.short, 'MM/DD');
      assert.equal(element.dateFormat?.full, 'MM/DD/YY');
      assert.isTrue(element.relative);
    });
  });

  suite('logged out', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({});
    });

    test('Default preferences are respected', () => {
      assert.equal(element.timeFormat, 'h:mm A');
      assert.equal(element.dateFormat?.short, 'MMM DD');
      assert.equal(element.dateFormat?.full, 'MMM DD, YYYY');
      assert.isFalse(element.relative);
    });
  });

  suite('with tooltip', () => {
    setup(async () => {
      element = await fixture(basicTemplate);
      setPrefs({});
      await element.updateComplete;
    });

    test('Tooltip is present', () => {
      const tooltip = queryAndAssert<GrTooltipContent>(
        element,
        'gr-tooltip-content'
      );
      assert.isOk(tooltip);
    });
  });

  suite('without tooltip', () => {
    setup(async () => {
      element = await fixture(lightTemplate);
      setPrefs({});
      await element.updateComplete;
    });

    test('Tooltip is absent', () => {
      const tooltip = query<GrTooltipContent>(element, 'gr-tooltip-content');
      assert.isNotOk(tooltip);
    });
  });
});
