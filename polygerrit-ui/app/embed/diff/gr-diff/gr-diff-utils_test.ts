/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {createElementDiff} from './gr-diff-utils';

suite('gr-diff-utils tests', () => {
  test('createElementDiff classStr applies all classes', () => {
    const node = createElementDiff('div', 'test classes');
    assert.isTrue(node.classList.contains('gr-diff'));
    assert.isTrue(node.classList.contains('test'));
    assert.isTrue(node.classList.contains('classes'));
  });
});
