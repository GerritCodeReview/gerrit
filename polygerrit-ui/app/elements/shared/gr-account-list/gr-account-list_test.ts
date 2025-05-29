/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-account-list';
import {GrAccountList} from './gr-account-list';
import {
  AccountId,
  AccountInfo,
  EmailAddress,
  GroupBaseInfo,
  GroupId,
  GroupName,
  SuggestedReviewerInfo,
  Suggestion,
} from '../../../types/common';
import {
  pressKey,
  queryAll,
  queryAndAssert,
  waitUntil,
} from '../../../test/test-utils';
import {ReviewerSuggestionsProvider} from '../../../services/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../gr-autocomplete/gr-autocomplete';
import {GrAccountEntry} from '../gr-account-entry/gr-account-entry';
import {createChange} from '../../../test/test-data-generators';
import {ReviewerState} from '../../../api/rest-api';
import {assert, fixture, html} from '@open-wc/testing';
import {AccountInfoInput, RawAccountInput} from '../../../utils/account-util';

class MockSuggestionsProvider implements ReviewerSuggestionsProvider {
  init() {}

  getSuggestions(_: string): Promise<Suggestion[]> {
    return Promise.resolve([]);
  }

  makeSuggestionItem(
    _: Suggestion
  ): AutocompleteSuggestion<SuggestedReviewerInfo> {
    return {
      name: 'test',
      value: {
        account: {
          _account_id: 1 as AccountId,
        } as AccountInfo,
        count: 1,
      },
    };
  }
}

