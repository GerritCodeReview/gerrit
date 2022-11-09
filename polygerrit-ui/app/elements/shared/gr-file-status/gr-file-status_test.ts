/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-file-status';
import {GrFileStatus} from './gr-file-status';
import {fixture, assert} from '@open-wc/testing';
import {FileInfoStatus} from '../../../api/rest-api';

suite('gr-file-status tests', () => {
  let element: GrFileStatus;

  setup(async () => {
    element = await fixture<GrFileStatus>('<gr-file-status></gr-file-status>');
    await setStatus();
  });

  const setStatus = async (status?: FileInfoStatus, newly = false) => {
    element.status = status;
    element.newlyChanged = newly;
    await element.updateComplete;
  };

  suite('semantic dom diff tests', () => {
    test('empty status', async () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-tooltip-content has-tooltip="" title="" aria-label="">
            <div class="status" aria-hidden="true"><span></span></div>
          </gr-tooltip-content>
        `
      );
    });

    test('added', async () => {
      await setStatus(FileInfoStatus.ADDED);
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-tooltip-content has-tooltip="" title="Added" aria-label="Added">
            <div class="A status" aria-hidden="true">
              <span>A</span>
            </div>
          </gr-tooltip-content>
        `
      );
    });

    test('newly added', async () => {
      await setStatus(FileInfoStatus.ADDED, true);
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-tooltip-content
            has-tooltip=""
            title="Newly Added"
            aria-label="newly"
          >
            <gr-icon icon="new_releases" class="size-16"></gr-icon>
          </gr-tooltip-content>
          <gr-tooltip-content
            has-tooltip=""
            title="Newly Added"
            aria-label="Added"
          >
            <div class="A status" aria-hidden="true">
              <span>A</span>
            </div>
          </gr-tooltip-content>
        `
      );
    });
  });
});
