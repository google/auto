#!/bin/bash
if [ $# -lt 1 ]; then
  echo "usage $0 <ssl-key> [<param> ...]"
  exit 1;
fi
key=${1}
shift
params=${@}

#validate key
keystatus=$(gpg --list-keys | grep ${key} | awk '{print $1}')
if [ "${keystatus}" != "pub" ]; then
  echo "Could not find public key with label ${key}"
  echo -n "Available keys from: "
  gpg --list-keys | grep --invert-match '^sub'

  exit 1
fi

mvn ${params} clean site:jar -DperformRelease=true -Dgpg.skip=false -Dgpg.keyname=${key} deploy
