:- load([aosp_rules,utils]).

:- begin_tests(t3_basic_conditions).

%% A negative test of is_exempt_uploader.
:- redefine(uploader,1,uploader(user(42))).  % mocked uploader
:- test1(uploader(user(42))).
:- test0(is_exempt_uploader).

%% Helper functions for positive test of is_exempt_uploader.
test_is_exempt_uploader(List) :- maplist(test1_uploader, List, _).
test1_uploader(X,_) :-
  redefine(uploader,1,uploader(user(X))),
  test1(uploader(user(X))),
  test1(is_exempt_uploader).
:- test_is_exempt_uploader([104, 106]).

%% Test has_build_cop_override.
:- redefine(commit_label,2,commit_label(label('Code-Review',1),user(102))).
:- test0(has_build_cop_override).
commit_label(label('Build-Cop-Override',1),user(101)).  % mocked 2nd label
:- test1(has_build_cop_override).
:- test1(commit_label(label(_,_),_)).           % expect fail, two matches
:- test1(commit_label(label('Build-Cop-Override',_),_)).  % good, one pass

%% TODO: more test for is_exempt_from_reviews.

%% Test needs_api_review, which checks commit_delta and project.
% Helper functions:
test_needs_api_review(File, Project, Tester) :-
  redefine(commit_delta,3,(commit_delta(R, _, P) :- regex_matches(R, File), P = File)),
  redefine(change_project,1,change_project(Project)),
  Goal =.. [Tester, needs_api_review],
  msg('# check CL with changed file ', File, ' in ', Project),
  once((Goal ; true)).  % do not backtrack

% Not api files
:- test_needs_api_review('api/Android.bp', 'platform/frameworks/base', test0).
:- test_needs_api_review('api/OWNERS', 'platform/frameworks/base', test0).
:- test_needs_api_review('api/TEST_MAPPING', 'platform/frameworks/base', test0).
:- test_needs_api_review('Android.bp', 'platform/frameworks/base', test0).
:- test_needs_api_review('frameworks/base/core/java/android/app/Activity.java', 'platform/frameworks/base', test0).

% Not java APIs

:- test_needs_api_review('media/libmedia/xsd/api/current.txt', 'platform/frameworks/av', test0).
:- test_needs_api_review('media/libmedia/xsd/api/removed.txt', 'platform/frameworks/av', test0).
:- test_needs_api_review('media/libstagefright/xmlparser/api/current.txt', 'platform/frameworks/av', test0).
:- test_needs_api_review('media/libstagefright/xmlparser/api/removed.txt', 'platform/frameworks/av', test0).

:- test_needs_api_review('audio/4.0/config/api/current.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/4.0/config/api/removed.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/5.0/config/api/current.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/5.0/config/api/removed.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/6.0/config/api/current.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/6.0/config/api/removed.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/effect/5.0/xml/api/current.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/effect/5.0/xml/api/removed.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/effect/6.0/xml/api/current.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/effect/6.0/xml/api/removed.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/policy/1.0/xml/api/current.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/policy/1.0/xml/api/removed.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/policy/1.0/xml/pfw_schemas/api/current.txt', 'platform/hardware/interfaces', test0).
:- test_needs_api_review('audio/policy/1.0/xml/pfw_schemas/api/removed.txt', 'platform/hardware/interfaces', test0).

:- test_needs_api_review('xsd/compatibilityMatrix/api/current.txt', 'platform/system/libvintf', test0).
:- test_needs_api_review('xsd/compatibilityMatrix/api/removed.txt', 'platform/system/libvintf', test0).
:- test_needs_api_review('xsd/halManifest/api/current.txt', 'platform/system/libvintf', test0).
:- test_needs_api_review('xsd/halManifest/api/removed.txt', 'platform/system/libvintf', test0).

:- test_needs_api_review('tests/resources/attr_group_simple/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/attr_group_simple/api/removed.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/group/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/group/api/removed.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/nested_type/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/nested_type/api/removed.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/predefined_types/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/predefined_types/api/removed.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/purchase_simple/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/purchase_simple/api/removed.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/reference/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/reference/api/removed.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/simple_complex_content/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/simple_complex_content/api/removed.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/simple_type/api/current.txt', 'platform/system/tools/xsdc', test0).
:- test_needs_api_review('tests/resources/simple_type/api/removed.txt', 'platform/system/tools/xsdc', test0).

