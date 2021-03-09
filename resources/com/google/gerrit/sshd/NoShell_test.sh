#!/bin/bash

generalMessage="
SYNOPSIS
  ./NoShell_test.sh pid port user host time
DESCRIPTION
  This test compares the number of file descriptors before and after running the NoShell command
  a certain number of times.
  pid: the process id running the gerrit instance.
  port: The ssh port used by the gerrit instance.
  user: The username used.
  host: The host used for gerrit.
  time: Tghe number of times to trigger the command.
EXAMPLE
  ./NoShell_test.sh 29995 29418 admin dummyhost 100
"

if [ "$#" != 5 ]; then
  echo "$generalMessage"
  exit -1
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

firstFdCountMessage="The result of the file descriptor count before the loop is ${fdCountBeforeLoop}."
secondFdCountMessage="The result of the file descriptor count after the loop is ${fdCountAfterLoop}."
echo "${firstFdCountMessage}"
echo "${secondFdCountMessage}"

if [ "$?" -eq 0 ]; then
  echo "The test terminated successfully."
else
  echo "A problem happened while terminating the test. Termination is not successful."
fi
