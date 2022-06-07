/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import {toSortedPermissionsArray} from './access-util';

suite('access-util tests', () => {
  test('toSortedPermissionsArray', () => {
    const rules = {
      'global:Project-Owners': {
        action: 'ALLOW',
        force: false,
      },
      '4c97682e6ce6b7247f3381b6f1789356666de7f': {
        action: 'ALLOW',
        force: false,
      },
    };
    const expectedResult = [
      {
        id: '4c97682e6ce6b7247f3381b6f1789356666de7f',
        value: {
          action: 'ALLOW',
          force: false,
        },
      },
      {
        id: 'global:Project-Owners',
        value: {
          action: 'ALLOW',
          force: false,
        },
      },
    ];
    assert.deepEqual(toSortedPermissionsArray(rules), expectedResult);
  });
});