% These are java apis

:- test_needs_api_review('api/current.txt', 'platform/external/apache-http', test1).
:- test_needs_api_review('api/removed.txt', 'platform/external/apache-http', test1).
:- test_needs_api_review('api/system-current.txt', 'platform/external/apache-http', test1).
:- test_needs_api_review('api/system-removed.txt', 'platform/external/apache-http', test1).
:- test_needs_api_review('api/test-current.txt', 'platform/external/apache-http', test1).
:- test_needs_api_review('api/test-removed.txt', 'platform/external/apache-http', test1).
:- test_needs_api_review('apex/media/framework/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/media/framework/api/module-lib-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/media/framework/api/module-lib-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/media/framework/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/media/framework/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/media/framework/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/framework/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/framework/api/module-lib-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/framework/api/module-lib-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/framework/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/framework/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/framework/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/service/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/permission/service/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/sdkextensions/framework/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/sdkextensions/framework/api/module-lib-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/sdkextensions/framework/api/module-lib-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/sdkextensions/framework/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/sdkextensions/framework/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/sdkextensions/framework/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/statsd/framework/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/statsd/framework/api/module-lib-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/statsd/framework/api/module-lib-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/statsd/framework/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/statsd/framework/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('apex/statsd/framework/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/lint-baseline.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/module-lib-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/module-lib-lint-baseline.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/module-lib-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/system-lint-baseline.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/test-lint-baseline.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('cmds/uiautomator/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('cmds/uiautomator/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('libs/usb/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('libs/usb/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('libs/usb/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('libs/usb/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('libs/usb/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('libs/usb/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('location/lib/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('location/lib/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('location/lib/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('location/lib/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('location/lib/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('location/lib/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/remotedisplay/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/remotedisplay/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/remotedisplay/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/remotedisplay/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/remotedisplay/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/remotedisplay/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/signer/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/signer/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/signer/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/signer/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/signer/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/signer/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/tvremote/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/tvremote/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/tvremote/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/tvremote/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/tvremote/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('media/lib/tvremote/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('nfc-extras/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('nfc-extras/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('nfc-extras/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('nfc-extras/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('nfc-extras/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('nfc-extras/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('obex/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('obex/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('obex/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('obex/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('obex/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('obex/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('packages/Tethering/common/TetheringLib/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('packages/Tethering/common/TetheringLib/api/module-lib-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('packages/Tethering/common/TetheringLib/api/module-lib-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('packages/Tethering/common/TetheringLib/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('packages/Tethering/common/TetheringLib/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('packages/Tethering/common/TetheringLib/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('services/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('services/api/lint-baseline.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('services/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('telephony/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('telephony/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-base/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-base/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-base/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-base/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-base/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-base/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-mock/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-mock/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-mock/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-mock/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-mock/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-mock/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-runner/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-runner/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-runner/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-runner/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-runner/api/test-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('test-runner/api/test-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('wifi/api/current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('wifi/api/module-lib-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('wifi/api/module-lib-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('wifi/api/removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('wifi/api/system-current.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('wifi/api/system-removed.txt', 'platform/frameworks/base', test1).
:- test_needs_api_review('api/current.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/module-lib-current.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/module-lib-removed.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/removed.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/system-current.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/system-removed.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/test-current.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/test-removed.txt', 'platform/frameworks/opt/net/ike', test1).
:- test_needs_api_review('api/current.txt', 'platform/frameworks/opt/tv/tvsystem', test1).
:- test_needs_api_review('api/removed.txt', 'platform/frameworks/opt/tv/tvsystem', test1).
:- test_needs_api_review('api/system-current.txt', 'platform/frameworks/opt/tv/tvsystem', test1).
:- test_needs_api_review('api/system-removed.txt', 'platform/frameworks/opt/tv/tvsystem', test1).
:- test_needs_api_review('api/test-current.txt', 'platform/frameworks/opt/tv/tvsystem', test1).
:- test_needs_api_review('api/test-removed.txt', 'platform/frameworks/opt/tv/tvsystem', test1).
:- test_needs_api_review('apex/framework/api/current.txt', 'platform/packages/providers/MediaProvider', test1).
:- test_needs_api_review('apex/framework/api/module-lib-current.txt', 'platform/packages/providers/MediaProvider', test1).
:- test_needs_api_review('apex/framework/api/module-lib-removed.txt', 'platform/packages/providers/MediaProvider', test1).
:- test_needs_api_review('apex/framework/api/removed.txt', 'platform/packages/providers/MediaProvider', test1).
:- test_needs_api_review('apex/framework/api/system-current.txt', 'platform/packages/providers/MediaProvider', test1).
:- test_needs_api_review('apex/framework/api/system-removed.txt', 'platform/packages/providers/MediaProvider', test1).
:- test_needs_api_review('car-lib/api/current.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('car-lib/api/lint-baseline.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('car-lib/api/removed.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('car-lib/api/system-current.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('car-lib/api/system-lint-baseline.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('car-lib/api/system-removed.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('car-lib/api/test-current.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('car-lib/api/test-removed.txt', 'platform/packages/services/Car', test1).
:- test_needs_api_review('28/public/api/removed.txt', 'platform/prebuilts/sdk', test1).
:- test_needs_api_review('libs/com.google.android.chromeos/api/current.txt', 'platform/vendor/google_arc', test1).
:- test_needs_api_review('libs/com.google.android.chromeos/api/removed.txt', 'platform/vendor/google_arc', test1).
:- test_needs_api_review('libs/com.google.android.chromeos/api/system-current.txt', 'platform/vendor/google_arc', test1).
:- test_needs_api_review('libs/com.google.android.chromeos/api/system-removed.txt', 'platform/vendor/google_arc', test1).
:- test_needs_api_review('libs/com.google.android.chromeos/api/test-current.txt', 'platform/vendor/google_arc', test1).
:- test_needs_api_review('libs/com.google.android.chromeos/api/test-removed.txt', 'platform/vendor/google_arc', test1).
:- test_needs_api_review('experimental2015/api/current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2015/api/removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2015/api/system-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2015/api/system-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2015/api/test-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2015/api/test-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2016/api/current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2016/api/removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2016/api/system-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2016/api/system-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2016/api/test-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2016/api/test-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2017/api/current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2017/api/removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2017/api/system-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2017/api/system-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2017/api/test-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2017/api/test-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2018/api/current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2018/api/removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2018/api/system-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2018/api/system-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2018/api/test-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2018/api/test-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2019/api/current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2019/api/removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2019/api/system-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2019/api/system-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2019/api/test-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2019/api/test-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020/api/current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020/api/removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020/api/system-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020/api/system-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020/api/test-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020/api/test-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020_midyear/api/current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020_midyear/api/removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020_midyear/api/system-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020_midyear/api/system-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020_midyear/api/test-current.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('experimental2020_midyear/api/test-removed.txt', 'platform/vendor/google/frameworks/camera', test1).
:- test_needs_api_review('mca/api/current.txt', 'platform/vendor/google/media', test1).
:- test_needs_api_review('mca/api/removed.txt', 'platform/vendor/google/media', test1).
:- test_needs_api_review('mca/api/system-current.txt', 'platform/vendor/google/media', test1).
:- test_needs_api_review('mca/api/system-removed.txt', 'platform/vendor/google/media', test1).
:- test_needs_api_review('mca/api/test-current.txt', 'platform/vendor/google/media', test1).
:- test_needs_api_review('mca/api/test-removed.txt', 'platform/vendor/google/media', test1).

%% TODO: Test needs_drno_review, needs_qualcomm_review

%% TODO: Test opt_out_find_owners.

:- test1(opt_in_find_owners).  % default, unless opt_out_find_owners

:- end_tests_or_halt(1).  % expect 1 failure of multiple commit_label

%% Test remove_label
:- begin_tests(t3_remove_label).

:- test1(remove_label('MyReview',[],[])).
:- test1(remove_label('MyReview',submit(),submit())).
:- test1(remove_label(myR,[label(a,X)],[label(a,X)])).
:- test1(remove_label(myR,[label(myR,_)],[])).
:- test1(remove_label(myR,[label(a,X),label(myR,_)],[label(a,X)])).
:- test1(remove_label(myR,submit(label(a,X)),submit(label(a,X)))).
:- test1(remove_label(myR,submit(label(myR,_)),submit())).

%% Test maplist
double(X,Y) :- Y is X * X.
:- test1(maplist(double, [2,4,6], [4,16,36])).
:- test1(maplist(double, [], [])).

:- end_tests_or_halt(0).  % expect no failure

%% TODO: Add more tests.
