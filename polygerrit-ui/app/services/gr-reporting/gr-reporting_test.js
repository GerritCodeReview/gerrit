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

import '../../test/common-test-setup-karma.js';
import {GrReporting, DEFAULT_STARTUP_TIMERS, initErrorReporter} from './gr-reporting_impl.js';
import {appContext} from '../app-context.js';
suite('gr-reporting tests', () => {
  let service;

  let clock;
  let fakePerformance;

  const NOW_TIME = 100;

  setup(() => {
    clock = sinon.useFakeTimers(NOW_TIME);
    service = new GrReporting(appContext.flagsService);
    service._baselines = {...DEFAULT_STARTUP_TIMERS};
    sinon.stub(service, 'reporter');
  });

  teardown(() => {
    clock.restore();
  });

  test('appStarted', () => {
    fakePerformance = {
      navigationStart: 1,
      loadEventEnd: 2,
    };
    fakePerformance.toJSON = () => fakePerformance;
    sinon.stub(service, 'performanceTiming').get(() => fakePerformance);
    sinon.stub(window.performance, 'now').returns(42);
    service.appStarted();
    assert.isTrue(
        service.reporter.calledWithMatch(
            'timing-report', 'UI Latency', 'App Started', 42
        ));
    assert.isTrue(
        service.reporter.calledWithExactly(
            'timing-report', 'UI Latency', 'NavResTime - loadEventEnd',
            fakePerformance.loadEventEnd - fakePerformance.navigationStart,
            undefined, true)
    );
  });

  test('WebComponentsReady', () => {
    sinon.stub(window.performance, 'now').returns(42);
    service.timeEnd('WebComponentsReady');
    assert.isTrue(service.reporter.calledWithMatch(
        'timing-report', 'UI Latency', 'WebComponentsReady', 42
    ));
  });

  test('beforeLocationChanged', () => {
    service._baselines['garbage'] = 'monster';
    sinon.stub(service, 'time');
    service.beforeLocationChanged();
    assert.isTrue(service.time.calledWithExactly('DashboardDisplayed'));
    assert.isTrue(service.time.calledWithExactly('ChangeDisplayed'));
    assert.isTrue(service.time.calledWithExactly('ChangeFullyLoaded'));
    assert.isTrue(service.time.calledWithExactly('DiffViewDisplayed'));
    assert.isTrue(service.time.calledWithExactly('FileListDisplayed'));
    assert.isFalse(service._baselines.hasOwnProperty('garbage'));
  });

  test('changeDisplayed', () => {
    sinon.spy(service, 'timeEnd');
    service.changeDisplayed();
    assert.isFalse(service.timeEnd.calledWith('ChangeDisplayed'));
    assert.isTrue(service.timeEnd.calledWith('StartupChangeDisplayed'));
    service.changeDisplayed();
    assert.isTrue(service.timeEnd.calledWith('ChangeDisplayed'));
  });

  test('changeFullyLoaded', () => {
    sinon.spy(service, 'timeEnd');
    service.changeFullyLoaded();
    assert.isFalse(
        service.timeEnd.calledWithExactly('ChangeFullyLoaded'));
    assert.isTrue(
        service.timeEnd.calledWithExactly('StartupChangeFullyLoaded'));
    service.changeFullyLoaded();
    assert.isTrue(service.timeEnd.calledWithExactly('ChangeFullyLoaded'));
  });

  test('diffViewDisplayed', () => {
    sinon.spy(service, 'timeEnd');
    service.diffViewDisplayed();
    assert.isFalse(service.timeEnd.calledWith('DiffViewDisplayed'));
    assert.isTrue(service.timeEnd.calledWith('StartupDiffViewDisplayed'));
    service.diffViewDisplayed();
    assert.isTrue(service.timeEnd.calledWith('DiffViewDisplayed'));
  });

  test('fileListDisplayed', () => {
    sinon.spy(service, 'timeEnd');
    service.fileListDisplayed();
    assert.isFalse(
        service.timeEnd.calledWithExactly('FileListDisplayed'));
    assert.isTrue(
        service.timeEnd.calledWithExactly('StartupFileListDisplayed'));
    service.fileListDisplayed();
    assert.isTrue(service.timeEnd.calledWithExactly('FileListDisplayed'));
  });

  test('dashboardDisplayed', () => {
    sinon.spy(service, 'timeEnd');
    service.dashboardDisplayed();
    assert.isFalse(service.timeEnd.calledWith('DashboardDisplayed'));
    assert.isTrue(service.timeEnd.calledWith('StartupDashboardDisplayed'));
    service.dashboardDisplayed();
    assert.isTrue(service.timeEnd.calledWith('DashboardDisplayed'));
  });

  test('dashboardDisplayed details', () => {
    sinon.spy(service, 'timeEnd');
    sinon.stub(window, 'performance').value( {
      memory: {
        usedJSHeapSize: 1024 * 1024,
      },
      measure: () => {},
      now: () => { 42; },
    });
    service.reportRpcTiming('/changes/*~*/comments', 500);
    service.dashboardDisplayed();
    assert.isTrue(
        service.timeEnd.calledWithExactly('StartupDashboardDisplayed',
            {rpcList: [
              {
                anonymizedUrl: '/changes/*~*/comments',
                elapsed: 500,
              },
            ],
            screenSize: {
              width: window.screen.width,
              height: window.screen.height,
            },
            viewport: {
              width: document.documentElement.clientWidth,
              height: document.documentElement.clientHeight,
            },
            usedJSHeapSizeMb: 1,
            hiddenDurationMs: 0,
            }
        ));
  });

  suite('hidden duration', () => {
    let nowStub;
    let visibilityStateStub;
    const assertHiddenDurationsMs = hiddenDurationMs => {
      service.dashboardDisplayed();
      assert.isTrue(
          service.timeEnd.calledWithMatch('StartupDashboardDisplayed',
              {hiddenDurationMs}
          ));
    };

    setup(() => {
      sinon.spy(service, 'timeEnd');
      nowStub = sinon.stub(window.performance, 'now');
      visibilityStateStub = {
        value: value => {
          Object.defineProperty(document, 'visibilityState',
              {value, configurable: true});
        },
      };
    });

    test('starts in hidden', () => {
      nowStub.returns(10);
      visibilityStateStub.value('hidden');
      service.onVisibilityChange();
      nowStub.returns(15);
      visibilityStateStub.value('visible');
      service.onVisibilityChange();
      assertHiddenDurationsMs(5);
    });

    test('full in hidden', () => {
      nowStub.returns(10);
      visibilityStateStub.value('hidden');
      assertHiddenDurationsMs(10);
    });

    test('full in visible', () => {
      nowStub.returns(10);
      visibilityStateStub.value('visible');
      assertHiddenDurationsMs(0);
    });

    test('accumulated', () => {
      nowStub.returns(10);
      visibilityStateStub.value('hidden');
      service.onVisibilityChange();
      nowStub.returns(15);
      visibilityStateStub.value('visible');
      service.onVisibilityChange();
      nowStub.returns(20);
      visibilityStateStub.value('hidden');
      service.onVisibilityChange();
      nowStub.returns(25);
      assertHiddenDurationsMs(10);
    });

    test('reset after location change', () => {
      nowStub.returns(10);
      visibilityStateStub.value('hidden');
      assertHiddenDurationsMs(10);
      visibilityStateStub.value('visible');
      nowStub.returns(15);
      service.beforeLocationChanged();
      service.timeEnd.resetHistory();
      service.dashboardDisplayed();
      assert.isTrue(
          service.timeEnd.calledWithMatch('DashboardDisplayed',
              {hiddenDurationMs: 0}
          ));
    });
  });

  test('time and timeEnd', () => {
    const nowStub = sinon.stub(window.performance, 'now').returns(0);
    service.time('foo');
    nowStub.returns(1);
    service.time('bar');
    nowStub.returns(2);
    service.timeEnd('bar');
    nowStub.returns(3);
    service.timeEnd('foo');
    assert.isTrue(service.reporter.calledWithMatch(
        'timing-report', 'UI Latency', 'foo', 3
    ));
    assert.isTrue(service.reporter.calledWithMatch(
        'timing-report', 'UI Latency', 'bar', 1
    ));
  });

  test('timer object', () => {
    const nowStub = sinon.stub(window.performance, 'now').returns(100);
    const timer = service.getTimer('foo-bar');
    nowStub.returns(150);
    timer.end();
    assert.isTrue(service.reporter.calledWithMatch(
        'timing-report', 'UI Latency', 'foo-bar', 50));
  });

  test('timer object double call', () => {
    const timer = service.getTimer('foo-bar');
    timer.end();
    assert.isTrue(service.reporter.calledOnce);
    assert.throws(() => {
      timer.end();
    }, 'Timer for "foo-bar" already ended.');
  });

  test('timer object maximum', () => {
    const nowStub = sinon.stub(window.performance, 'now').returns(100);
    const timer = service.getTimer('foo-bar').withMaximum(100);
    nowStub.returns(150);
    timer.end();
    assert.isTrue(service.reporter.calledOnce);

    timer.reset();
    nowStub.returns(260);
    timer.end();
    assert.isTrue(service.reporter.calledOnce);
  });

  test('recordDraftInteraction', () => {
    const key = 'TimeBetweenDraftActions';
    const nowStub = sinon.stub(window.performance, 'now').returns(100);
    const timingStub = sinon.stub(service, '_reportTiming');
    service.recordDraftInteraction();
    assert.isFalse(timingStub.called);

    nowStub.returns(200);
    service.recordDraftInteraction();
    assert.isTrue(timingStub.calledOnce);
    assert.equal(timingStub.lastCall.args[0], key);
    assert.equal(timingStub.lastCall.args[1], 100);

    nowStub.returns(350);
    service.recordDraftInteraction();
    assert.isTrue(timingStub.calledTwice);
    assert.equal(timingStub.lastCall.args[0], key);
    assert.equal(timingStub.lastCall.args[1], 150);

    nowStub.returns(370 + 2 * 60 * 1000);
    service.recordDraftInteraction();
    assert.isFalse(timingStub.calledThrice);
  });

  test('timeEndWithAverage', () => {
    const nowStub = sinon.stub(window.performance, 'now').returns(0);
    nowStub.returns(1000);
    service.time('foo');
    nowStub.returns(1100);
    service.timeEndWithAverage('foo', 'bar', 10);
    assert.isTrue(service.reporter.calledTwice);
    assert.isTrue(service.reporter.calledWithMatch(
        'timing-report', 'UI Latency', 'foo', 100));
    assert.isTrue(service.reporter.calledWithMatch(
        'timing-report', 'UI Latency', 'bar', 10));
  });

  test('reportExtension', () => {
    service.reportExtension('foo');
    assert.isTrue(service.reporter.calledWithExactly(
        'lifecycle', 'Extension detected', 'foo'
    ));
  });

  test('reportInteraction', () => {
    service.reporter.restore();
    sinon.spy(service, '_reportEvent');
    service.pluginsLoaded(); // so we don't cache
    service.reportInteraction('button-click', {name: 'sendReply'});
    assert.isTrue(service._reportEvent.getCall(2).calledWithMatch(
        {
          type: 'interaction',
          name: 'button-click',
          eventDetails: JSON.stringify({name: 'sendReply'}),
        }
    ));
  });

  test('report start time', () => {
    service.reporter.restore();
    sinon.stub(window.performance, 'now').returns(42);
    sinon.spy(service, '_reportEvent');
    const dispatchStub = sinon.spy(document, 'dispatchEvent');
    service.pluginsLoaded();
    service.time('timeAction');
    service.timeEnd('timeAction');
    assert.isTrue(service._reportEvent.getCall(2).calledWithMatch(
        {
          type: 'timing-report',
          category: 'UI Latency',
          name: 'timeAction',
          value: 0,
          eventStart: 42,
        }
    ));
    assert.equal(dispatchStub.getCall(2).args[0].detail.eventStart, 42);
  });

  suite('plugins', () => {
    setup(() => {
      service.reporter.restore();
      sinon.stub(service, '_reportEvent');
    });

    test('pluginsLoaded reports time', () => {
      sinon.stub(window.performance, 'now').returns(42);
      service.pluginsLoaded();
      assert.isTrue(service._reportEvent.calledWithMatch(
          {
            type: 'timing-report',
            category: 'UI Latency',
            name: 'PluginsLoaded',
            value: 42,
          }
      ));
    });

    test('pluginsLoaded reports plugins', () => {
      service.pluginsLoaded(['foo', 'bar']);
      assert.isTrue(service._reportEvent.calledWithMatch(
          {
            type: 'lifecycle',
            category: 'Plugins installed',
            eventDetails: JSON.stringify({pluginsList: ['foo', 'bar']}),
          }
      ));
    });

    test('caches reports if plugins are not loaded', () => {
      service.timeEnd('foo');
      assert.isFalse(service._reportEvent.called);
    });

    test('reports if plugins are loaded', () => {
      service.pluginsLoaded();
      assert.isTrue(service._reportEvent.called);
    });

    test('reports if metrics plugin xyz is loaded', () => {
      service.pluginLoaded('metrics-xyz');
      assert.isTrue(service._reportEvent.called);
    });

    test('reports cached events preserving order', () => {
      service.time('foo');
      service.time('bar');
      service.timeEnd('foo');
      service.pluginsLoaded();
      service.timeEnd('bar');
      assert.isTrue(service._reportEvent.getCall(0).calledWithMatch(
          {type: 'timing-report', category: 'UI Latency', name: 'foo'}
      ));
      assert.isTrue(service._reportEvent.getCall(1).calledWithMatch(
          {type: 'timing-report', category: 'UI Latency',
            name: 'PluginsLoaded'}
      ));
      assert.isTrue(service._reportEvent.getCall(2).calledWithMatch(
          {type: 'lifecycle', category: 'Plugins installed'}
      ));
      assert.isTrue(service._reportEvent.getCall(3).calledWithMatch(
          {type: 'timing-report', category: 'UI Latency', name: 'bar'}
      ));
    });
  });

  test('search', () => {
    service.locationChanged('_handleSomeRoute');
    assert.isTrue(service.reporter.calledWithExactly(
        'nav-report', 'Location Changed', 'Page', '_handleSomeRoute'));
  });

  suite('exception logging', () => {
    let fakeWindow;
    let reporter;

    const emulateThrow = function(msg, url, line, column, error) {
      return fakeWindow.onerror(msg, url, line, column, error);
    };

    setup(() => {
      reporter = service.reporter;
      fakeWindow = {
        handlers: {},
        addEventListener(type, handler) {
          this.handlers[type] = handler;
        },
      };
      sinon.stub(console, 'error');
      Object.defineProperty(appContext, 'reportingService', {
        get() {
          return service;
        },
      });
      const errorReporter = initErrorReporter(appContext);
      errorReporter.catchErrors(fakeWindow);
    });

    test('is reported', () => {
      const error = new Error('bar');
      error.stack = undefined;
      emulateThrow('bar', 'http://url', 4, 2, error);
      assert.isTrue(reporter.calledWith('error', 'exception', 'bar'));
      const payload = reporter.lastCall.args[3];
      assert.deepEqual(payload, {
        url: 'http://url',
        line: 4,
        column: 2,
        error,
      });
    });

    test('is reported with 3 lines of stack', () => {
      const error = new Error('bar');
      emulateThrow('bar', 'http://url', 4, 2, error);
      const expectedStack = error.stack.split('\n').slice(0, 3)
          .join('\n');
      assert.isTrue(reporter.calledWith('error', 'exception',
          expectedStack));
    });

    test('prevent default event handler', () => {
      assert.isTrue(emulateThrow());
    });

    test('unhandled rejection', () => {
      fakeWindow.handlers['unhandledrejection']({
        reason: {
          message: 'bar',
        },
      });
      assert.isTrue(reporter.calledWith('error', 'exception', 'bar'));
    });
  });
});

