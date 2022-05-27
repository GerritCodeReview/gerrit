/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-create-commands-dialog';
import {GrCreateCommandsDialog} from './gr-create-commands-dialog';

const basicFixture = fixtureFromElement('gr-create-commands-dialog');

suite('gr-create-commands-dialog tests', () => {
  let element: GrCreateCommandsDialog;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('branch', () => {
    element.branch = 'master';
    assert.equal(element.branch, 'master');
  });
});
