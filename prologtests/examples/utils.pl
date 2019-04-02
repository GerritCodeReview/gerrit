%% Unit test helpers

% Write one line message.
msg(A) :- write(A), nl.
msg(A,B) :- write(A), msg(B).
msg(A,B,C) :- write(A), msg(B,C).
msg(A,B,C,D) :- write(A), msg(B,C,D).
msg(A,B,C,D,E) :- write(A), msg(B,C,D,E).
msg(A,B,C,D,E,F) :- write(A), msg(B,C,D,E,F).

% Redefine a caluse.
redefine(Atom,Arity,Clause) :- abolish(Atom/Arity), assertz(Clause).

% Increment/decrement of pass/fail counters.
set_counters(N,X,Y) :- redefine(test_count,3,test_count(N,X,Y)).
get_counters(N,X,Y) :- clause(test_count(N,X,Y), _) -> true ; (X=0, Y=0).
inc_pass_count :- get_counters(N,P,F), P1 is P + 1, set_counters(N,P1,F).
inc_fail_count :- get_counters(N,P,F), F1 is F + 1, set_counters(N,P,F1).

% Report pass or fail of G.
pass_1(G) :- msg('PASS: ', G), inc_pass_count.
fail_1(G) :- msg('FAIL: ', G), inc_fail_count.

% Report pass or fail of not(G).
pass_0(G) :- msg('PASS: not(', G, ')'), inc_pass_count.
fail_0(G) :- msg('FAIL: not(', G, ')'), inc_fail_count.

% Report a test as failed if it passed 2 or more times
pass_twice(G) :-
  msg('FAIL: (pass twice): ', G),
  inc_fail_count.
pass_many(G) :-
  G = [A,B|_],
  length(G, N),
  msg('FAIL: (pass ', N, ' times): ', [A,B,'...']),
  inc_fail_count.

% Test if G fails.
test0(G) :- once(G) -> fail_0(G) ; pass_0(G).

% Test if G passes exactly once.
test1(G) :-
  findall(G, G, S), length(S, N),
  (N == 0
   -> fail_1(G)
   ;  (N == 1
       -> pass_1(S)
       ;  (N == 2 -> pass_twice(S) ; pass_many(S))
      )
  ).

% Report the begin of test N.
begin_tests(N) :-
  nl,
  msg('BEGIN test ',N),
  set_counters(N,0,0).

% Repot the end of test N and total pass/fail counts,
% and check if the numbers are as exected OutP/OutF.
end_tests(OutP,OutF) :-
  get_counters(N,P,F),
  (OutP = P
   -> msg('Expected #PASS: ', OutP)
   ;  msg('ERROR: expected #PASS is ',OutP)
  ),
  (OutF = F
   -> msg('Expected #FAIL: ', OutF)
   ;  msg('ERROR: expected #FAIL is ',OutF)
  ),
  msg('END test ', N),
  nl.

% Repot the end of test N and total pass/fail counts.
end_tests(N) :- end_tests(N,_,_).
