/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup-karma';
import {GrChecksResults} from './gr-checks-results';

suite('gr-checks-results test', () => {
  test('is defined', () => {
    const el = document.createElement('gr-checks-results');
    assert.instanceOf(el, GrChecksResults);
  });
});
