/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-account-list.js';

const basicFixture = fixtureFromElement('gr-account-list');

class MockSuggestionsProvider {
  getSuggestions(input) {
    return Promise.resolve([]);
  }

  makeSuggestionItem(item) {
    return item;
  }
}

suite('gr-account-list tests', () => {
  let _nextAccountId = 0;
  const makeAccount = function() {
    const accountId = ++_nextAccountId;
    return {
      _account_id: accountId,
    };
  };
  const makeGroup = function() {
    const groupId = 'group' + (++_nextAccountId);
    return {
      id: groupId,
      _group: true,
    };
  };

  let existingAccount1;
  let existingAccount2;

  let element;
  let suggestionsProvider;

  function getChips() {
    return element.root.querySelectorAll('gr-account-chip');
  }

  setup(() => {
    existingAccount1 = makeAccount();
    existingAccount2 = makeAccount();

    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
    });
    element = basicFixture.instantiate();
    element.accounts = [existingAccount1, existingAccount2];
    suggestionsProvider = new MockSuggestionsProvider();
    element.suggestionsProvider = suggestionsProvider;
  });

  test('account entry only appears when editable', () => {
    element.readonly = false;
    assert.isFalse(element.$.entry.hasAttribute('hidden'));
    element.readonly = true;
    assert.isTrue(element.$.entry.hasAttribute('hidden'));
  });

  test('addition and removal of account/group chips', () => {
    flush();
    sinon.stub(element, '_computeRemovable').returns(true);
    // Existing accounts are listed.
    let chips = getChips();
    assert.equal(chips.length, 2);
    assert.isFalse(chips[0].classList.contains('pendingAdd'));
    assert.isFalse(chips[1].classList.contains('pendingAdd'));

    // New accounts are added to end with pendingAdd class.
    const newAccount = makeAccount();
    element._handleAdd({
      detail: {
        value: {
          account: newAccount,
        },
      },
    });
    flush();
    chips = getChips();
    assert.equal(chips.length, 3);
    assert.isFalse(chips[0].classList.contains('pendingAdd'));
    assert.isFalse(chips[1].classList.contains('pendingAdd'));
    assert.isTrue(chips[2].classList.contains('pendingAdd'));

    // Removed accounts are taken out of the list.
    element.dispatchEvent(
        new CustomEvent('remove', {
          detail: {account: existingAccount1},
          composed: true, bubbles: true,
        }));
    flush();
    chips = getChips();
    assert.equal(chips.length, 2);
    assert.isFalse(chips[0].classList.contains('pendingAdd'));
    assert.isTrue(chips[1].classList.contains('pendingAdd'));

    // Invalid remove is ignored.
    element.dispatchEvent(
        new CustomEvent('remove', {
          detail: {account: existingAccount1},
          composed: true, bubbles: true,
        }));
    element.dispatchEvent(
        new CustomEvent('remove', {
          detail: {account: newAccount},
          composed: true, bubbles: true,
        }));
    flush();
    chips = getChips();
    assert.equal(chips.length, 1);
    assert.isFalse(chips[0].classList.contains('pendingAdd'));

    // New groups are added to end with pendingAdd and group classes.
    const newGroup = makeGroup();
    element._handleAdd({
      detail: {
        value: {
          group: newGroup,
        },
      },
    });
    flush();
    chips = getChips();
    assert.equal(chips.length, 2);
    assert.isTrue(chips[1].classList.contains('group'));
    assert.isTrue(chips[1].classList.contains('pendingAdd'));

    // Removed groups are taken out of the list.
    element.dispatchEvent(
        new CustomEvent('remove', {
          detail: {account: newGroup},
          composed: true, bubbles: true,
        }));
    flush();
    chips = getChips();
    assert.equal(chips.length, 1);
    assert.isFalse(chips[0].classList.contains('pendingAdd'));
  });

  test('_getSuggestions uses filter correctly', done => {
    const originalSuggestions = [
      {
        email: 'abc@example.com',
        text: 'abcd',
        _account_id: 3,
      },
      {
        email: 'qwe@example.com',
        text: 'qwer',
        _account_id: 1,
      },
      {
        email: 'xyz@example.com',
        text: 'aaaaa',
        _account_id: 25,
      },
    ];
    sinon.stub(suggestionsProvider, 'getSuggestions')
        .returns(Promise.resolve(originalSuggestions));
    sinon.stub(suggestionsProvider, 'makeSuggestionItem')
        .callsFake( suggestion => {
          return {
            name: suggestion.email,
            value: suggestion._account_id,
          };
        });

    element._getSuggestions().then(suggestions => {
      // Default is no filtering.
      assert.equal(suggestions.length, 3);

      // Set up filter that only accepts suggestion1.
      const accountId = originalSuggestions[0]._account_id;
      element.filter = function(suggestion) {
        return suggestion._account_id === accountId;
      };

      element._getSuggestions()
          .then(suggestions => {
            assert.deepEqual(suggestions,
                [{name: originalSuggestions[0].email,
                  value: originalSuggestions[0]._account_id}]);
          })
          .then(done);
    });
  });

  test('_computeChipClass', () => {
    const account = makeAccount();
    assert.equal(element._computeChipClass(account), '');
    account._pendingAdd = true;
    assert.equal(element._computeChipClass(account), 'pendingAdd');
    account._group = true;
    assert.equal(element._computeChipClass(account), 'group pendingAdd');
    account._pendingAdd = false;
    assert.equal(element._computeChipClass(account), 'group');
  });

  test('_computeRemovable', () => {
    const newAccount = makeAccount();
    newAccount._pendingAdd = true;
    element.readonly = false;
    element.removableValues = [];
    assert.isFalse(element._computeRemovable(existingAccount1, false));
    assert.isTrue(element._computeRemovable(newAccount, false));

    element.removableValues = [existingAccount1];
    assert.isTrue(element._computeRemovable(existingAccount1, false));
    assert.isTrue(element._computeRemovable(newAccount, false));
    assert.isFalse(element._computeRemovable(existingAccount2, false));

    element.readonly = true;
    assert.isFalse(element._computeRemovable(existingAccount1, true));
    assert.isFalse(element._computeRemovable(newAccount, true));
  });

  test('submitEntryText', () => {
    element.allowAnyInput = true;
    flush();

    const getTextStub = sinon.stub(element.$.entry, 'getText');
    getTextStub.onFirstCall().returns('');
    getTextStub.onSecondCall().returns('test');
    getTextStub.onThirdCall().returns('test@test');

    // When entry is empty, return true.
    const clearStub = sinon.stub(element.$.entry, 'clear');
    assert.isTrue(element.submitEntryText());
    assert.isFalse(clearStub.called);

    // When entry is invalid, return false.
    assert.isFalse(element.submitEntryText());
    assert.isFalse(clearStub.called);

    // When entry is valid, return true and clear text.
    assert.isTrue(element.submitEntryText());
    assert.isTrue(clearStub.called);
    assert.equal(element.additions()[0].account.email, 'test@test');
  });

  test('additions returns sanitized new accounts and groups', () => {
    assert.equal(element.additions().length, 0);

    const newAccount = makeAccount();
    element._handleAdd({
      detail: {
        value: {
          account: newAccount,
        },
      },
    });
    const newGroup = makeGroup();
    element._handleAdd({
      detail: {
        value: {
          group: newGroup,
        },
      },
    });

    assert.deepEqual(element.additions(), [
      {
        account: {
          _account_id: newAccount._account_id,
          _pendingAdd: true,
        },
      },
      {
        group: {
          id: newGroup.id,
          _group: true,
          _pendingAdd: true,
        },
      },
    ]);
  });

  test('large group confirmations', () => {
    assert.isNull(element.pendingConfirmation);
    assert.deepEqual(element.additions(), []);

    const group = makeGroup();
    const reviewer = {
      group,
      count: 10,
      confirm: true,
    };
    element._handleAdd({
      detail: {
        value: reviewer,
      },
    });

    assert.deepEqual(element.pendingConfirmation, reviewer);
    assert.deepEqual(element.additions(), []);

    element.confirmGroup(group);
    assert.isNull(element.pendingConfirmation);
    assert.deepEqual(element.additions(), [
      {
        group: {
          id: group.id,
          _group: true,
          _pendingAdd: true,
          confirmed: true,
        },
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

  test('max-count', () => {
    element.maxCount = 1;
    const acct = makeAccount();
    element._handleAdd({
      detail: {
        value: {
          account: acct,
        },
      },
    });
    flush();
    assert.isTrue(element.$.entry.hasAttribute('hidden'));
  });

  test('enter text calls suggestions provider', async () => {
    const suggestions = [
      {
        email: 'abc@example.com',
        text: 'abcd',
      },
      {
        email: 'qwe@example.com',
        text: 'qwer',
      },
    ];
    const getSuggestionsStub =
        sinon.stub(suggestionsProvider, 'getSuggestions')
            .returns(Promise.resolve(suggestions));

    const makeSuggestionItemStub =
        sinon.stub(suggestionsProvider, 'makeSuggestionItem')
            .callsFake( item => item);

    const input = element.$.entry.$.input;

    input.text = 'newTest';
    MockInteractions.focus(input.$.input);
    input.noDebounce = true;
    await flush();
    assert.isTrue(getSuggestionsStub.calledOnce);
    assert.equal(getSuggestionsStub.lastCall.args[0], 'newTest');
    assert.equal(makeSuggestionItemStub.getCalls().length, 2);
  });

  test('suggestion on empty', async () => {
    element.skipSuggestOnEmpty = false;
    const suggestions = [
      {
        email: 'abc@example.com',
        text: 'abcd',
      },
      {
        email: 'qwe@example.com',
        text: 'qwer',
      },
    ];
    const getSuggestionsStub =
        sinon.stub(suggestionsProvider, 'getSuggestions')
            .returns(Promise.resolve(suggestions));

    const makeSuggestionItemStub =
        sinon.stub(suggestionsProvider, 'makeSuggestionItem')
            .callsFake( item => item);

    const input = element.$.entry.$.input;

    input.text = '';
    MockInteractions.focus(input.$.input);
    input.noDebounce = true;
    await flush();
    assert.isTrue(getSuggestionsStub.calledOnce);
    assert.equal(getSuggestionsStub.lastCall.args[0], '');
    assert.equal(makeSuggestionItemStub.getCalls().length, 2);
  });

  test('skip suggestion on empty', async () => {
    element.skipSuggestOnEmpty = true;
    const getSuggestionsStub =
        sinon.stub(suggestionsProvider, 'getSuggestions')
            .returns(Promise.resolve([]));

    const input = element.$.entry.$.input;

    input.text = '';
    MockInteractions.focus(input.$.input);
    input.noDebounce = true;
    await flush();
    assert.isTrue(getSuggestionsStub.notCalled);
  });

  suite('allowAnyInput', () => {
    setup(() => {
      element.allowAnyInput = true;
    });

    test('adds emails', () => {
      const accountLen = element.accounts.length;
      element._handleAdd({detail: {value: 'test@test'}});
      assert.equal(element.accounts.length, accountLen + 1);
      assert.equal(element.accounts[accountLen].email, 'test@test');
    });

    test('toasts on invalid email', () => {
      const toastHandler = sinon.stub();
      element.addEventListener('show-alert', toastHandler);
      element._handleAdd({detail: {value: 'test'}});
      assert.isTrue(toastHandler.called);
    });
  });

  test('_accountMatches', () => {
    const acct = makeAccount();

    assert.isTrue(element._accountMatches(acct, acct));
    acct.email = 'test';
    assert.isTrue(element._accountMatches(acct, acct));
    assert.isTrue(element._accountMatches({email: 'test'}, acct));

    assert.isFalse(element._accountMatches({}, acct));
    assert.isFalse(element._accountMatches({email: 'test2'}, acct));
    assert.isFalse(element._accountMatches({_account_id: -1}, acct));
  });

  suite('keyboard interactions', () => {
    test('backspace at text input start removes last account', async () => {
      const input = element.$.entry.$.input;
      sinon.stub(input, '_updateSuggestions');
      sinon.stub(element, '_computeRemovable').returns(true);
      await flush();
      // Next line is a workaround for Firefox not moving cursor
      // on input field update
      assert.equal(
          element._getNativeInput(input.$.input).selectionStart, 0);
      input.text = 'test';
      MockInteractions.focus(input.$.input);
      flush();
      assert.equal(element.accounts.length, 2);
      MockInteractions.pressAndReleaseKeyOn(
          element._getNativeInput(input.$.input), 8); // Backspace
      assert.equal(element.accounts.length, 2);
      input.text = '';
      MockInteractions.pressAndReleaseKeyOn(
          element._getNativeInput(input.$.input), 8); // Backspace
      flush();
      assert.equal(element.accounts.length, 1);
    });

    test('arrow key navigation', async () => {
      const input = element.$.entry.$.input;
      input.text = '';
      element.accounts = [makeAccount(), makeAccount()];
      flush();
      MockInteractions.focus(input.$.input);
      await flush();
      const chips = element.accountChips;
      const chipsOneSpy = sinon.spy(chips[1], 'focus');
      MockInteractions.pressAndReleaseKeyOn(input.$.input, 37); // Left
      assert.isTrue(chipsOneSpy.called);
      const chipsZeroSpy = sinon.spy(chips[0], 'focus');
      MockInteractions.pressAndReleaseKeyOn(chips[1], 37); // Left
      assert.isTrue(chipsZeroSpy.called);
      MockInteractions.pressAndReleaseKeyOn(chips[0], 37); // Left
      assert.isTrue(chipsZeroSpy.calledOnce);
      MockInteractions.pressAndReleaseKeyOn(chips[0], 39); // Right
      assert.isTrue(chipsOneSpy.calledTwice);
    });

    test('delete', () => {
      element.accounts = [makeAccount(), makeAccount()];
      flush();
      const focusSpy = sinon.spy(element.accountChips[1], 'focus');
      const removeSpy = sinon.spy(element, 'removeAccount');
      MockInteractions.pressAndReleaseKeyOn(
          element.accountChips[0], 8); // Backspace
      assert.isTrue(focusSpy.called);
      assert.isTrue(removeSpy.calledOnce);

      MockInteractions.pressAndReleaseKeyOn(
          element.accountChips[1], 46); // Delete
      assert.isTrue(removeSpy.calledTwice);
    });
  });
});

