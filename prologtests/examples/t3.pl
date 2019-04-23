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
  redefine(commit_delta,1,(commit_delta(R) :- regex_matches(R, File))),
  redefine(change_project,1,change_project(Project)),
  Goal =.. [Tester, needs_api_review],
  msg('# check CL with changed file ', File, ' in ', Project),
  once((Goal ; true)).  % do not backtrack

:- test_needs_api_review('apio/test.cc', 'platform/art', test0).
:- test_needs_api_review('api/test.cc', 'platform/art', test0).
:- test_needs_api_review('api/test.cc', 'platform/prebuilts/sdk', test1).
:- test_needs_api_review('d1/d2/api/test.cc', 'platform/prebuilts/sdk', test1).
:- test_needs_api_review('system-api/d/t.c', 'platform/external/apache-http', test1).

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
