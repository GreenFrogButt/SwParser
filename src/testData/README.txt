Test data for regression testing.

Running _turnIn2_17.txt against _db2_16.txt should yeild _db2_17.txt.
I don't have a _sb2_18.txt, but _turnIn2_18.txt has some PBB action so ensure
it doesn't crash.

_db2_16.txt
_db2_17.txt

_turnIn2_16.txt
_turnIn2_17.txt
_turnIn2_18.txt

The below are my first shot, they worked.  Need to remember which set
goes to what

_db15.txt
_db16.txt
_db17.txt
_turn15.txt
_turnin16.txt
_expected15.txt
_expected16.txt

What I'm using
-t foo -d bar -o results; diff -b results testData/blatz
W32 and W34 have expected diffs.

What I was using
_turnIn16.txt
_db16.txt
_db17.txt
===========================

One test case:
testData/turnIn.txt
testData/dbIn.txt
testData/expectedResults.txt

These are from the game I'm in now.
dbIn.txt
expectedResults.txt
turnIn.txt
