/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-account-chip';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrAccountChip} from './gr-account-chip';
import {createAccountWithIdNameAndEmail} from '../../../test/test-data-generators';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {ApprovalInfo, LabelInfo, Timestamp} from '../../../types/common';

suite('gr-account-chip screenshot tests', () => {
  let element: GrAccountChip;

  setup(async () => {
    const label: LabelInfo = {
      values: {
        '0': 'No score',
        '-1': 'Not recommended',
        '+1': 'Recommended',
      },
      default_value: 0,
    };
    const vote: ApprovalInfo = {
      value: 1,
      date: {_timestamp: '1452612012000'} as Timestamp,
    };
    element = await fixture<GrAccountChip>(
      html`<gr-account-chip></gr-account-chip>`
    );
    element.account = createAccountWithIdNameAndEmail();
    element.removable = true;
    element.vote = vote;
    element.label = label;
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-account-chip');
    await visualDiffDarkTheme(element, 'gr-account-chip');
  });
});
