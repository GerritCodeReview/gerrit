/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, waitUntil} from '@open-wc/testing';
import '../test/common-test-setup';
import {Model} from './model';

interface TestModelState {
  something: string;
}

export class TestModel extends Model<TestModelState> {
  constructor() {
    super({something: ''});
  }
}

suite('model tests', () => {
  test('stateUpdateInProgress', async () => {
    const model = new TestModel();
    let firstUpdateCompleted = false;
    let secondUpdateCompleted = false;
    model.state$.subscribe(s => {
      if (s.something === '2') {
        // Otherwise this would be a clear indication of a nested `setState()`
        // call, which `stateUpdateInProgress` is supposed to avoid.
        assert.isTrue(firstUpdateCompleted);
        secondUpdateCompleted = true;
      }
      if (s.something === '1') model.setState({something: '2'});
    });

    // This call should return before the subscriber calls `setState()` again.
    model.setState({something: '1'});
    firstUpdateCompleted = true;

    await waitUntil(() => secondUpdateCompleted);
  });
});
