#!/bin/bash
# Change this to your netid
netid=sxz162330

#
# Root directory of your project
PROJDIR=/home/012/s/sx/sxz162330/AOS/Project2

#
# Directory where the config file is located on your local system
CONFIGLOCAL=config.txt

n=0

cat $CONFIGLOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read -r firstLine
    # first line has info of numOfNode, interRequestDelay, csExecutionTime, numOfRequest
    numOfNode=$(echo "$firstLine" | awk '{ print $1 }')
    interRequestDelay=$(echo "$firstLine" | awk '{ print $2 }')
    csExecutionTime=$(echo "$firstLine" | awk '{ print $3 }')
    maxNumOfRequest=$(echo "$firstLine" | awk '{ print $4 }')

    while [[ $n -lt $numOfNode ]]
    do
    	read line
        host=$( echo $line | awk '{ print $2 }' )

        echo $host
        gnome-terminal -- sh -c "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host killall -u $netid" &
        sleep 1

        n=$(( n + 1 ))
    done

)


echo "Cleanup complete"
