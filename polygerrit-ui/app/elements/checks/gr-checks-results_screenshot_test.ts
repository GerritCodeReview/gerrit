/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
import './gr-checks-results';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {checksModelToken} from '../../models/checks/checks-model';
import {setAllcheckRuns} from '../../test/test-data-generators';
import {resolve} from '../../models/dependency';
import {GrChecksResults} from './gr-checks-results';
import {visualDiffDarkTheme} from '../../test/test-utils';

suite('gr-checks-results screenshot', () => {
  let element: GrChecksResults;

  setup(async () => {
    element = await fixture<GrChecksResults>(
      html`<gr-checks-results></gr-checks-results>`
    );
    const getChecksModel = resolve(element, checksModelToken);
    getChecksModel().allRunsSelectedPatchset$.subscribe(
      runs => (element.runs = runs)
    );
    setAllcheckRuns(getChecksModel());
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-checks-results');
    await visualDiffDarkTheme(element, 'gr-checks-results');
  });
});
