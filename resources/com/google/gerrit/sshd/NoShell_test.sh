#!/bin/bash

generalMessage="
./NoShell_test.sh process_id ssh_port user_name host_name number_of_times

Compares the number of file descriptors before and after running the NoShell command
toward the specified Gerrit process, for the given number of times. Example:

./NoShell_test.sh 12345 29418 admin localhost 100
"

if [ "$#" != 5 ]; then
  echo "$generalMessage"
  exit 1
fi

pid="$1"
port=$2
user=$3
host=$4
times=$5

fdCountBeforeLoop=$(find /proc/"$pid"/fd | wc -l)
for ((iterations = 0; iterations < "$times"; iterations++)); do
  ssh -p "$port" "$user"@"$host"
done
fdCountAfterLoop=$(find /proc/"$pid"/fd | wc -l)

beforeFdCountMessage="File descriptor count before the loop: ${fdCountBeforeLoop}."
afterFdCountMessage="File descriptor count after the loop: ${fdCountAfterLoop}."
if [ "$fdCountBeforeLoop" == "$fdCountAfterLoop" ]; then
  echo "The test is successful. The counts of file descriptors are equal."
else
  echo "The test fails. The counts of file descriptors are different."
  printf "%s\n%s\n" "${firstFdCountMessage}" "${secondFdCountMessage}"
fi

terminationStatus="$?"
if [ "$terminationStatus" != 0 ]; then
  echo "The last executed ssh command exited with non-zero status ${terminationStatus}."
fi
