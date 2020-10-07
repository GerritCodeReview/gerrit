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

import '../../../test/common-test-setup-karma.js';
import './gr-date-formatter.js';
import {parseDate} from '../../../utils/date-util.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
<gr-date-formatter date-str="2015-09-24 23:30:17.033000000"></gr-date-formatter>
`);

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

  function testDates(nowStr, dateStr, expected, expectedWithDateAndTime,
      expectedTooltip) {
    // Normalize and convert the date to mimic server response.
    dateStr = normalizedDate(dateStr)
        .toJSON()
        .replace('T', ' ')
        .slice(0, -1);
    sinon.useFakeTimers(normalizedDate(nowStr).getTime());
    element.dateStr = dateStr;
    flush();
    const span = element.shadowRoot
        .querySelector('span');
    assert.equal(span.textContent.trim(), expected);
    assert.equal(element.title, expectedTooltip);
    element.showDateAndTime = true;
    flush();
    assert.equal(span.textContent.trim(), expectedWithDateAndTime);
  }

  function stubRestAPI(preferences) {
    const loggedInPromise = Promise.resolve(preferences !== null);
    const preferencesPromise = Promise.resolve(preferences);
    stub('gr-rest-api-interface', {
      getLoggedIn: sinon.stub().returns(loggedInPromise),
      getPreferences: sinon.stub().returns(preferencesPromise),
    });
    return Promise.all([loggedInPromise, preferencesPromise]);
  }

  suite('STD + 24 hours time format preference', () => {
    setup(() => stubRestAPI({
      time_format: 'HHMM_24',
      date_format: 'STD',
      relative_date_in_change_table: false,
    }).then(() => {
      element = basicFixture.instantiate();
      sinon.stub(element, '_getUtcOffsetString').returns('');
      return element._loadPreferences();
    }));

    test('invalid dates are quietly rejected', () => {
      assert.notOk((new Date('foo')).valueOf());
      assert.equal(element._computeDateStr('foo', 'h:mm A'), '');
    });

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          'Jul 29, 2015, 15:34:14');
    });

    test('Within 24 hours on different days', () => {
      testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          'Jul 28',
          'Jul 28 20:25',
          'Jul 28, 2015, 20:25:14');
    });

    test('More than 24 hours but less than six months', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          'Jun 15',
          'Jun 15 03:25',
          'Jun 15, 2015, 03:25:14');
    });

    test('More than six months', () => {
      testDates('2015-09-15 20:34:00.000000000',
          '2015-01-15 03:25:00.000000000',
          'Jan 15, 2015',
          'Jan 15, 2015 03:25',
          'Jan 15, 2015, 03:25:00');
    });
  });

  suite('US + 24 hours time format preference', () => {
    setup(() => stubRestAPI({
      time_format: 'HHMM_24',
      date_format: 'US',
      relative_date_in_change_table: false,
    }).then(() => {
      element = basicFixture.instantiate();
      sinon.stub(element, '_getUtcOffsetString').returns('');
      return element._loadPreferences();
    }));

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '07/29/15, 15:34:14');
    });

    test('Within 24 hours on different days', () => {
      testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '07/28',
          '07/28 20:25',
          '07/28/15, 20:25:14');
    });

    test('More than 24 hours but less than six months', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '06/15',
          '06/15 03:25',
          '06/15/15, 03:25:14');
    });
  });

  suite('ISO + 24 hours time format preference', () => {
    setup(() => stubRestAPI({
      time_format: 'HHMM_24',
      date_format: 'ISO',
      relative_date_in_change_table: false,
    }).then(() => {
      element = basicFixture.instantiate();
      sinon.stub(element, '_getUtcOffsetString').returns('');
      return element._loadPreferences();
    }));

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '2015-07-29, 15:34:14');
    });

    test('Within 24 hours on different days', () => {
      testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '07-28',
          '07-28 20:25',
          '2015-07-28, 20:25:14');
    });

    test('More than 24 hours but less than six months', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '06-15',
          '06-15 03:25',
          '2015-06-15, 03:25:14');
    });
  });

  suite('EURO + 24 hours time format preference', () => {
    setup(() => stubRestAPI({
      time_format: 'HHMM_24',
      date_format: 'EURO',
      relative_date_in_change_table: false,
    }).then(() => {
      element = basicFixture.instantiate();
      sinon.stub(element, '_getUtcOffsetString').returns('');
      return element._loadPreferences();
    }));

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '29.07.2015, 15:34:14');
    });

    test('Within 24 hours on different days', () => {
      testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '28. Jul',
          '28. Jul 20:25',
          '28.07.2015, 20:25:14');
    });

    test('More than 24 hours but less than six months', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '15. Jun',
          '15. Jun 03:25',
          '15.06.2015, 03:25:14');
    });
  });

  suite('UK + 24 hours time format preference', () => {
    setup(() => stubRestAPI({
      time_format: 'HHMM_24',
      date_format: 'UK',
      relative_date_in_change_table: false,
    }).then(() => {
      element = basicFixture.instantiate();
      sinon.stub(element, '_getUtcOffsetString').returns('');
      return element._loadPreferences();
    }));

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '15:34',
          '15:34',
          '29/07/2015, 15:34:14');
    });

    test('Within 24 hours on different days', () => {
      testDates('2015-07-29 03:34:14.985000000',
          '2015-07-28 20:25:14.985000000',
          '28/07',
          '28/07 20:25',
          '28/07/2015, 20:25:14');
    });

    test('More than 24 hours but less than six months', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-06-15 03:25:14.985000000',
          '15/06',
          '15/06 03:25',
          '15/06/2015, 03:25:14');
    });
  });

  suite('STD + 12 hours time format preference', () => {
    setup(() =>
      // relative_date_in_change_table is not set when false.
      stubRestAPI(
          {time_format: 'HHMM_12', date_format: 'STD'}
      ).then(() => {
        element = basicFixture.instantiate();
        sinon.stub(element, '_getUtcOffsetString').returns('');
        return element._loadPreferences();
      })
    );

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          'Jul 29, 2015, 3:34:14 PM');
    });
  });

  suite('US + 12 hours time format preference', () => {
    setup(() =>
      // relative_date_in_change_table is not set when false.
      stubRestAPI(
          {time_format: 'HHMM_12', date_format: 'US'}
      ).then(() => {
        element = basicFixture.instantiate();
        sinon.stub(element, '_getUtcOffsetString').returns('');
        return element._loadPreferences();
      })
    );

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '07/29/15, 3:34:14 PM');
    });
  });

  suite('ISO + 12 hours time format preference', () => {
    setup(() =>
      // relative_date_in_change_table is not set when false.
      stubRestAPI(
          {time_format: 'HHMM_12', date_format: 'ISO'}
      ).then(() => {
        element = basicFixture.instantiate();
        sinon.stub(element, '_getUtcOffsetString').returns('');
        return element._loadPreferences();
      })
    );

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '2015-07-29, 3:34:14 PM');
    });
  });

  suite('EURO + 12 hours time format preference', () => {
    setup(() =>
      // relative_date_in_change_table is not set when false.
      stubRestAPI(
          {time_format: 'HHMM_12', date_format: 'EURO'}
      ).then(() => {
        element = basicFixture.instantiate();
        sinon.stub(element, '_getUtcOffsetString').returns('');
        return element._loadPreferences();
      })
    );

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '29.07.2015, 3:34:14 PM');
    });
  });

  suite('UK + 12 hours time format preference', () => {
    setup(() =>
      // relative_date_in_change_table is not set when false.
      stubRestAPI(
          {time_format: 'HHMM_12', date_format: 'UK'}
      ).then(() => {
        element = basicFixture.instantiate();
        sinon.stub(element, '_getUtcOffsetString').returns('');
        return element._loadPreferences();
      })
    );

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '3:34 PM',
          '3:34 PM',
          '29/07/2015, 3:34:14 PM');
    });
  });

  suite('relative date preference', () => {
    setup(() => stubRestAPI({
      time_format: 'HHMM_12',
      date_format: 'STD',
      relative_date_in_change_table: true,
    }).then(() => {
      element = basicFixture.instantiate();
      sinon.stub(element, '_getUtcOffsetString').returns('');
      return element._loadPreferences();
    }));

    test('Within 24 hours on same day', () => {
      testDates('2015-07-29 20:34:14.985000000',
          '2015-07-29 15:34:14.985000000',
          '5 hours',
          '5 hours',
          'Jul 29, 2015, 3:34:14 PM');
    });

    test('More than six months', () => {
      testDates('2015-09-15 20:34:00.000000000',
          '2015-01-15 03:25:00.000000000',
          '8 months',
          '8 months',
          'Jan 15, 2015, 3:25:00 AM');
    });
  });

  suite('logged in', () => {
    setup(() => stubRestAPI({
      time_format: 'HHMM_12',
      date_format: 'US',
      relative_date_in_change_table: true,
    }).then(() => {
      element = basicFixture.instantiate();
      return element._loadPreferences();
    }));

    test('Preferences are respected', () => {
      assert.equal(element._timeFormat, 'h:mm A');
      assert.equal(element._dateFormat.short, 'MM/DD');
      assert.equal(element._dateFormat.full, 'MM/DD/YY');
      assert.isTrue(element._relative);
    });
  });

  suite('logged out', () => {
    setup(() => stubRestAPI(null).then(() => {
      element = basicFixture.instantiate();
      return element._loadPreferences();
    }));

    test('Default preferences are respected', () => {
      assert.equal(element._timeFormat, 'HH:mm');
      assert.equal(element._dateFormat.short, 'MMM DD');
      assert.equal(element._dateFormat.full, 'MMM DD, YYYY');
      assert.isFalse(element._relative);
    });
  });
});

