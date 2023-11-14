#!/bin/sh
base=$(basename $1)
outputName=${base%%.*}.pde
echo $outputName
doCopy="false"
while IFS= read -r line; do
    if [ "$doCopy" = "true" ]; then
        case "$line" in
            *+++END*)
              doCopy="false";
            ;;
            *+++ONLY*)
            ;;
            *)
          echo "$line";
            ;;
        esac
    else
        case "$line" in
            *+++START*)
                doCopy="true"
                ;;
        esac
    fi
done < "$1"