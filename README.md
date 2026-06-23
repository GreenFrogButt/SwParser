This program helps mapping the game Starweb from Flying Buffalo Inc.

http://www.rickloomispbm.com/starweb.html

It keeps track of every world seen, makes connections to unseen worlds (If W1 and W2
connect to W3 but you can't see W3), and prints a list of worlds that changed
owners.
```
It takes 3 arguments:
    -t input turn name
    -d database to use      Defaults to "Database.txt"
    -o output file name     Defaults to database (-o)
```

If no output file (-o) is given the database (-d) is used.  This is my normal use.

I only play anonymous multi Starweb, I've never tried this on a regular game.
I also run Linux (actually, WSL under Windows) with Intellij IDEA IDE.

Java needs to be installed on your local computer: https://www.java.com/en/download/help/download_options.html

The executable is in ..../out/artifacts/SwParser_jar/SwParser.jar.
A copy is in src if you don't want to build from source. \
The file runTests.sh is a bash file I use for testing. \
Starweb.java.txt is the original Java this started from. \
The 2 main files are SwParser.kt and World.kt

Typical usage:

    Before the first turn delete file Database.txt so the file gets created

    For every turn:
    SwParser.jar -t "Starweb game name"

    Every subsequent turn will read the database, add the turn's information,
    and save the database.

Java needs to be installed on your local computer: https://www.java.com/en/download/help/download_options.html

Caveats:
I've been using the Java version for years, adding cruft as it came up.  I'm currently
playing my second game using the Kotlin version.  Probable issues: \
    planet buster bombs \
    consumer goods \
    ???

I've dorked with parsing artifacts and fleets but don't actually use them.

Error handling could be better.  Until very recently I always ran this from a
debugger and I wanted to see errors ASAP.  Everything dealing with command line
arguments is also very new.

HISTORY

I'm an embedded C programmer who taught myself Java when I retired.  The 3rd
program I wrote was the precursor to this, essentially C written with Java syntax.
The Java version has several years of "oops, didn't account for that" stuff added
so I'm testing by comparing the new Kotlin version against the Java version's output.

If you have a problem send me as much information as you can. Scrub turns of your
user/account/game info first.  I only play anonymous multi, if you're in an anonymous
multi game please don't send me any turn sheets for active games.

This is my first github.  I don't know what I'm doing here, be gentle.
