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
  other: string;
}

export class TestModel extends Model<TestModelState> {
  constructor() {
    super({something: '', other: ''});
  }
}

suite('model tests', () => {
  test('setState update in progress', async () => {
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
      if (s.something === '1') model.setState({something: '2', other: ''});
    });

    // This call should return before the subscriber calls `setState()` again.
    model.setState({something: '1', other: ''});
    firstUpdateCompleted = true;

    await waitUntil(() => secondUpdateCompleted);
  });

  test('updateState update in progress', async () => {
    const model = new TestModel();
    let firstUpdateCompleted = false;
    let secondUpdateCompleted = false;
    model.state$.subscribe(s => {
      if (s.other === 'x') {
        // Otherwise this would be a clear indication of a nested `setState()`
        // call, which `stateUpdateInProgress` is supposed to avoid.
        assert.isTrue(firstUpdateCompleted);
        // The second update (x) should include the first update (1).
        assert.equal(s.something, '1');
        secondUpdateCompleted = true;
      }
      if (s.something === '1') model.updateState({other: 'x'});
    });

    // This call should return before the subscriber calls `setState()` again.
    model.updateState({something: '1'});
    firstUpdateCompleted = true;

    await waitUntil(() => secondUpdateCompleted);
  });
});
