/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, waitUntil} from '@open-wc/testing';
import '../../test/common-test-setup';
import {Model} from './model';

interface TestModelState {
  prop1?: string;
  prop2?: string;
  prop3?: string;
}

export class TestModel extends Model<TestModelState> {
  constructor() {
    super({});
  }
}

suite('model tests', () => {
  test('setState update in progress', async () => {
    const model = new TestModel();
    let firstUpdateCompleted = false;
    let secondUpdateCompleted = false;
    model.state$.subscribe(s => {
      if (s.prop2 === 'set') {
        // Otherwise this would be a clear indication of a nested `setState()`
        // call, which `stateUpdateInProgress` is supposed to avoid.
        assert.isTrue(firstUpdateCompleted);
        secondUpdateCompleted = true;
      }
      if (s.prop1 === 'set' && s.prop2 !== 'set')
        model.setState({prop2: 'set'});
    });

    // This call should return before the subscriber calls `setState()` again.
    model.setState({prop1: 'set'});
    firstUpdateCompleted = true;

    await waitUntil(() => secondUpdateCompleted);
  });

  test('updateState update in progress', async () => {
    const model = new TestModel();
    let completed = false;
    model.state$.subscribe(s => {
      if (s.prop1 !== 'go') return;
      if (s.prop2 !== 'set' && s.prop3 !== 'set')
        model.updateState({prop2: 'set'});
      if (s.prop2 === 'set' && s.prop3 === 'set') completed = true;
    });
    model.state$.subscribe(s => {
      if (s.prop1 !== 'go') return;
      if (s.prop2 !== 'set' && s.prop3 !== 'set')
        model.updateState({prop3: 'set'});
      if (s.prop2 === 'set' && s.prop3 === 'set') completed = true;
    });

    model.updateState({prop1: 'go'});

    await waitUntil(() => completed);
  });
});
