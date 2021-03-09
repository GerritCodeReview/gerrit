#!/bin/bash
first_result=$(ls /proc/$1/fd | wc -l)
for ((c=0; c<1000; c++))
do
	ssh -p $2 $3@$4
done
second_result=$(ls /proc/$1/fd | wc -l)

echo $first_result
echo $second_result
if [ $first_result == $second_result ]
then
	echo "the two results are equal"
else
	echo "the two results are not equal after 1000 user"
fi