suite('gr-account-list tests', () => {
  let _nextAccountId = 0;
  const makeAccount: () => AccountInfo = function () {
    const accountId = ++_nextAccountId;
    return {
      _account_id: accountId as AccountId,
    };
  };
  const makeGroup: () => GroupBaseInfo = function () {
    const groupId = `group${++_nextAccountId}`;
    return {
      id: groupId as GroupId,
      name: 'abcd' as GroupName,
    };
  };

  let existingAccount1: AccountInfo;
  let existingAccount2: AccountInfo;

  let element: GrAccountList;
  let suggestionsProvider: MockSuggestionsProvider;

  function getChips() {
    return queryAll(element, 'gr-account-chip');
  }

  function handleAdd(value: RawAccountInput) {
    element.handleAdd(
      new CustomEvent<{value: string}>('add', {
        detail: {value: value as unknown as string},
      })
    );
  }

  setup(async () => {
    existingAccount1 = makeAccount();
    existingAccount2 = makeAccount();

    element = await fixture(html`<gr-account-list></gr-account-list>`);
    element.accounts = [existingAccount1, existingAccount2];
    element.reviewerState = ReviewerState.REVIEWER;
    element.change = {...createChange()};
    element.change.reviewers[ReviewerState.REVIEWER] = [...element.accounts];
    suggestionsProvider = new MockSuggestionsProvider();
    element.suggestionsProvider = suggestionsProvider;
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */
      `<div class="list">
          <gr-account-chip removable="" tabindex="-1"> </gr-account-chip>
          <gr-account-chip removable="" tabindex="-1"> </gr-account-chip>
        </div>
        <gr-account-entry borderless="" id="entry"></gr-account-entry>
        <slot></slot>`
    );
  });

  test('account entry only appears when editable', async () => {
    element.readonly = false;
    await element.updateComplete;
    assert.isFalse(
      queryAndAssert<GrAccountEntry>(element, '#entry').hasAttribute('hidden')
    );
    element.readonly = true;
    await element.updateComplete;
    assert.isTrue(
      queryAndAssert<GrAccountEntry>(element, '#entry').hasAttribute('hidden')
    );
  });

  test('addition and removal of account/group chips', async () => {
    await element.updateComplete;
    sinon.stub(element, 'computeRemovable').returns(true);
    // Existing accounts are listed.
    let chips = getChips();
    assert.equal(chips.length, 2);
    assert.isFalse(chips[0].classList.contains('newlyAdded'));
    assert.isFalse(chips[1].classList.contains('newlyAdded'));

    // New accounts are added to end with newlyAdded class.
    const newAccount = makeAccount();
    handleAdd({account: newAccount, count: 1});
    await element.updateComplete;
    chips = getChips();
    assert.equal(chips.length, 3);
    assert.isFalse(chips[0].classList.contains('newlyAdded'));
    assert.isFalse(chips[1].classList.contains('newlyAdded'));
    assert.isTrue(chips[2].classList.contains('newlyAdded'));

    // Removed accounts are taken out of the list.
    element.dispatchEvent(
      new CustomEvent('remove-account', {
        detail: {account: existingAccount1},
        composed: true,
        bubbles: true,
      })
    );
    await element.updateComplete;
    chips = getChips();
    assert.equal(chips.length, 2);
    assert.isFalse(chips[0].classList.contains('newlyAdded'));
    assert.isTrue(chips[1].classList.contains('newlyAdded'));

    // Invalid remove is ignored.
    element.dispatchEvent(
      new CustomEvent('remove-account', {
        detail: {account: existingAccount1},
        composed: true,
        bubbles: true,
      })
    );
    element.dispatchEvent(
      new CustomEvent('remove-account', {
        detail: {account: newAccount},
        composed: true,
        bubbles: true,
      })
    );
    await element.updateComplete;
    chips = getChips();
    assert.equal(chips.length, 1);
    assert.isFalse(chips[0].classList.contains('newlyAdded'));

    // New groups are added to end with newlyAdded and group classes.
    const newGroup = makeGroup();
    handleAdd({group: newGroup, confirm: false, count: 1});
    await element.updateComplete;
    chips = getChips();
    assert.equal(chips.length, 2);
    assert.isTrue(chips[1].classList.contains('group'));
    assert.isTrue(chips[1].classList.contains('newlyAdded'));

    // Removed groups are taken out of the list.
    element.dispatchEvent(
      new CustomEvent('remove-account', {
        detail: {account: newGroup},
        composed: true,
        bubbles: true,
      })
    );
    await element.updateComplete;
    chips = getChips();
    assert.equal(chips.length, 1);
    assert.isFalse(chips[0].classList.contains('newlyAdded'));
  });

  test('getSuggestions uses filter correctly', () => {
    const originalSuggestions: Suggestion[] = [
      {
        email: 'abc@example.com' as EmailAddress,
        text: 'abcd',
        _account_id: 3 as AccountId,
      } as AccountInfo,
      {
        email: 'qwe@example.com' as EmailAddress,
        text: 'qwer',
        _account_id: 1 as AccountId,
      } as AccountInfo,
      {
        email: 'xyz@example.com' as EmailAddress,
        text: 'aaaaa',
        _account_id: 25 as AccountId,
      } as AccountInfo,
    ];
    sinon
      .stub(suggestionsProvider, 'getSuggestions')
      .returns(Promise.resolve(originalSuggestions));
    sinon
      .stub(suggestionsProvider, 'makeSuggestionItem')
      .callsFake(suggestion => {
        return {
          name: ((suggestion as AccountInfo).email as string) ?? '',
          value: {
            account: suggestion as AccountInfo,
            count: 1,
          },
        };
      });

    return element
      .getSuggestions('')
      .then(suggestions => {
        // Default is no filtering.
        assert.equal(suggestions.length, 3);

        // Set up filter that only accepts suggestion1.
        const accountId = (originalSuggestions[0] as AccountInfo)._account_id;
        element.filter = function (suggestion) {
          return (suggestion as AccountInfo)._account_id === accountId;
        };

        return element.getSuggestions('');
      })
      .then(suggestions => {
        assert.deepEqual(suggestions, [
          {
            name: (originalSuggestions[0] as AccountInfo).email as string,
            value: {
              account: originalSuggestions[0] as AccountInfo,
              count: 1,
            },
          },
        ]);
      });
  });

  test('computeRemovable', async () => {
    const newAccount = makeAccount() as AccountInfoInput;
    element.readonly = false;
    element.removableValues = [];
    element.updateComplete;
    assert.isFalse(element.computeRemovable(existingAccount1));
    assert.isTrue(element.computeRemovable(newAccount));

    element.removableValues = [existingAccount1];
    element.updateComplete;
    assert.isTrue(element.computeRemovable(existingAccount1));
    assert.isTrue(element.computeRemovable(newAccount));
    assert.isFalse(element.computeRemovable(existingAccount2));

    element.readonly = true;
    element.updateComplete;
    assert.isFalse(element.computeRemovable(existingAccount1));
    assert.isFalse(element.computeRemovable(newAccount));
  });

  test('addAccountItem with invalid item', () => {
    const toastHandler = sinon.stub();
    element.allowAnyInput = false;
    element.addEventListener('show-alert', toastHandler);
    const result = element.addAccountItem('test');
    assert.isFalse(result);
    assert.isTrue(toastHandler.called);
  });

  test('submitEntryText', async () => {
    element.allowAnyInput = true;
    await element.updateComplete;

    const getTextStub = sinon.stub(
      queryAndAssert<GrAccountEntry>(element, '#entry'),
      'getText'
    );
    getTextStub.onFirstCall().returns('');
    getTextStub.onSecondCall().returns('test');
    getTextStub.onThirdCall().returns('test@test');

    // When entry is empty, return true.
    const clearStub = sinon.stub(
      queryAndAssert<GrAccountEntry>(element, '#entry'),
      'clear'
    );
    assert.isTrue(element.submitEntryText());
    assert.isFalse(clearStub.called);

    // When entry is invalid, return false.
    assert.isFalse(element.submitEntryText());
    assert.isFalse(clearStub.called);

    // When entry is valid, return true and clear text.
    assert.isTrue(element.submitEntryText());
    assert.isTrue(clearStub.called);
    assert.equal(
      (element.additions()[0] as AccountInfo)?.email,
      'test@test' as EmailAddress
    );
  });

  test('additions returns sanitized new accounts and groups', () => {
    assert.equal(element.additions().length, 0);

    const newAccount = makeAccount();
    handleAdd({account: newAccount, count: 1});
    const newGroup = makeGroup();
    handleAdd({group: newGroup, confirm: false, count: 1});

    assert.deepEqual(element.additions(), [
      {
        _account_id: newAccount._account_id,
      },
      {
        id: newGroup.id,
        name: 'abcd' as GroupName,
      },
    ]);
  });

  test('large group confirmations', () => {
    assert.isNull(element.pendingConfirmation);
    assert.deepEqual(element.additions(), []);

    const group = makeGroup();
    const reviewer: RawAccountInput = {
      group,
      count: 10,
      confirm: true,
    };
    handleAdd(reviewer);

    assert.deepEqual(element.pendingConfirmation, reviewer);
    assert.deepEqual(element.additions(), []);

    element.confirmGroup(group);
    assert.isNull(element.pendingConfirmation);
    assert.deepEqual(element.additions(), [
      {
        id: group.id,
        name: 'abcd' as GroupName,
        confirmed: true,
      },
    ]);
  });

  test('removeAccount fails if account is not removable', () => {
    element.readonly = true;
    const acct = makeAccount();
    element.accounts = [acct];
    element.removeAccount(acct);
    assert.equal(element.accounts.length, 1);
  });

  test('enter text calls suggestions provider', async () => {
    const suggestions: Suggestion[] = [
      {
        email: 'abc@example.com' as EmailAddress,
        text: 'abcd',
      } as AccountInfo,
      {
        email: 'qwe@example.com' as EmailAddress,
        text: 'qwer',
      } as AccountInfo,
    ];
    const getSuggestionsStub = sinon
      .stub(suggestionsProvider, 'getSuggestions')
      .returns(Promise.resolve(suggestions));

    const makeSuggestionItemSpy = sinon.spy(
      suggestionsProvider,
      'makeSuggestionItem'
    );

    const input = queryAndAssert<GrAutocomplete>(
      queryAndAssert<GrAccountEntry>(element, '#entry'),
      '#input'
    );
    input.text = 'newTest';
    input.input!.focus();
    await element.updateComplete;
    await input.latestSuggestionUpdateComplete;
    assert.isTrue(getSuggestionsStub.calledOnce);
    assert.equal(getSuggestionsStub.lastCall.args[0], 'newTest');
    await waitUntil(() => makeSuggestionItemSpy.getCalls().length === 2);
  });

  suite('allowAnyInput', () => {
    setup(() => {
      element.allowAnyInput = true;
    });

    test('adds emails', () => {
      const accountLen = element.accounts.length;
      handleAdd('test@test');
      assert.equal(element.accounts.length, accountLen + 1);
      assert.equal(
        (element.accounts[accountLen] as AccountInfoInput).email,
        'test@test' as EmailAddress
      );
    });

    test('toasts on invalid email', () => {
      const toastHandler = sinon.stub();
      element.addEventListener('show-alert', toastHandler);
      handleAdd('test');
      assert.isTrue(toastHandler.called);
    });
  });

  suite('keyboard interactions', () => {
    test('backspace at text input start removes last account', async () => {
      const input = queryAndAssert<GrAutocomplete>(
        queryAndAssert<GrAccountEntry>(element, '#entry'),
        '#input'
      );
      sinon.stub(input, 'updateSuggestions');
      sinon.stub(element, 'computeRemovable').returns(true);
      await element.updateComplete;
      // Next line is a workaround for Firefox not moving cursor
      // on input field update
      assert.equal(element.getOwnNativeInput(input.input!).selectionStart, 0);
      input.text = 'test';
      input.input!.focus();
      await element.updateComplete;
      assert.equal(element.accounts.length, 2);
      pressKey(element.getOwnNativeInput(input.input!), 'Backspace');
      await waitUntil(() => element.accounts.length === 2);
      input.text = '';
      await input.updateComplete;
      pressKey(element.getOwnNativeInput(input.input!), 'Backspace');
      await waitUntil(() => element.accounts.length === 1);
    });

    test('arrow key navigation', async () => {
      const input = queryAndAssert<GrAutocomplete>(
        queryAndAssert<GrAccountEntry>(element, '#entry'),
        '#input'
      );
      input.text = '';
      element.accounts = [makeAccount(), makeAccount()];
      await element.updateComplete;
      input.input!.focus();
      await element.updateComplete;
      const chips = element.accountChips;
      const chipsOneSpy = sinon.spy(chips[1], 'focus');
      pressKey(input.input!, 'ArrowLeft');
      assert.isTrue(chipsOneSpy.called);
      const chipsZeroSpy = sinon.spy(chips[0], 'focus');
      pressKey(chips[1], 'ArrowLeft');
      assert.isTrue(chipsZeroSpy.called);
      pressKey(chips[0], 'ArrowLeft');
      assert.isTrue(chipsZeroSpy.calledOnce);
      pressKey(chips[0], 'ArrowRight');
      assert.isTrue(chipsOneSpy.calledTwice);
    });

    test('delete', async () => {
      element.accounts = [makeAccount(), makeAccount()];
      await element.updateComplete;
      const focusSpy = sinon.spy(element.accountChips[1], 'focus');
      const removeSpy = sinon.spy(element, 'removeAccount');
      pressKey(element.accountChips[0], 'Backspace');
      assert.isTrue(focusSpy.called);
      assert.isTrue(removeSpy.calledOnce);

      pressKey(element.accountChips[0], 'Delete');
      assert.isTrue(removeSpy.calledTwice);
    });
  });
});
