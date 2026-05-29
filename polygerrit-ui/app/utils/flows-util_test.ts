/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {computeFlowString, Stage} from './flows-util';

suite('flows-util tests', () => {
  suite('computeFlowString', () => {
    test('empty stages', () => {
      const stages: Stage[] = [];
      assert.equal(computeFlowString(stages), '');
    });

    test('single stage with condition only', () => {
      const stages: Stage[] = [
        {condition: 'cond 1', action: '', parameterStr: ''},
      ];
      assert.equal(computeFlowString(stages), 'cond 1');
    });

    test('single stage with condition and action', () => {
      const stages: Stage[] = [
        {condition: 'cond 1', action: 'act-1', parameterStr: ''},
      ];
      assert.equal(computeFlowString(stages), 'cond 1 -> act-1');
    });

    test('single stage with condition, action, and params', () => {
      const stages: Stage[] = [
        {condition: 'cond 1', action: 'act-1', parameterStr: 'param1 param2'},
      ];
      assert.equal(computeFlowString(stages), 'cond 1 -> act-1 param1 param2');
    });

    test('multiple stages', () => {
      const stages: Stage[] = [
        {condition: 'cond 1', action: 'act-1', parameterStr: ''},
        {condition: 'cond 2', action: 'act-2', parameterStr: 'p2'},
        {condition: 'cond 3', action: '', parameterStr: ''},
      ];
      assert.equal(
        computeFlowString(stages),
        'cond 1 -> act-1;cond 2 -> act-2 p2;cond 3'
      );
    });
  });
});
