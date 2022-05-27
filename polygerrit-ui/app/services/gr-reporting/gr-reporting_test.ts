/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup-karma';
import {
  GrReporting,
  DEFAULT_STARTUP_TIMERS,
  initErrorReporter,
} from './gr-reporting_impl';
import {getAppContext} from '../app-context';
import {Deduping} from '../../api/reporting';
import {SinonFakeTimers} from 'sinon';

suite('gr-reporting tests', () => {
  // We have to type as any because we access
  // private properties for testing.
  let service: any;

  let clock: SinonFakeTimers;
  let fakePerformance: any;

  const NOW_TIME = 100;

  setup(() => {
    clock = sinon.useFakeTimers(NOW_TIME);
    service = new GrReporting(getAppContext().flagsService);
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
        'timing-report',
        'UI Latency',
        'App Started',
        42
      )
    );
    assert.isTrue(
      service.reporter.calledWithExactly(
        'timing-report',
        'UI Latency',
        'NavResTime - loadEventEnd',
        fakePerformance.loadEventEnd - fakePerformance.navigationStart,
        undefined,
        true
      )
    );
  });

  test('WebComponentsReady', () => {
    sinon.stub(window.performance, 'now').returns(42);
    service.timeEnd('WebComponentsReady');
    assert.isTrue(
      service.reporter.calledWithMatch(
        'timing-report',
        'UI Latency',
        'WebComponentsReady',
        42
      )
    );
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
    assert.isFalse(
      Object.prototype.hasOwnProperty.call(service._baselines, 'garbage')
    );
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
    assert.isFalse(service.timeEnd.calledWithExactly('ChangeFullyLoaded'));
    assert.isTrue(
      service.timeEnd.calledWithExactly('StartupChangeFullyLoaded')
    );
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
    assert.isFalse(service.timeEnd.calledWithExactly('FileListDisplayed'));
    assert.isTrue(
      service.timeEnd.calledWithExactly('StartupFileListDisplayed')
    );
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
    sinon.stub(window, 'performance').value({
      memory: {
        usedJSHeapSize: 1024 * 1024,
      },
      measure: () => {},
      now: () => {
        42;
      },
    });
    service.reportRpcTiming('/changes/*~*/comments', 500);
    service.dashboardDisplayed();
    assert.isTrue(
      service.timeEnd.calledWithExactly('StartupDashboardDisplayed', {
        rpcList: [
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
      })
    );
  });

  suite('hidden duration', () => {
    let nowStub: sinon.SinonStub;
    let visibilityStateStub: sinon.SinonStub;
    const assertHiddenDurationsMs = (hiddenDurationMs: number) => {
      service.dashboardDisplayed();
      assert.isTrue(
        service.timeEnd.calledWithMatch('StartupDashboardDisplayed', {
          hiddenDurationMs,
        })
      );
    };

    setup(() => {
      sinon.spy(service, 'timeEnd');
      nowStub = sinon.stub(window.performance, 'now');
      visibilityStateStub = {
        value: value => {
          Object.defineProperty(document, 'visibilityState', {
            value,
            configurable: true,
          });
        },
      } as sinon.SinonStub;
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
        service.timeEnd.calledWithMatch('DashboardDisplayed', {
          hiddenDurationMs: 0,
        })
      );
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
    assert.isTrue(
      service.reporter.calledWithMatch('timing-report', 'UI Latency', 'foo', 3)
    );
    assert.isTrue(
      service.reporter.calledWithMatch('timing-report', 'UI Latency', 'bar', 1)
    );
  });

  test('timer object', () => {
    const nowStub = sinon.stub(window.performance, 'now').returns(100);
    const timer = service.getTimer('foo-bar');
    nowStub.returns(150);
    timer.end();
    assert.isTrue(
      service.reporter.calledWithMatch(
        'timing-report',
        'UI Latency',
        'foo-bar',
        50
      )
    );
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

  test('reportExtension', () => {
    service.reportExtension('foo');
    assert.isTrue(
      service.reporter.calledWithExactly(
        'lifecycle',
        'Extension detected',
        'Extension detected',
        undefined,
        {name: 'foo'}
      )
    );
  });

  test('reportInteraction', () => {
    service.reporter.restore();
    sinon.spy(service, '_reportEvent');
    service.pluginsLoaded(); // so we don't cache
    service.reportInteraction('button-click', {name: 'sendReply'});
    assert.isTrue(
      service._reportEvent.getCall(2).calledWithMatch({
        type: 'interaction',
        name: 'button-click',
        eventDetails: JSON.stringify({name: 'sendReply'}),
      })
    );
  });

  test('trackApi reports same event only once', () => {
    sinon.spy(service, '_reportEvent');
    const pluginApi = {getPluginName: () => 'test'};
    service.trackApi(pluginApi, 'object', 'method');
    service.trackApi(pluginApi, 'object', 'method');
    assert.isTrue(service.reporter.calledOnce);
    service.trackApi(pluginApi, 'object', 'method2');
    assert.isTrue(service.reporter.calledTwice);
  });

  test('report start time', () => {
    service.reporter.restore();
    sinon.stub(window.performance, 'now').returns(42);
    sinon.spy(service, '_reportEvent');
    const dispatchStub = sinon.spy(document, 'dispatchEvent');
    service.pluginsLoaded();
    service.time('timeAction');
    service.timeEnd('timeAction');
    assert.isTrue(
      service._reportEvent.getCall(2).calledWithMatch({
        type: 'timing-report',
        category: 'UI Latency',
        name: 'timeAction',
        value: 0,
        eventStart: 42,
      })
    );
    assert.equal(
      (dispatchStub.getCall(2).args[0] as CustomEvent).detail.eventStart,
      42
    );
  });

  test('dedup', () => {
    assert.isFalse(service._dedup('a', undefined, undefined));
    assert.isFalse(service._dedup('a', undefined, undefined));

    let deduping = Deduping.EVENT_ONCE_PER_SESSION;
    assert.isFalse(service._dedup('b', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('b', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('b', {x: 'bar'}, deduping));

    deduping = Deduping.DETAILS_ONCE_PER_SESSION;
    assert.isFalse(service._dedup('c', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('c', {x: 'foo'}, deduping));
    assert.isFalse(service._dedup('c', {x: 'bar'}, deduping));
    assert.isTrue(service._dedup('c', {x: 'bar'}, deduping));

    deduping = Deduping.EVENT_ONCE_PER_CHANGE;
    service.setChangeId(1);
    assert.isFalse(service._dedup('d', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('d', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('d', {x: 'bar'}, deduping));
    service.setChangeId(2);
    assert.isFalse(service._dedup('d', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('d', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('d', {x: 'bar'}, deduping));

    deduping = Deduping.DETAILS_ONCE_PER_CHANGE;
    service.setChangeId(1);
    assert.isFalse(service._dedup('e', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('e', {x: 'foo'}, deduping));
    assert.isFalse(service._dedup('e', {x: 'bar'}, deduping));
    assert.isTrue(service._dedup('e', {x: 'bar'}, deduping));
    service.setChangeId(2);
    assert.isFalse(service._dedup('e', {x: 'foo'}, deduping));
    assert.isTrue(service._dedup('e', {x: 'foo'}, deduping));
    assert.isFalse(service._dedup('e', {x: 'bar'}, deduping));
    assert.isTrue(service._dedup('e', {x: 'bar'}, deduping));
  });

  suite('plugins', () => {
    setup(() => {
      service.reporter.restore();
      sinon.stub(service, '_reportEvent');
    });

    test('pluginsLoaded reports time', () => {
      sinon.stub(window.performance, 'now').returns(42);
      service.pluginsLoaded();
      assert.isTrue(
        service._reportEvent.calledWithMatch({
          type: 'timing-report',
          category: 'UI Latency',
          name: 'PluginsLoaded',
          value: 42,
        })
      );
    });

    test('pluginsLoaded reports plugins', () => {
      service.pluginsLoaded(['foo', 'bar']);
      assert.isTrue(
        service._reportEvent.calledWithMatch({
          type: 'lifecycle',
          category: 'Plugins installed',
          eventDetails: JSON.stringify({pluginsList: ['foo', 'bar']}),
        })
      );
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
      assert.isTrue(
        service._reportEvent.getCall(0).calledWithMatch({
          type: 'timing-report',
          category: 'UI Latency',
          name: 'foo',
        })
      );
      assert.isTrue(
        service._reportEvent.getCall(1).calledWithMatch({
          type: 'timing-report',
          category: 'UI Latency',
          name: 'PluginsLoaded',
        })
      );
      assert.isTrue(
        service._reportEvent
          .getCall(2)
          .calledWithMatch({type: 'lifecycle', category: 'Plugins installed'})
      );
      assert.isTrue(
        service._reportEvent.getCall(3).calledWithMatch({
          type: 'timing-report',
          category: 'UI Latency',
          name: 'bar',
        })
      );
    });
  });

  test('search', () => {
    service.locationChanged('_handleSomeRoute');
    assert.isTrue(
      service.reporter.calledWithExactly(
        'nav-report',
        'Location Changed',
        'Page',
        '_handleSomeRoute'
      )
    );
  });

  suite('exception logging', () => {
    let fakeWindow: any;
    let reporter: sinon.SinonStub;

    const emulateThrow = function (
      msg?: string,
      url?: string,
      line?: number,
      column?: number,
      error?: Error
    ) {
      return fakeWindow.onerror(msg, url, line, column, error);
    };

    setup(() => {
      reporter = service.reporter;
      fakeWindow = {
        handlers: {},
        addEventListener(type: string, handler: object) {
          this.handlers[type] = handler;
        },
      };
      sinon.stub(console, 'error');
      Object.defineProperty(getAppContext(), 'reportingService', {
        get() {
          return service;
        },
      });
      const errorReporter = initErrorReporter(getAppContext().reportingService);
      errorReporter.catchErrors(fakeWindow);
    });

    test('is reported', () => {
      const error = new Error('bar');
      error.stack = undefined;
      emulateThrow('bar', 'http://url', 4, 2, error);
      assert.isTrue(reporter.calledWith('error', 'exception', 'onError: bar'));
    });

    test('is reported with stack', () => {
      const error = new Error('bar');
      emulateThrow('bar', 'http://url', 4, 2, error);
      const eventDetails = reporter.lastCall.args[4];
      assert.equal(error.stack, eventDetails.stack);
    });

    test('prevent default event handler', () => {
      assert.isTrue(emulateThrow());
    });

    test('unhandled rejection', () => {
      const newError = new Error('bar');
      fakeWindow.handlers['unhandledrejection']({reason: newError});
      assert.isTrue(
        reporter.calledWith('error', 'exception', 'unhandledrejection: bar')
      );
    });
  });
});
