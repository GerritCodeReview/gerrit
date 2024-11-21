/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-preferences';
import {
  queryAll,
  queryAndAssert,
  stubFlags,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {GrPreferences} from './gr-preferences';
import {PreferencesInfo, TopMenuItemInfo} from '../../../types/common';
import {
  AppTheme,
  DateFormat,
  DefaultBase,
  DiffViewMode,
  EmailFormat,
  EmailStrategy,
  TimeFormat,
  createDefaultPreferences,
} from '../../../constants/constants';
import {fixture, html, assert} from '@open-wc/testing';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {
  createAccountDetailWithId,
  createPreferences,
} from '../../../test/test-data-generators';

suite('gr-preferences tests', () => {
  let element: GrPreferences;
  let preferences: PreferencesInfo;

  function valueOf(title: string, id: string): Element {
    const sections = queryAll(element, `#${id} section`) ?? [];
    let titleEl;
    for (let i = 0; i < sections.length; i++) {
      titleEl = sections[i].querySelector('.title');
      if (titleEl?.textContent?.trim() === title) {
        const el = sections[i].querySelector('.value');
        if (el) return el;
      }
    }
    assert.fail(`element with title ${title} not found`);
  }

  setup(async () => {
    preferences = {
      ...createPreferences(),
      changes_per_page: 25,
      theme: AppTheme.LIGHT,
      date_format: DateFormat.UK,
      time_format: TimeFormat.HHMM_12,
      diff_view: DiffViewMode.UNIFIED,
      email_strategy: EmailStrategy.ENABLED,
      email_format: EmailFormat.HTML_PLAINTEXT,
      default_base_for_merges: DefaultBase.FIRST_PARENT,
      relative_date_in_change_table: false,
      size_bar_in_change_table: true,
      my: [
        {url: '/first/url', name: 'first name', target: '_blank'},
        {url: '/second/url', name: 'second name', target: '_blank'},
      ] as TopMenuItemInfo[],
      change_table: [],
    };

    stubRestApi('getPreferences').returns(Promise.resolve(preferences));

    element = await fixture(html`<gr-preferences></gr-preferences>`);

    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <h2 id="Preferences">Preferences</h2>
        <fieldset id="preferences">
          <div class="gr-form-styles" id="preferences">
            <section>
              <label class="title" for="themeSelect"> Theme </label>
              <span class="value">
                <gr-select>
                  <select id="themeSelect">
                    <option value="AUTO">Auto (based on OS prefs)</option>
                    <option value="LIGHT">Light</option>
                    <option value="DARK">Dark</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="changesPerPageSelect">
                Changes per page
              </label>
              <span class="value">
                <gr-select>
                  <select id="changesPerPageSelect">
                    <option value="10">10 rows per page</option>
                    <option value="25">25 rows per page</option>
                    <option value="50">50 rows per page</option>
                    <option value="100">100 rows per page</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="dateTimeFormatSelect">
                Date/time format
              </label>
              <span class="value">
                <gr-select>
                  <select id="dateTimeFormatSelect">
                    <option value="STD">Jun 3 ; Jun 3, 2016</option>
                    <option value="US">06/03 ; 06/03/16</option>
                    <option value="ISO">06-03 ; 2016-06-03</option>
                    <option value="EURO">3. Jun ; 03.06.2016</option>
                    <option value="UK">03/06 ; 03/06/2016</option>
                  </select>
                </gr-select>
                <gr-select aria-label="Time Format">
                  <select id="timeFormatSelect">
                    <option value="HHMM_12">4:10 PM</option>
                    <option value="HHMM_24">16:10</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="emailNotificationsSelect">
                Email notifications
              </label>
              <span class="value">
                <gr-select>
                  <select id="emailNotificationsSelect">
                    <option value="CC_ON_OWN_COMMENTS">Every comment</option>
                    <option value="ENABLED">
                      Only comments left by others
                    </option>
                    <option value="ATTENTION_SET_ONLY">
                      Only when I am in the attention set
                    </option>
                    <option value="DISABLED">None</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="emailFormatSelect">
                Email format
              </label>
              <span class="value">
                <gr-select>
                  <select id="emailFormatSelect">
                    <option value="HTML_PLAINTEXT">HTML and plaintext</option>
                    <option value="PLAINTEXT">Plaintext only</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section id="allowBrowserNotificationsSection">
              <div class="title">
                <label for="allowBrowserNotifications">
                  Allow browser notifications
                </label>
                <a
                  href="/Documentation/user-attention-set.html#_browser_notifications"
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <gr-icon icon="help" title="read documentation"></gr-icon>
                </a>
              </div>
              <span class="value">
                <input
                  checked=""
                  id="allowBrowserNotifications"
                  type="checkbox"
                />
              </span>
            </section>
            <section>
              <label class="title" for="relativeDateInChangeTable">
                Show Relative Dates In Changes Table
              </label>
              <span class="value">
                <input id="relativeDateInChangeTable" type="checkbox" />
              </span>
            </section>
            <section>
              <span class="title"> Diff view </span>
              <span class="value">
                <gr-select>
                  <select id="diffViewSelect">
                    <option value="SIDE_BY_SIDE">Side by side</option>
                    <option value="UNIFIED_DIFF">Unified diff</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="showSizeBarsInFileList">
                Show size bars in file list
              </label>
              <span class="value">
                <input checked="" id="showSizeBarsInFileList" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="publishCommentsOnPush">
                Publish comments on push
              </label>
              <span class="value">
                <input id="publishCommentsOnPush" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="workInProgressByDefault">
                Set new changes to "work in progress" by default
              </label>
              <span class="value">
                <input id="workInProgressByDefault" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="disableKeyboardShortcuts">
                Disable all keyboard shortcuts
              </label>
              <span class="value">
                <input id="disableKeyboardShortcuts" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="disableTokenHighlighting">
                Disable token highlighting on hover
              </label>
              <span class="value">
                <input id="disableTokenHighlighting" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="insertSignedOff">
                Insert Signed-off-by Footer For Inline Edit Changes
              </label>
              <span class="value">
                <input id="insertSignedOff" type="checkbox" />
              </span>
            </section>
          </div>
          <gr-button
            aria-disabled="true"
            disabled=""
            id="savePrefs"
            role="button"
            tabindex="-1"
          >
            Save changes
          </gr-button>
        </fieldset>
      `
    );
  });

  test('allow browser notifications', async () => {
    stubFlags('isEnabled').returns(true);
    element.account = createAccountDetailWithId();
    await element.updateComplete;
    assert.dom.equal(
      queryAndAssert(element, '#allowBrowserNotificationsSection'),
      /* HTML */ `<section id="allowBrowserNotificationsSection">
        <div class="title">
          <label for="allowBrowserNotifications">
            Allow browser notifications
          </label>
          <a
            href="/Documentation/user-attention-set.html#_browser_notifications"
            target="_blank"
            rel="noopener noreferrer"
          >
            <gr-icon icon="help" title="read documentation"> </gr-icon>
          </a>
        </div>
        <span class="value">
          <input checked="" id="allowBrowserNotifications" type="checkbox" />
        </span>
      </section>`
    );
  });

  test('input values match preferences', () => {
    // Rendered with the expected preferences selected.
    assert.equal(
      Number(
        (
          valueOf('Changes per page', 'preferences')!
            .firstElementChild as GrSelect
        ).bindValue
      ),
      preferences.changes_per_page
    );
    assert.equal(
      (valueOf('Theme', 'preferences').firstElementChild as GrSelect).bindValue,
      preferences.theme
    );
    assert.equal(
      (
        valueOf('Date/time format', 'preferences')!
          .firstElementChild as GrSelect
      ).bindValue,
      preferences.date_format
    );
    assert.equal(
      (valueOf('Date/time format', 'preferences')!.lastElementChild as GrSelect)
        .bindValue,
      preferences.time_format
    );
    assert.equal(
      (
        valueOf('Email notifications', 'preferences')!
          .firstElementChild as GrSelect
      ).bindValue,
      preferences.email_strategy
    );
    assert.equal(
      (valueOf('Email format', 'preferences')!.firstElementChild as GrSelect)
        .bindValue,
      preferences.email_format
    );
    assert.equal(
      (
        valueOf('Show Relative Dates In Changes Table', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (valueOf('Diff view', 'preferences')!.firstElementChild as GrSelect)
        .bindValue,
      preferences.diff_view
    );
    assert.equal(
      (
        valueOf('Show size bars in file list', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      true
    );
    assert.equal(
      (
        valueOf('Publish comments on push', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (
        valueOf(
          'Set new changes to "work in progress" by default',
          'preferences'
        )!.firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (
        valueOf('Disable token highlighting on hover', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (
        valueOf(
          'Insert Signed-off-by Footer For Inline Edit Changes',
          'preferences'
        )!.firstElementChild as HTMLInputElement
      ).checked,
      false
    );

    assert.isFalse(element.hasUnsavedChanges());
  });

  test('save changes', async () => {
    assert.equal(element.prefs?.theme, AppTheme.LIGHT);

    const themeSelect = valueOf('Theme', 'preferences')
      .firstElementChild as GrSelect;
    themeSelect.bindValue = AppTheme.DARK;

    themeSelect.dispatchEvent(
      new CustomEvent('change', {
        composed: true,
        bubbles: true,
      })
    );

    const publishOnPush = valueOf('Publish comments on push', 'preferences')!
      .firstElementChild! as HTMLSpanElement;

    publishOnPush.click();

    assert.isTrue(element.hasUnsavedChanges());

    const savePrefStub = stubRestApi('savePreferences').resolves(
      element.prefs as PreferencesInfo
    );

    await element.save();

    // Wait for model state update, since this is not awaited by element.save()
    await waitUntil(
      () =>
        element.getUserModel().getState().preferences?.theme === AppTheme.DARK
    );
    await waitUntil(
      () => element.getUserModel().getState().preferences?.my === preferences.my
    );
    await waitUntil(
      () =>
        element.getUserModel().getState().preferences
          ?.publish_comments_on_push === true
    );

    assert.isTrue(savePrefStub.called);
    assert.isFalse(element.hasUnsavedChanges());
  });

  test('publish comments on push', async () => {
    assert.isFalse(element.hasUnsavedChanges());

    const publishCommentsOnPush = valueOf(
      'Publish comments on push',
      'preferences'
    )!.firstElementChild! as HTMLSpanElement;
    publishCommentsOnPush.click();

    assert.isTrue(element.hasUnsavedChanges());

    stubRestApi('savePreferences').callsFake(prefs => {
      assert.equal(prefs.publish_comments_on_push, true);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element.save();
    assert.isFalse(element.hasUnsavedChanges());
  });

  test('set new changes work-in-progress', async () => {
    assert.isFalse(element.hasUnsavedChanges());

    const newChangesWorkInProgress = valueOf(
      'Set new changes to "work in progress" by default',
      'preferences'
    )!.firstElementChild! as HTMLSpanElement;
    newChangesWorkInProgress.click();

    assert.isTrue(element.hasUnsavedChanges());

    stubRestApi('savePreferences').callsFake(prefs => {
      assert.equal(prefs.work_in_progress_by_default, true);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element.save();
    assert.isFalse(element.hasUnsavedChanges());
  });
});
