/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma.js';
import './gr-date-formatter.js';
import {parseDate} from '../../../utils/date-util.js';
import {fixture, html} from '@open-wc/testing-helpers';
import {stubRestApi} from '../../../test/test-utils.js';

const basicTemplate = html`
  <gr-date-formatter withTooltip dateStr="2015-09-24 23:30:17.033000000">
  </gr-date-formatter>
`;

const lightTemplate = html`
  <gr-date-formatter dateStr="2015-09-24 23:30:17.033000000">
  </gr-date-formatter>
`;

suite('gr-date-formatter tests', () => {
  let element;

  setup(() => {
  });

  /**
   * Parse server-formatter date and normalize into current timezone.
   */
  function normalizedDate(dateStr) {
    const d = parseDate(dateStr);
    d.setMinutes(d.getMinutes() + d.getTimezoneOffset());
    return d;
  }

  async function testDates(nowStr, dateStr, expected, expectedWithDateAndTime,
      expectedTooltip) {
    // Normalize and convert the date to mimic server response.
    dateStr = normalizedDate(dateStr)
        .toJSON()
        .replace('T', ' ')
        .slice(0, -1);
    sinon.useFakeTimers(normalizedDate(nowStr).getTime());
    element.dateStr = dateStr;
    await element.updateComplete;
    const span = element.shadowRoot.querySelector('span');
    const tooltip = element.shadowRoot.querySelector('gr-tooltip-content');
    assert.equal(span.textContent.trim(), expected);
    assert.equal(tooltip.title, expectedTooltip);
    element.showDateAndTime = true;
    await element.updateComplete;
    assert.equal(span.textContent.trim(), expectedWithDateAndTime);
  }

  function stubRestAPI(preferences) {
    const loggedInPromise = Promise.resolve(preferences !== null);
    const preferencesPromise = Promise.resolve(preferences);
    stubRestApi('getLoggedIn').returns(loggedInPromise);
    stubRestApi('getPreferences').returns(preferencesPromise);
    return Promise.all([loggedInPromise, preferencesPromise]);
  }

  suite('STD + 24 hours time format preference', () => {
    setup(async () => {
      await stubRestAPI({
        time_format: 'HHMM_24',
        date_format: 'STD',
        relative_date_in_change_table: false,
      });

      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('invalid dates are quietly rejected', () => {
      assert.notOk((new Date('foo')).valueOf());
      element.dateStr = 'foo';
      element.timeFormat = 'h:mm A';
      assert.equal(element._computeDateStr(), '');
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          'Jul 29, 2015, 15:34:14');
    });

    test('Within 24 hours on different days', async () => {
      await testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          'Jul 28',
          'Jul 28 20:25',
          'Jul 28, 2015, 20:25:14');
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          'Jun 15',
          'Jun 15 03:25',
          'Jun 15, 2015, 03:25:14');
    });

    test('More than six months', async () => {
      await testDates('2015-09-15 20:34:00.000000000',
          '2015-01-15 03:25:00.000000000',
          'Jan 15, 2015',
          'Jan 15, 2015 03:25',
          'Jan 15, 2015, 03:25:00');
    });
  });

  suite('US + 24 hours time format preference', () => {
    setup(async () => {
      await stubRestAPI({
        time_format: 'HHMM_24',
        date_format: 'US',
        relative_date_in_change_table: false,
      });
      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '07/29/15, 15:34:14');
    });

    test('Within 24 hours on different days', async () => {
      await testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '07/28',
          '07/28 20:25',
          '07/28/15, 20:25:14');
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '06/15',
          '06/15 03:25',
          '06/15/15, 03:25:14');
    });
  });

  suite('ISO + 24 hours time format preference', () => {
    setup(async () => {
      await stubRestAPI({
        time_format: 'HHMM_24',
        date_format: 'ISO',
        relative_date_in_change_table: false,
      });

      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '2015-07-29, 15:34:14');
    });

    test('Within 24 hours on different days', async () => {
      await testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '07-28',
          '07-28 20:25',
          '2015-07-28, 20:25:14');
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '06-15',
          '06-15 03:25',
          '2015-06-15, 03:25:14');
    });
  });

  suite('EURO + 24 hours time format preference', () => {
    setup(async () => {
      await stubRestAPI({
        time_format: 'HHMM_24',
        date_format: 'EURO',
        relative_date_in_change_table: false,
      });

      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '29.07.2015, 15:34:14');
    });

    test('Within 24 hours on different days', async () => {
      await testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '28. Jul',
          '28. Jul 20:25',
          '28.07.2015, 20:25:14');
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '15. Jun',
          '15. Jun 03:25',
          '15.06.2015, 03:25:14');
    });
  });

  suite('UK + 24 hours time format preference', () => {
    setup(async () => {
      stubRestAPI({
        time_format: 'HHMM_24',
        date_format: 'UK',
        relative_date_in_change_table: false,
      });

      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '29/07/2015, 15:34:14');
    });

    test('Within 24 hours on different days', async () => {
      await testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '28/07',
          '28/07 20:25',
          '28/07/2015, 20:25:14');
    });

    test('More than 24 hours but less than six months', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '15/06',
          '15/06 03:25',
          '15/06/2015, 03:25:14');
    });
  });

  suite('STD + 12 hours time format preference', () => {
    setup(async () => {
      // relative_date_in_change_table is not set when false.
      await stubRestAPI({time_format: 'HHMM_12', date_format: 'STD'});
      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          'Jul 29, 2015, 3:34:14 PM');
    });
  });

  suite('US + 12 hours time format preference', () => {
    setup(async () => {
      // relative_date_in_change_table is not set when false.
      await stubRestAPI({time_format: 'HHMM_12', date_format: 'US'});
      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '07/29/15, 3:34:14 PM');
    });
  });

  suite('ISO + 12 hours time format preference', () => {
    setup(async () => {
      // relative_date_in_change_table is not set when false.
      await stubRestAPI({time_format: 'HHMM_12', date_format: 'ISO'});
      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '2015-07-29, 3:34:14 PM');
    });
  });

  suite('EURO + 12 hours time format preference', () => {
    setup(async () => {
      // relative_date_in_change_table is not set when false.
      await stubRestAPI({time_format: 'HHMM_12', date_format: 'EURO'});
      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '29.07.2015, 3:34:14 PM');
    });
  });

  suite('UK + 12 hours time format preference', () => {
    setup(async () => {
      // relative_date_in_change_table is not set when false.
      stubRestAPI({time_format: 'HHMM_12', date_format: 'UK'});
      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      await element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '29/07/2015, 3:34:14 PM');
    });
  });

  suite('relative date preference', () => {
    setup(async () => {
      stubRestAPI({
        time_format: 'HHMM_12',
        date_format: 'STD',
        relative_date_in_change_table: true,
      });
      element = await fixture(basicTemplate);
      sinon.stub(element, '_getUtcOffsetString').returns('');
      return element._loadPreferences();
    });

    test('Within 24 hours on same day', async () => {
      await testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '5 hours ago',
          '5 hours ago',
          'Jul 29, 2015, 3:34:14 PM');
    });

    test('More than six months', async () => {
      await testDates('2015-09-15 20:34:00.000000000',
          '2015-01-15 03:25:00.000000000',
          '8 months ago',
          '8 months ago',
          'Jan 15, 2015, 3:25:00 AM');
    });
  });

  suite('logged in', () => {
    setup(async () => {
      await stubRestAPI({
        time_format: 'HHMM_12',
        date_format: 'US',
        relative_date_in_change_table: true,
      });
      element = await fixture(basicTemplate);
      await element._loadPreferences();
    });

    test('Preferences are respected', () => {
      assert.equal(element.timeFormat, 'h:mm A');
      assert.equal(element.dateFormat.short, 'MM/DD');
      assert.equal(element.dateFormat.full, 'MM/DD/YY');
      assert.isTrue(element.relative);
    });
  });

  suite('logged out', () => {
    setup(async () => {
      await stubRestAPI(null);
      element = await fixture(basicTemplate);
      await element._loadPreferences();
    });

    test('Default preferences are respected', () => {
      assert.equal(element.timeFormat, 'HH:mm');
      assert.equal(element.dateFormat.short, 'MMM DD');
      assert.equal(element.dateFormat.full, 'MMM DD, YYYY');
      assert.isFalse(element.relative);
    });
  });

  suite('with tooltip', () => {
    setup(async () => {
      await stubRestAPI(null);
      element = await fixture(basicTemplate);
      await element._loadPreferences();
      await element.updateComplete;
    });

    test('Tooltip is present', () => {
      const tooltip = element.shadowRoot.querySelector('gr-tooltip-content');
      assert.isOk(tooltip);
    });
  });

  suite('without tooltip', () => {
    setup(async () => {
      await stubRestAPI(null);
      element = await fixture(lightTemplate);
      await element._loadPreferences();
      await element.updateComplete;
    });

    test('Tooltip is absent', () => {
      const tooltip = element.shadowRoot.querySelector('gr-tooltip-content');
      assert.isNotOk(tooltip);
    });
  });
});

