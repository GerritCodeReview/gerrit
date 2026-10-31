/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {query, queryAndAssert} from '../../../utils/common-util';
import {assert} from '@open-wc/testing';
import {StylePluginApi} from '../../../api/styles';
import {
  getRecordedCSSRules,
  measureHeaderSnapshot,
  reconcilePluginStyleCache,
  resetRecordedCSSRules,
  updateThemeStyleCacheSnapshot,
} from './gr-plugin-style-api';
import {createServerInfo} from '../../../test/test-data-generators';
import {
  computeThemeStyleCacheKey,
  getThemeStyleCacheStorageKey,
} from '../../../utils/url-util';

suite('gr-plugin-style-api tests', () => {
  let styleApi: StylePluginApi;

  setup(() => {
    resetRecordedCSSRules();
    window.localStorage.removeItem(getThemeStyleCacheStorageKey());
    window.Gerrit.install(
      p => (styleApi = p.styleApi()),
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
  });

  teardown(() => {
    query<HTMLStyleElement>(document.head, 'style#plugin-style')?.remove();
    query<HTMLStyleElement>(
      document.head,
      'style#plugin-style-cache'
    )?.remove();
    document.body.querySelector('gr-app')?.remove();
    window.localStorage.removeItem(getThemeStyleCacheStorageKey());
    document.documentElement.classList.remove(
      'darkTheme',
      'lightTheme',
      'gr-custom-header-title',
      'gr-custom-mobile-header-title',
      'gr-header-shell-ready'
    );
    document.documentElement.style.removeProperty('--gr-cached-header-bg');
    document.documentElement.style.removeProperty('--gr-cached-header-height');
    document.documentElement.style.removeProperty(
      '--gr-cached-header-border-bottom'
    );
    document.documentElement.style.removeProperty(
      '--gr-cached-header-box-shadow'
    );
    resetRecordedCSSRules();
  });

  test('insertCSSRule adds a rule', async () => {
    styleApi.insertCSSRule('html{color:green;}');
    const styleEl = queryAndAssert<HTMLStyleElement>(
      document.head,
      'style#plugin-style'
    );
    const styleSheet = styleEl.sheet;
    assert.equal(styleSheet?.cssRules.length, 1);
    assert.deepEqual(getRecordedCSSRules(), ['html{color:green;}']);
  });

  test('insertCSSRule re-uses the <style> element and appends in order', async () => {
    styleApi.insertCSSRule('html{color:green;}');
    styleApi.insertCSSRule('html{margin:0px;}');
    const styleEl = queryAndAssert<HTMLStyleElement>(
      document.head,
      'style#plugin-style'
    );
    const styleSheet = styleEl.sheet;
    assert.equal(styleSheet?.cssRules.length, 2);
    assert.include(styleSheet?.cssRules[0].cssText, 'color: green');
    assert.include(styleSheet?.cssRules[1].cssText, 'margin: 0px');
  });

  test('reconcilePluginStyleCache cleans temporary head state and persists rules', async () => {
    const cacheStyle = document.createElement('style');
    cacheStyle.id = 'plugin-style-cache';
    document.head.appendChild(cacheStyle);
    document.documentElement.classList.add(
      'gr-custom-header-title',
      'gr-header-shell-ready'
    );
    document.documentElement.style.setProperty(
      '--gr-cached-header-bg',
      'rgb(59, 120, 231)'
    );
    document.documentElement.style.setProperty(
      '--gr-cached-header-height',
      '54px'
    );

    styleApi.insertCSSRule('html { --header-background-color: #3b78e7; }');
    const serverInfo = {
      ...createServerInfo(),
      plugin: {
        has_avatars: false,
        js_resource_paths: ['plugins/gerrithub/static/gerrithub.js'],
      },
    };

    reconcilePluginStyleCache(serverInfo, true, true);

    assert.isNull(document.head.querySelector('style#plugin-style-cache'));
    assert.isFalse(
      document.documentElement.classList.contains('gr-header-shell-ready')
    );
    assert.equal(
      document.documentElement.style.getPropertyValue('--gr-cached-header-bg'),
      ''
    );
    // Custom header title class stays until endpoint mounts or next animation frame
    assert.isTrue(
      document.documentElement.classList.contains('gr-custom-header-title')
    );
    await new Promise(resolve => requestAnimationFrame(resolve));
    assert.isFalse(
      document.documentElement.classList.contains('gr-custom-header-title')
    );

    const stored = JSON.parse(
      window.localStorage.getItem(getThemeStyleCacheStorageKey()) ?? '{}'
    );
    assert.equal(stored.key, computeThemeStyleCacheKey(serverInfo));
    assert.deepEqual(stored.rules, [
      'html { --header-background-color: #3b78e7; }',
    ]);
    assert.isTrue(stored.hasCustomHeaderTitle);
  });

  test('reconcilePluginStyleCache preserves existing cache when canPersist is false', () => {
    window.localStorage.setItem(
      getThemeStyleCacheStorageKey(),
      JSON.stringify({key: 'existing-key', rules: ['html { color: red; }']})
    );
    const cacheStyle = document.createElement('style');
    cacheStyle.id = 'plugin-style-cache';
    document.head.appendChild(cacheStyle);

    reconcilePluginStyleCache(createServerInfo(), false, false);

    assert.isNull(document.head.querySelector('style#plugin-style-cache'));
    const stored = JSON.parse(
      window.localStorage.getItem(getThemeStyleCacheStorageKey()) ?? '{}'
    );
    assert.equal(stored.key, 'existing-key');
  });

  test('measures and preserves per-mode lightTheme and darkTheme header snapshots', () => {
    const fakeApp = document.createElement('gr-app');
    const appShadow = fakeApp.attachShadow({mode: 'open'});
    const fakeAppElem = document.createElement('gr-app-element');
    const appElemShadow = fakeAppElem.attachShadow({mode: 'open'});
    const fakeHeader = document.createElement('gr-main-header');
    fakeHeader.style.display = 'block';
    fakeHeader.style.height = '54px';
    fakeHeader.style.backgroundColor = 'rgb(59, 120, 231)';
    const headerShadow = fakeHeader.attachShadow({mode: 'open'});
    const fakeNav = document.createElement('nav');
    fakeNav.className = 'hideOnMobile';
    fakeNav.style.borderBottom = '1px solid rgb(0, 0, 0)';
    headerShadow.appendChild(fakeNav);
    appElemShadow.appendChild(fakeHeader);
    appShadow.appendChild(fakeAppElem);
    document.body.appendChild(fakeApp);

    document.documentElement.classList.add('lightTheme');
    const lightSnap = measureHeaderSnapshot();
    assert.isDefined(lightSnap);
    assert.equal(lightSnap?.headerBg, 'rgb(59, 120, 231)');
    assert.equal(lightSnap?.headerHeight, '54px');

    const serverInfo = {
      ...createServerInfo(),
      default_theme: '/static/gerrit-theme.js',
    };
    reconcilePluginStyleCache(serverInfo, false, true);

    // Switch to dark mode and update header color
    document.documentElement.classList.remove('lightTheme');
    document.documentElement.classList.add('darkTheme');
    fakeHeader.style.backgroundColor = 'rgb(49, 73, 114)';
    updateThemeStyleCacheSnapshot();

    const stored = JSON.parse(
      window.localStorage.getItem(getThemeStyleCacheStorageKey()) ?? '{}'
    );
    assert.equal(stored.snapshots.lightTheme.headerBg, 'rgb(59, 120, 231)');
    assert.equal(stored.snapshots.darkTheme.headerBg, 'rgb(49, 73, 114)');
    fakeApp.remove();
  });
});
