#!/bin/sh
program=../out/artifacts/SwParser_jar/SwParser.jar
ls -lt $program
$program -t testData/turnIn.txt -d testData/dbin.txt -o results.txt
diff -b results.txt testData/expectedResults.txt

