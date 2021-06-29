/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-file-status-chip';
import {GrFileStatusChip} from './gr-file-status-chip';

const fixture = fixtureFromElement('gr-file-status-chip');

suite('gr-file-status-chip tests', () => {
  let element: GrFileStatusChip;

  setup(() => {
    element = fixture.instantiate();
  });

  test('computed properties', () => {
    assert.equal(element._computeFileStatus('A'), 'A');
    assert.equal(element._computeFileStatus(undefined), 'M');

    assert.equal(element._computeClass('clazz', '/foo/bar/baz'), 'clazz');
    assert.equal(
      element._computeClass('clazz', '/COMMIT_MSG'),
      'clazz invisible'
    );
  });

  test('_computeFileStatusLabel', () => {
    assert.equal(element._computeFileStatusLabel('A'), 'Added');
    assert.equal(element._computeFileStatusLabel('M'), 'Modified');
  });
});
