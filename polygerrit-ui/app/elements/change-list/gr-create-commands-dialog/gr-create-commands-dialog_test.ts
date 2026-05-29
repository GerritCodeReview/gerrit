/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import './gr-create-commands-dialog';
import {GrCreateCommandsDialog} from './gr-create-commands-dialog';

suite('gr-create-commands-dialog tests', () => {
  let element: GrCreateCommandsDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-create-commands-dialog></gr-create-commands-dialog>`
    );
  });

  test('branch', () => {
    element.branch = 'master';
    assert.equal(element.branch, 'master');
  });

  test('render', () => {
    // prettier and shadowDom assert don't agree about wrapping in the <p> tags
    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
      <dialog id="commandsModal" tabindex="-1">
        <gr-dialog
          cancel-label=""
          confirm-label="Done"
          confirm-on-enter=""
          id="commandsDialog"
          role="dialog"
        >
          <div class="header" slot="header">Create change commands</div>
          <div class="main" slot="main">
            <ol>
              <li>
                <p>Make the changes to the files on your machine</p>
              </li>
              <li>
                <p>If you are making a new commit use</p>
                <gr-shell-command> </gr-shell-command>
                <p>Or to amend an existing commit use</p>
                <gr-shell-command> </gr-shell-command>
                <p>
                  Please make sure you add a commit message as it becomes the
                description for your change.
                </p>
              </li>
              <li>
                <p>Push the change for code review</p>
                <gr-shell-command> </gr-shell-command>
              </li>
              <li>
                <p>
                  Close this dialog and you should be able to see your recently
                created change in the 'Outgoing changes' section on the 'Your
                changes' page.
                </p>
              </li>
            </ol>
          </div>
        </gr-dialog>
      </dialog>
    `
    );
  });
});
