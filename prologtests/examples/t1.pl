:- load([rules,utils]).
:- begin_tests(t1).

:- test1(true).     % expect true to pass
:- test0(false).    % expect false to fail

:- test1(X = 3).    % unification should pass
:- test1(_ = 3).    % unification should pass
:- test0(X \= 3).   % not-unified should fail

% (7-4) should have expected result
:- test1((X is (7-4), X =:= 3)).
:- test1((X is (7-4), X =\= 4)).

% memberchk should pass/fail exactly once
:- test1(memberchk(3,[1,3,5,3])).
:- test0(memberchk(2,[1,3,5,3])).
:- test0(memberchk(2,[])).

:- end_tests(_,0).  % expect no failure
