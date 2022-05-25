/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-file-status-chip';
import {GrFileStatusChip} from './gr-file-status-chip';
import {fixture} from '@open-wc/testing-helpers';
import {FileInfoStatus} from '../../../api/rest-api';
import {queryAndAssert} from '../../../utils/common-util';
import {isVisible} from '../../../test/test-utils';

suite('gr-file-status-chip tests', () => {
  let element: GrFileStatusChip;

  setup(async () => {
    element = await fixture<GrFileStatusChip>(
      '<gr-file-status-chip></gr-file-status-chip>'
    );
    await setFile();
  });

  const setFile = async (
    status?: FileInfoStatus,
    path = 'test-path/test-file.txt'
  ) => {
    element.file = {
      status,
      __path: path,
    };
    await element.updateComplete;
  };

  suite('semantic dom diff tests', () => {
    test('modified by default', async () => {
      expect(element).shadowDom.to.equal(/* HTML */ `
        <span
          class="M status"
          aria-label="Modified"
          tabindex="0"
          title="Modified"
          >Modified</span
        >
      `);
      const span = queryAndAssert(element, 'span');
      assert.isFalse(isVisible(span));
    });

    test('added', async () => {
      await setFile(FileInfoStatus.ADDED);
      expect(element).shadowDom.to.equal(/* HTML */ `
        <span class="A status" aria-label="Added" tabindex="0" title="Added"
          >Added</span
        >
      `);
      const span = queryAndAssert(element, 'span');
      assert.isTrue(isVisible(span));
    });

    test('invisible for special path', async () => {
      await setFile(undefined, '/COMMIT_MSG');
      expect(element).shadowDom.to.equal(/* HTML */ `
        <span
          class="M invisible status"
          aria-label="Modified"
          tabindex="0"
          title="Modified"
          >Modified</span
        >
      `);
      const span = queryAndAssert(element, 'span');
      assert.isFalse(isVisible(span));
    });
  });
});
