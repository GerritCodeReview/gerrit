/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-confirm-rebase-dialog';
import {GrConfirmRebaseDialog, RebaseChange} from './gr-confirm-rebase-dialog';
import {
  pressKey,
  query,
  queryAndAssert,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {
  NumericChangeId,
  BranchName,
  Timestamp,
  AccountId,
  EmailAddress,
} from '../../../types/common';
import {
  createAccountWithEmail,
  createChangeViewChange,
} from '../../../test/test-data-generators';
import {fixture, html, assert} from '@open-wc/testing';
import {Key} from '../../../utils/dom-util';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken} from '../../../models/user/user-model';
import {changeModelToken} from '../../../models/change/change-model';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {LoadingStatus} from '../../../types/types';
import {GrDropdownList} from '../../shared/gr-dropdown-list/gr-dropdown-list';

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
            <input id="rebaseOnParentInput" name="rebaseOptions" type="radio" />
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
            <input
              disabled=""
              id="rebaseOnTipInput"
              name="rebaseOptions"
              type="radio"
            />
            <label for="rebaseOnTipInput" id="rebaseOnTipLabel">
              Rebase on top of the test branch
              <span hidden=""> (breaks relation chain) </span>
            </label>
          </div>
          <div class="message" id="tipUpToDateMsg">
            Change is up to date with the target branch already (test)
          </div>
          <div class="rebaseOption" id="rebaseOnOther">
            <input id="rebaseOnOtherInput" name="rebaseOptions" type="radio" />
            <label for="rebaseOnOtherInput" id="rebaseOnOtherLabel">
              Rebase on a specific change, ref, or commit
              <span hidden=""> (breaks relation chain) </span>
            </label>
          </div>
          <div class="parentRevisionContainer">
            <gr-autocomplete
              allow-non-suggested-values=""
              id="parentInput"
              placeholder="Change number, ref, or commit hash"
            >
            </gr-autocomplete>
          </div>
          <div class="rebaseCheckbox">
            <input id="rebaseAllowConflicts" type="checkbox" />
            <label for="rebaseAllowConflicts">
              Allow rebase with conflicts
            </label>
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
        dropdownList.items!.map(e => e.value),
        element.committerEmailDropdownItems.map(e => e.email)
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
        dropdownList.items!.map(e => e.value),
        element.committerEmailDropdownItems.map(e => e.email)
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

  test('_getSelectedBase', async () => {
    element.text = '5fab321c';
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
    assert.equal(element.getSelectedBase(), element.text);
    element.text = '101: Test';
    await element.updateComplete;

    assert.equal(element.getSelectedBase(), '101');
  });

  suite('parent suggestions', () => {
    let recentChanges: RebaseChange[];
    let getChangesStub: sinon.SinonStub;
    setup(() => {
      recentChanges = [
        {
          name: '123: my first awesome change',
          value: 123 as NumericChangeId,
        },
        {
          name: '124: my second awesome change',
          value: 124 as NumericChangeId,
        },
        {
          name: '245: my third awesome change',
          value: 245 as NumericChangeId,
        },
      ];

      getChangesStub = stubRestApi('getChanges').returns(
        Promise.resolve([
          {
            ...createChangeViewChange(),
            _number: 123 as NumericChangeId,
            subject: 'my first awesome change',
          },
          {
            ...createChangeViewChange(),
            _number: 124 as NumericChangeId,
            subject: 'my second awesome change',
          },
          {
            ...createChangeViewChange(),
            _number: 245 as NumericChangeId,
            subject: 'my third awesome change',
          },
        ])
      );
    });

    test('_getRecentChanges', async () => {
      const recentChangesSpy = sinon.spy(element, 'getRecentChanges');
      await element.getRecentChanges();
      await element.updateComplete;

      assert.deepEqual(element.recentChanges, recentChanges);
      assert.equal(getChangesStub.callCount, 1);

      // When called a second time, should not re-request recent changes.
      await element.getRecentChanges();
      await element.updateComplete;

      assert.equal(recentChangesSpy.callCount, 2);
      assert.equal(getChangesStub.callCount, 1);
    });

    test('_filterChanges', async () => {
      assert.equal(element.filterChanges('123', recentChanges).length, 1);
      assert.equal(element.filterChanges('12', recentChanges).length, 2);
      assert.equal(element.filterChanges('awesome', recentChanges).length, 3);
      assert.equal(element.filterChanges('third', recentChanges).length, 1);

      element.changeNum = 123 as NumericChangeId;
      await element.updateComplete;

      assert.equal(element.filterChanges('123', recentChanges).length, 0);
      assert.equal(element.filterChanges('124', recentChanges).length, 1);
      assert.equal(element.filterChanges('awesome', recentChanges).length, 2);
    });

    test('input text change triggers function', async () => {
      const recentChangesSpy = sinon.spy(element, 'getRecentChanges');
      pressKey(
        queryAndAssert(queryAndAssert(element, '#parentInput'), '#input'),
        Key.ENTER
      );
      await element.updateComplete;
      element.text = '1';

      await waitUntil(() => recentChangesSpy.calledOnce);
      element.text = '12';

      await waitUntil(() => recentChangesSpy.calledTwice);
    });
  });
});
