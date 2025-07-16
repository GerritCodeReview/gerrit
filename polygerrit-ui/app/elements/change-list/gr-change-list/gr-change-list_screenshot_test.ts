/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-list';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrChangeList} from './gr-change-list';
import {createChange} from '../../../test/test-data-generators';
import {ChangeInfo, NumericChangeId, Timestamp} from '../../../types/common';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-change-list screenshot tests', () => {
  let element: GrChangeList;

  function createChanges(count: number): ChangeInfo[] {
    return Array.from(Array(count).keys()).map(index => {
      return {
        ...createChange(),
        _number: (index + 1) as NumericChangeId,
        subject: `Change subject ${index + 1}`,
        updated: `2020-01-${String(index + 1).padStart(
          2,
          '0'
        )} 10:00:00.000000000` as Timestamp,
      };
    });
  }

  setup(async () => {
    element = await fixture(html`<gr-change-list></gr-change-list>`);
    element.changes = createChanges(5);
    await element.updateComplete;
  });

  test('basic list', async () => {
    await visualDiff(element, 'gr-change-list');
    await visualDiffDarkTheme(element, 'gr-change-list');
  });
});
