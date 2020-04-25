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
test_needs_api_review(Project, File, Tester) :-
  redefine(commit_delta,3,(commit_delta(R, _, P) :- regex_matches(R, File), P = File)),
  redefine(change_project,1,change_project(Project)),
  Goal =.. [Tester, needs_api_review],
  msg('# check CL with changed file ', File, ' in ', Project),
  once((Goal ; true)).  % do not backtrack

%%%%%%%%%%%%%%%%%%%%%%%%
%% API-Review needed
%%%%%%%%%%%%%%%%%%%%%%%%

% These are java apis
:- test_needs_api_review('platform/external/apache-http', 'api/current.txt', test1).
:- test_needs_api_review('platform/external/icu', 'android_icu4j/api/intra/current.txt', test1).
:- test_needs_api_review('platform/frameworks/base', 'api/removed.txt', test1).
:- test_needs_api_review('platform/frameworks/base', 'media/lib/remotedisplay/api/test-removed.txt', test1).
:- test_needs_api_review('platform/prebuilts/sdk', '24/public/api/android.txt', test1).
:- test_needs_api_review('platform/vendor/google/frameworks/camera', 'experimental2020_midyear/api/system-current.txt', test1).
:- test_needs_api_review('platform/vendor/unbundled_google/libraries/camera2_stubs', 'prebuilt/1/public/api/com.google.android.camera.experimental2018-removed.txt', test1).

% Sysprop files
:- test_needs_api_review('platform/system/core', 'healthd/api/charger_sysprop-current.txt', test1).

%%%%%%%%%%%%%%%%%%%%%%%%
%% No API-Review needed
%%%%%%%%%%%%%%%%%%%%%%%%
% Not api files
:- test_needs_api_review('platform/frameworks/base', 'api/Android.bp', test0).
:- test_needs_api_review('platform/frameworks/base', 'api/OWNERS', test0).
:- test_needs_api_review('platform/frameworks/base', 'api/TEST_MAPPING', test0).
:- test_needs_api_review('platform/frameworks/base', 'Android.bp', test0).
:- test_needs_api_review('platform/frameworks/base', 'frameworks/base/core/java/android/app/Activity.java', test0).

% Not java APIs
:- test_needs_api_review('platform/frameworks/av', 'media/libmedia/xsd/api/current.txt', test0).

% CMakeLists excluded
:- test_needs_api_review('platform/external/OpenCL-CTS', 'test_conformance/api/CMakeLists.txt', test0).
:- test_needs_api_review('platform/vendor/qcom/sm7250', 'proprietary/chi-cdk/api/generated/build/linuxembedded/CMakeLists.txt', test0).

% XSD/XML
:- test_needs_api_review('platform/frameworks/av', 'media/libmedia/xsd/api/current.txt', test0).
:- test_needs_api_review('platform/frameworks/av', 'media/libstagefright/xmlparser/api/current.txt', test0).

% Excluded projects
:- test_needs_api_review('platform/hardware/interfaces', 'audio/6.0/config/api/last_current.txt', test0).
:- test_needs_api_review('platform/system/tools/xsdc', 'tests/resources/attr_group_simple/api/current.txt', test0).
:- test_needs_api_review('platform/prebuilts/go/linux-x86', 'api/go1.7.txt', test0).
:- test_needs_api_review('device/generic/vulkan-cereal', 'protocols/vk-gen/api/defines/VK_VERSION_MINOR.txt', test0).
:- test_needs_api_review('platform/external/qemu', 'android/android-emugl/host/libs/libOpenglRender/vulkan-registry/api/structs/VkExternalMemoryImageCreateInfo.txt', test0).


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
