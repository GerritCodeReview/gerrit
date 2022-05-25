/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-file-status-chip';
import {GrFileStatusChip} from './gr-file-status-chip';
import {fixture} from '@open-wc/testing-helpers';
import {FileInfo} from '../../../api/rest-api';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';

suite('gr-file-status-chip tests', () => {
  let element: GrFileStatusChip;

  setup(async () => {
    element = await fixture<GrFileStatusChip>(
      '<gr-file-status-chip></gr-file-status-chip>'
    );
    await element.updateComplete;
  });

  const setFile = async (file: Partial<NormalizedFileInfo>) => {
    element.file = {
      ...createFileInfo(),
      ...file,
    };
    await element.updateComplete;
  };

  suite('semantic dom diff tests', () => {
    test('modified', () => {
      expect(element).shadowDom.to.equal(/* HTML */ `
        <span aria-label="Modified" tabindex="0" title="Modified"
          >Modified</span
        >
      `);
    });

    test('computed properties', () => {
      assert.equal(element.computeFileStatus('A'), 'A');
      assert.equal(element.computeFileStatus(undefined), 'M');

      assert.equal(element.computeClass('clazz', '/foo/bar/baz'), 'clazz');
      assert.equal(
        element.computeClass('clazz', '/COMMIT_MSG'),
        'clazz invisible'
      );
    });

    test('computeFileStatusLabel', () => {
      assert.equal(element.computeLabel('A'), 'Added');
      assert.equal(element.computeLabel('M'), 'Modified');
    });
  });
});
