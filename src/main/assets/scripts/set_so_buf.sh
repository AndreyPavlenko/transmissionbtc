#!/system/bin/sh

R=4194304
W=1048576

RMAX=$(cat /proc/sys/net/core/rmem_max)
WMAX=$(cat /proc/sys/net/core/wmem_max)

[ $RMAX -lt $R ] && echo $R > /proc/sys/net/core/rmem_max
[ $WMAX -lt $W ] && echo $W > /proc/sys/net/core/wmem_max

RMAX=$(cat /proc/sys/net/core/rmem_max)
WMAX=$(cat /proc/sys/net/core/wmem_max)

if [ $RMAX -lt $R ] || [ $WMAX -lt $W ]; then
  exit 2
else
  exit 0
fi
