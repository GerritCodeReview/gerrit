:- load([rules,utils]).
:- begin_tests(t2).

% expected to pass or fail once.
:- test0(super_users(1000)).
:- test1(super_users(1001)).

:- test1(is_super_user(1001)).
:- test1(is_super_user(1002)).
:- test0(is_super_user(1003)).

:- test1(super_users(X)).  % expected fail (pass twice)
:- test1(multi_users(X)).  % expected fail (pass many times)

:- test1(single_user(X)).  % expected pass once

% Redefine change_owner, skip gerrit:change_owner,
% then test is_owner without a gerrit repository.

:- redefine(change_owner,1,(change_owner(42))).
:- test1(is_owner(42)).
:- test1(is_owner(X)).
:- test0(is_owner(24)).

:- end_tests(_,2).  % expect 2 failures
