#!/bin/sh
base=$(basename $1)
outputName=${base%%.*}.pde
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
          echo "$line" >> "$outputName";
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