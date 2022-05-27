/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {createRunResult} from '../../test/test-data-generators';
import {matches} from './gr-checks-util';
import {RunResult} from '../../models/checks/checks-model';

suite('gr-checks-util test', () => {
  test('regexp filter matching results', () => {
    const result: RunResult = {
      ...createRunResult(),
      tags: [{name: 'tag'}],
    };
    assert.isTrue(matches(result, new RegExp('message')));
    assert.isTrue(matches(result, new RegExp('summary')));
    assert.isTrue(matches(result, new RegExp('name')));
    assert.isTrue(matches(result, new RegExp('tag')));
    assert.isFalse(matches(result, new RegExp('qwertyui')));
  });
});
