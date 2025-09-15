/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-confirm-rebase-dialog';
import {GrConfirmRebaseDialog} from './gr-confirm-rebase-dialog';
import {query, queryAndAssert} from '../../../test/test-utils';
import {
  AccountId,
  BranchName,
  EmailAddress,
  EmailInfo,
  Timestamp,
} from '../../../types/common';
import {
  createAccountWithEmail,
  createChangeViewChange,
} from '../../../test/test-data-generators';
import {assert, fixture, html} from '@open-wc/testing';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken} from '../../../models/user/user-model';
import {changeModelToken} from '../../../models/change/change-model';
import {GrChangeAutocomplete} from '../gr-change-autocomplete/gr-change-autocomplete';
import {GrAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';
import {LoadingStatus} from '../../../types/types';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {
  DropdownItem,
  GrDropdownList,
} from '../../shared/gr-dropdown-list/gr-dropdown-list';

suite('gr-confirm-rebase-dialog tests', () => {
  let element: GrConfirmRebaseDialog;

  setup(async () => {
    const userModel = testResolver(userModelToken);
    userModel.setAccount({
      ...createAccountWithEmail('abc@def.com'),
      registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
    });
    element = await fixture(
      html`<gr-confirm-rebase-dialog></gr-confirm-rebase-dialog>`
    );
  });

  test('render', async () => {
    element.branch = 'test' as BranchName;
    element.hasParent = false;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `<gr-dialog
        confirm-label="Rebase"
        id="confirmDialog"
        role="dialog"
      >
        <div class="header" slot="header">Confirm rebase</div>
        <div class="main" slot="main">
          <div class="rebaseOption" hidden="" id="rebaseOnParent">
            <md-radio
              id="rebaseOnParentInput"
              name="rebaseOptions"
              tabindex="0"
              touch-target="wrapper"
            >
            </md-radio>
            <label for="rebaseOnParentInput" id="rebaseOnParentLabel">
              Rebase on parent change
            </label>
          </div>
          <div class="message" hidden="">
            Still loading parent information ...
          </div>
          <div class="message" hidden="" id="parentUpToDateMsg">
            This change is up to date with its parent.
          </div>
          <div class="rebaseOption" hidden="" id="rebaseOnTip">
            <md-radio
              disabled=""
              id="rebaseOnTipInput"
              name="rebaseOptions"
              tabindex="0"
              touch-target="wrapper"
            >
            </md-radio>
            <label for="rebaseOnTipInput" id="rebaseOnTipLabel">
              Rebase on top of the test branch
              <span hidden=""> (breaks relation chain) </span>
            </label>
          </div>
          <div class="message" id="tipUpToDateMsg">
            Change is up to date with the target branch already (test)
          </div>
          <div class="rebaseOption" id="rebaseOnOther">
            <md-radio
              id="rebaseOnOtherInput"
              name="rebaseOptions"
              tabindex="0"
              touch-target="wrapper"
            >
            </md-radio>
            <label for="rebaseOnOtherInput" id="rebaseOnOtherLabel">
              Rebase on a specific change, ref, or commit
              <span hidden=""> (breaks relation chain) </span>
            </label>
          </div>
          <div class="parentRevisionContainer">
            <gr-change-autocomplete> </gr-change-autocomplete>
          </div>
          <div class="rebaseCheckbox">
            <md-checkbox id="rebaseAllowConflicts" touch-target="wrapper">
            </md-checkbox>
            <label for="rebaseAllowConflicts">
              Allow rebase with conflicts
            </label>
            <gr-validation-options> </gr-validation-options>
          </div>
        </div>
      </gr-dialog> `
    );
  });

  suite('on behalf of uploader', () => {
    let changeModel;
    const change = {
      ...createChangeViewChange(),
    };
    setup(async () => {
      element.branch = 'test' as BranchName;
      await element.updateComplete;
      changeModel = testResolver(changeModelToken);
      changeModel.setState({
        loadingStatus: LoadingStatus.LOADED,
        change,
      });
    });
    test('for reviewer it shows message about on behalf', () => {
      const rebaseOnBehalfMsg = queryAndAssert(element, '.rebaseOnBehalfMsg');
      assert.dom.equal(
        rebaseOnBehalfMsg,
        /* HTML */ `<div class="rebaseOnBehalfMsg">
          Rebase will be done on behalf of the uploader:
          <gr-account-chip> </gr-account-chip> <span> </span>
        </div>`
      );
      const accountChip: GrAccountChip = queryAndAssert(
        rebaseOnBehalfMsg,
        'gr-account-chip'
      );
      assert.equal(
        accountChip.account!,
        change?.revisions[change.current_revision]?.uploader
      );
    });
    test('allowConflicts', async () => {
      element.allowConflicts = true;
      await element.updateComplete;
      const rebaseOnBehalfMsg = queryAndAssert(element, '.rebaseOnBehalfMsg');
      assert.dom.equal(
        rebaseOnBehalfMsg,
        /* HTML */ `<div class="rebaseOnBehalfMsg">
          Rebase will be done on behalf of
          <gr-account-chip> </gr-account-chip> <span> </span>
        </div>`
      );
      const accountChip: GrAccountChip = queryAndAssert(
        rebaseOnBehalfMsg,
        'gr-account-chip'
      );
      assert.equal(accountChip.account, element.account);
    });
  });

  suite('rebase with committer email', () => {
    setup(async () => {
      element.branch = 'test' as BranchName;
      await element.updateComplete;
    });

    test('hide rebaseWithCommitterEmail dialog when committer has single email', async () => {
      element.committerEmailDropdownItems = [
        {
          email: 'test1@example.com' as EmailAddress,
          preferred: true,
          pending_confirmation: true,
        },
      ];
      await element.updateComplete;
      assert.isNotOk(query(element, '.rebaseWithCommitterEmail'));
    });

    test('show rebaseWithCommitterEmail dialog when committer has more than one email', async () => {
      element.committerEmailDropdownItems = [
        {
          email: 'test1@example.com' as EmailAddress,
          preferred: true,
          pending_confirmation: true,
        },
        {
          email: 'test2@example.com' as EmailAddress,
          pending_confirmation: true,
        },
      ];
      await element.updateComplete;
      const committerEmail = queryAndAssert(
        element,
        '.rebaseWithCommitterEmail'
      );
      assert.dom.equal(
        committerEmail,
        /* HTML */ `<div class="rebaseWithCommitterEmail"
              >Rebase with committer email
              <gr-dropdown-list>
              </gr-dropdown-list>
              <span></div>`
      );
      const dropdownList: GrDropdownList = queryAndAssert(
        committerEmail,
        'gr-dropdown-list'
      );
      assert.strictEqual(dropdownList.items!.length, 2);
    });

    test('hide rebaseWithCommitterEmail dialog when RebaseChain is set', async () => {
      element.shouldRebaseChain = true;
      await element.updateComplete;
      assert.isNotOk(query(element, '.rebaseWithCommitterEmail'));
    });

    test('show current user emails in the dropdown list when rebase with conflicts is allowed', async () => {
      element.allowConflicts = true;
      element.latestCommitter = {
        email: 'commit@example.com' as EmailAddress,
        name: 'committer',
        date: '2023-06-12 18:32:08.000000000' as Timestamp,
      };
      element.committerEmailDropdownItems = [
        {
          email: 'currentuser1@example.com' as EmailAddress,
          preferred: true,
          pending_confirmation: true,
        },
        {
          email: 'currentuser2@example.com' as EmailAddress,
          pending_confirmation: true,
        },
      ];
      await element.updateComplete;
      const committerEmail = queryAndAssert(
        element,
        '.rebaseWithCommitterEmail'
      );
      const dropdownList: GrDropdownList = queryAndAssert(
        committerEmail,
        'gr-dropdown-list'
      );
      assert.deepStrictEqual(
        dropdownList.items!.map((e: DropdownItem) => e.value),
        element.committerEmailDropdownItems.map((e: EmailInfo) => e.email)
      );
    });

    test('show uploader emails in the dropdown list when rebase with conflicts is not allowed', async () => {
      element.allowConflicts = false;
      element.uploader = {_account_id: 2 as AccountId, name: '2'};
      element.latestCommitter = {
        email: 'commit@example.com' as EmailAddress,
        name: 'committer',
        date: '2023-06-12 18:32:08.000000000' as Timestamp,
      };
      element.committerEmailDropdownItems = [
        {
          email: 'uploader1@example.com' as EmailAddress,
          preferred: true,
          pending_confirmation: true,
        },
        {
          email: 'uploader2@example.com' as EmailAddress,
          preferred: false,
          pending_confirmation: true,
        },
      ];
      await element.updateComplete;
      const committerEmail = queryAndAssert(
        element,
        '.rebaseWithCommitterEmail'
      );
      const dropdownList: GrDropdownList = queryAndAssert(
        committerEmail,
        'gr-dropdown-list'
      );
      assert.deepStrictEqual(
        dropdownList.items!.map((e: DropdownItem) => e.value),
        element.committerEmailDropdownItems.map((e: EmailInfo) => e.email)
      );
    });
  });

  test('disableActions property disables dialog confirm', async () => {
    element.disableActions = false;
    await element.updateComplete;

    const dialog = queryAndAssert<GrDialog>(element, 'gr-dialog');
    assert.isFalse(dialog.disabled);

    element.disableActions = true;
    await element.updateComplete;

    assert.isTrue(dialog.disabled);
  });

  test('controls with parent and rebase on current available', async () => {
    element.rebaseOnCurrent = true;
    element.hasParent = true;
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert<HTMLInputElement>(element, '#rebaseOnParentInput').checked
    );
    assert.isFalse(
      queryAndAssert(element, '#rebaseOnParent').hasAttribute('hidden')
    );
    assert.isTrue(
      queryAndAssert(element, '#parentUpToDateMsg').hasAttribute('hidden')
    );
    assert.isFalse(
      queryAndAssert(element, '#rebaseOnTip').hasAttribute('hidden')
    );
    assert.isTrue(
      queryAndAssert(element, '#tipUpToDateMsg').hasAttribute('hidden')
    );
  });

  test('controls with parent rebase on current not available', async () => {
    element.rebaseOnCurrent = false;
    element.hasParent = true;
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert<HTMLInputElement>(element, '#rebaseOnTipInput').checked
    );
    assert.isTrue(
      queryAndAssert(element, '#rebaseOnParent').hasAttribute('hidden')
    );
    assert.isFalse(
      queryAndAssert(element, '#parentUpToDateMsg').hasAttribute('hidden')
    );
    assert.isFalse(
      queryAndAssert(element, '#rebaseOnTip').hasAttribute('hidden')
    );
    assert.isTrue(
      queryAndAssert(element, '#tipUpToDateMsg').hasAttribute('hidden')
    );
  });

  test('controls without parent and rebase on current available', async () => {
    element.rebaseOnCurrent = true;
    element.hasParent = false;
    await element.updateComplete;

    assert.isTrue(
      queryAndAssert<HTMLInputElement>(element, '#rebaseOnTipInput').checked
    );
    assert.isTrue(
      queryAndAssert(element, '#rebaseOnParent').hasAttribute('hidden')
    );
    assert.isTrue(
      queryAndAssert(element, '#parentUpToDateMsg').hasAttribute('hidden')
    );
    assert.isFalse(
      queryAndAssert(element, '#rebaseOnTip').hasAttribute('hidden')
    );
    assert.isTrue(
      queryAndAssert(element, '#tipUpToDateMsg').hasAttribute('hidden')
    );
  });

  test('controls without parent rebase on current not available', async () => {
    element.rebaseOnCurrent = false;
    element.hasParent = false;
    await element.updateComplete;

    assert.isTrue(element.rebaseOnOtherInput?.checked);
    assert.isTrue(
      queryAndAssert(element, '#rebaseOnParent').hasAttribute('hidden')
    );
    assert.isTrue(
      queryAndAssert(element, '#parentUpToDateMsg').hasAttribute('hidden')
    );
    assert.isTrue(
      queryAndAssert(element, '#rebaseOnTip').hasAttribute('hidden')
    );
    assert.isFalse(
      queryAndAssert(element, '#tipUpToDateMsg').hasAttribute('hidden')
    );
  });

  test('committer email is sent when chain is not rebased', async () => {
    const fireStub = sinon.stub(element, 'dispatchEvent');
    element.text = '123';
    element.selectedEmailForRebase = 'abc@def.com';
    await element.updateComplete;
    queryAndAssert(element, '#confirmDialog').dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: true,
      })
    );
    assert.deepEqual((fireStub.lastCall.args[0] as CustomEvent).detail, {
      allowConflicts: false,
      base: '123',
      rebaseChain: false,
      onBehalfOfUploader: true,
      committerEmail: 'abc@def.com',
    });
  });

  test('committer email is not sent when chain is rebased', async () => {
    const fireStub = sinon.stub(element, 'dispatchEvent');
    element.text = '123';
    element.selectedEmailForRebase = 'abc@def.com';
    element.hasParent = true;
    element.shouldRebaseChain = true;
    await element.updateComplete;
    queryAndAssert<HTMLInputElement>(element, '#rebaseChain').checked = true;
    queryAndAssert(element, '#confirmDialog').dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: true,
      })
    );
    assert.deepEqual((fireStub.lastCall.args[0] as CustomEvent).detail, {
      allowConflicts: false,
      base: '123',
      rebaseChain: true,
      onBehalfOfUploader: true,
      committerEmail: null,
    });
  });

  test('input cleared on cancel or submit', async () => {
    element.text = '123';
    await element.updateComplete;
    queryAndAssert(element, '#confirmDialog').dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: true,
      })
    );
    assert.equal(element.text, '');

    element.text = '123';
    await element.updateComplete;

    queryAndAssert(element, '#confirmDialog').dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: true,
      })
    );
    assert.equal(element.text, '');
  });

  test('getSelectedBase', async () => {
    const autocomplete = queryAndAssert<GrChangeAutocomplete>(
      element,
      'gr-change-autocomplete'
    );
    const innerAutocomplete = queryAndAssert<GrAutocomplete>(
      autocomplete,
      'gr-autocomplete'
    );

    innerAutocomplete.dispatchEvent(
      new CustomEvent('text-changed', {detail: {value: '5fab321c'}})
    );
    await innerAutocomplete.updateComplete;
    await element.updateComplete;

    queryAndAssert<HTMLInputElement>(element, '#rebaseOnParentInput').checked =
      true;
    assert.equal(element.getSelectedBase(), null);
    queryAndAssert<HTMLInputElement>(element, '#rebaseOnParentInput').checked =
      false;
    queryAndAssert<HTMLInputElement>(element, '#rebaseOnTipInput').checked =
      true;
    assert.equal(element.getSelectedBase(), '');
    queryAndAssert<HTMLInputElement>(element, '#rebaseOnTipInput').checked =
      false;
    assert.equal(element.getSelectedBase(), '5fab321c');
  });
});
