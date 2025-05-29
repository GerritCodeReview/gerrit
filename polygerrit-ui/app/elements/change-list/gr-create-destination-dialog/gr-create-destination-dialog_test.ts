/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './gr-create-destination-dialog';
import {GrCreateDestinationDialog} from './gr-create-destination-dialog';

suite('gr-create-destination-dialog tests', () => {
  let element: GrCreateDestinationDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-create-destination-dialog></gr-create-destination-dialog>`
    );
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <dialog id="createModal" tabindex="-1">
          <gr-dialog confirm-label="View commands" disabled="" role="dialog">
            <div class="header" slot="header">Create change</div>
            <div class="main" slot="main">
              <gr-repo-branch-picker> </gr-repo-branch-picker>
              <p>
                If you haven't done so, you will need to clone the repository.
              </p>
            </div>
          </gr-dialog>
        </dialog>
      `
    );
  });
});
