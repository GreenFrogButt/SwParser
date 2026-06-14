/**
 * Some 10-15 years ago I wrote my 3rd Java program, a Starweb turn parser.
 * It's not pretty.  So I'm re-writing it.  Along the way I'm finding bugs in
 * the original Java that I never noticed.
 */

import Utilities.hasMatch
import Utilities.simpleInt
import java.io.*
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.*
import kotlin.system.exitProcess

/**
 * Parse a turn, filling in data on Worlds, Fleets, and Artifacts
 * Fleets and artifacts not yet fully implemented.
 *
 *  Turn sheets are broken up into records which are 1 or more lines separated
 *  by a blank line.  So "foo bar" is a record, as is
 *  "foo"
 *  "bar"
 *
 *  "blatz"         // this is the start of the next record
 *
 *  Turn sheets have 3 parts: Header, body, and other.
 *
 *  1)  In the header look for something like:
 *          `Game SW-A1234, Turn 3, [ENIF]`, we want the game name and turn number
 *
 *  1.5)  In the header look for "[foo]:", foo is a player name and the left
 *        bracket is at the start of the line.
 *
 *  2)  A record of dashes "------------" denotes the end of the header and start
 *      of the body.
 *
 *      Note that in the Database header I can put anything I want.  Except for the lines
 *      above the parser ignores lines it doesn's understand.  I used this for
 *      worlds that changed owners.
 *
 *  3)  The body has a list of worlds, like:
 *     W74 (71,178,232) [CERES] (Industry=30,Metal=30,Mines=2,Population=50,
 *           Limit=100,Turns=1,I-Ships=1,P-Ships=1)
 *        F12[CERES]=0
 *        F189[CERES]=0
 *        F225[CERES]=0
 *        F227[CERES]=0
 *        F243[CERES]=0
 *
 *     Can also look like:
 *        W5 (4,79,202) [DUNE] (Gift from [PAVO],Metal=35,Mines=4,Population=89,
 *     or
 *        `W134 (130,150,160,199) [ENIF] (Captured,Lost by [WYRM],Industry=1/0,`
 *     or
 *        `W83 (59,99,181,198) [ENIF] (Captured,Industry=1,Metal=3,Mines=8,`
 *          Population=88,Limit=88,Turns=2)
 *
 * 4) Any record in the body that does not start with "W" denotes the end of the body.
 *
 * 5) Currently I don't do anything with anything after the body.
 */

class SwParser {
    private val versionString = "1.0"
    fun getVersionString() : String { return versionString }

    data class GameInfo(
        val id: String = "",     // e.g. "SW-BM/100"
        val turn: Int = 0        // turn number
    )

    private var gameNameAndTurn = GameInfo()
    private val artifactManager = ArtifactManager()
    private val fleetManager = FleetManager(artifactManager)
    private val worldManager = WorldManager(fleetManager)

    private var myNames = mutableMapOf<String, String>()   // map name to player type

    // It's useful to keep a running total of these turn to turn
    private var totalWorlds = 0
    private var totalKeys = 0
    private var totalShips = 0
    private var totalIndustry = 0
    private var totalMines = 0

    /**
     * Parse the header, filling in worldList.
     */
    private fun parseHeader(reader: BufferedReader) {
        while (reader.ready()) {
            val record = getRecord(reader)
            if (hasMatch("--------", record)) break     // end of header

            when {
                hasMatch("Game", record)     -> gameNameAndTurn = getGameInfo(record)
                hasMatch("^\\[", record)     -> getName(record)
                hasMatch("^Version", record) -> { /* ignored, I should do something with it */ }
                hasMatch("^(?:SW|\\{|Player|Worlds|World|Available|Subject)", record) -> { /* Ignored */ }
                else                         -> println("parseHeader: unknown line <$record>")
            }
        }

        worldManager.setMyNames(myNames)
        worldManager.setTurnNumber(gameNameAndTurn.turn)
    }

    /**
     * Parse the database if it exists.
     */
   private  fun parseDatabase(fileName: String, debug: Boolean = false) {
        if (debug) println("Parsing database $fileName")
        getFileReader(fileName, create = false)?.use { reader ->
            parseHeader(reader)
            getWorlds(reader, true)
        }
    }

    /**
     * The same parsers are used for both the database and turn, parsing the database
     * may have set some things we don't want set
     */
    private fun initializeTurn() {
        worldManager.clearChangedOwners()
        worldManager.clearTotalMetal()
        totalWorlds = 0
        totalKeys = 0
        totalShips = 0
        totalIndustry = 0
        totalMines = 0
    }

    /**
     * If the database existed ensure the game names are the same, if not print an error and exit.
     * Then ensure the turn only increased by 1, otherwise print a message (missed turn) and continue
     *
     * @return true if the database matches the turn, else false
     */
    private fun validateDatabaseMatchesTurn(db: GameInfo, turn: GameInfo) : Boolean {
        if (turn == GameInfo()) {
            println("No valid game or turn found, turn: $turn")
            return false
        }

        if (db != GameInfo()) {
            val whoopsi = "Database is for game ${db.id} turn ${db.turn}, " +
                    "Turn is for game ${turn.id} turn ${turn.turn}"

            if (db.id != turn.id) {
                println("Database and turn are for different games.")
                println(whoopsi)
                return false
            }
            if ( (db.turn + 1) != turn.turn) {
                println("Skipped turns.")
                println(whoopsi)
                //return false
                return true      // turns out it's handy to skip turns sometimes
            }
        }
        return true
    }

    /**
     * Parse a turn.
     */
    private fun parseTurn(turnName: String, debug: Boolean = false) {
        if(debug) println("parseTurn: $turnName")
        initializeTurn()
        // code smell:  gameNameAndTurn got set in parseHeader.  I should fix this.
        val databaseGameAndTurn = gameNameAndTurn
        val turnReader = getFileReader(turnName, create = false)
        if (turnReader == null) {
            println("Can't open turn $turnName")
            exitProcess(-1)
        }
        parseHeader(turnReader)

        if (!validateDatabaseMatchesTurn(databaseGameAndTurn, gameNameAndTurn)) {
            println("Turn $turnName doesn't match the database")
            exitProcess(-1)
        }
        worldManager.setTurnNumber(gameNameAndTurn.turn)
        getWorlds(turnReader, false)
        turnReader.close()
    }

    /**
     * Given a filename open the file and return the reader.  Files from FBI
     * have 0xc2 0xa0 in them, which is a Unicode non breakable space (&nbsp).
     * I can't figure out how to get my scanner to deal with them, so strip
     * them out.
     *
     *  file exists  create
     *      y           x   return file
     *      n           y   create file, return null
     *      n           n   print message, throw exception, exit
     */

    private fun getFileReader(fileName: String, create: Boolean = false, debug: Boolean = false): BufferedReader? {
        if (debug) println("getFileReader: Opening $fileName (create = $create)")
        try {
            val file = File(fileName)
            if (file.exists()) return file.bufferedReader(StandardCharsets.UTF_8)

            if (create)
                file.createNewFile()
            return null

        } catch (e: Exception) {
            println("getFileReader: error opening $fileName, and create is $create.\n$e")
            exitProcess(-1)
        }
    }

    /**
     * Prints the game to stdout.  Currently matches the Java version so I can
     * test via 'diff', that will probably change when I'm convinced everything works.
     */

    @Suppress("unused")
    fun printGame(destination: PrintStream = System.out, full: Boolean = false, makeADecision: Boolean = false) {
        // header
        destination.println("Version ${getVersionString()}")
        if(makeADecision) {
            // Don't know which format I prefer yet.
            myNames.forEach { name ->
                destination.println("[${name.key}]: ${name.value}\n")
            }
        } else
            destination.println("$myNames\n")

        destination.println(String.format(
                "Worlds %d  Keys %d  Ships %d  Ind %d  Mines %d\n",
                totalWorlds, totalKeys, totalShips, totalIndustry, totalMines
            )
        )

        destination.println("Available metal: ${worldManager.getTotalMetal()}\n")

        val changed = worldManager.getChangedOwners()
        changed.forEach { changed ->
            destination.println(changed)
        }

        destination.println("\nGame ${gameNameAndTurn.id},  Turn ${gameNameAndTurn.turn}.   ${LocalDate.now()}\n")

        destination.println("\n  ------------")

        // body
        worldManager.printWorlds()

        // Had a game where some worlds only had 1 connection.
        // TODO: track down that game and ensure the parser Does The Right Thing
        //for (world in worldList) {
        //    if (world.connections.size > 1 || world.last != 0)
        //        println(world)
        //}
        if(full)    destination.println("printGame: full not implemented")
    }

    /**
     * Redirect stdout to a file, print the game, and put stdout back.
     * This is a hack, I need to write directly to a file.
     */

    // TODO:  This Is The Way, but it doesn't work :(
    private fun xaveDatabase(fileName: String, debug: Boolean = false) {
        if (debug) println("saveDatabase: fileName = $fileName")
        try {
            PrintStream(fileName).use { out ->
                printGame(destination = out)
            }
        } catch (io: IOException) {
            // "Permission denied" shows up here
            println("IOException, is $fileName read only?\n$io")
        } catch (e: Exception) {
            println("Can't open $fileName for writing: $e")
        }
    }
    private fun saveDatabase(fileName: String, debug: Boolean = false) {
        if (debug) println("saveDatabase: fileName = $fileName")
        val old: PrintStream? = System.out
        try {
            // Don't care if it already exists or not
            File(fileName).createNewFile()

            System.setOut(PrintStream(fileName))
            printGame()
            System.setOut(old)
        } catch (io: IOException) {
            // "Permission denied" shows up here
            println("IOException, is $fileName read only?\n$io")
        } catch (e: Exception) {
            println("Can't open $fileName for writing: $e")
        }
    }

    /**
     * Print worlds, but not fleet nor artifact info.
     * Currently unused except in development branches.
     * TODO: look at saveDatabase(), make it work like that.
     */
    @Suppress("unused")
    fun printWorlds(fileName: String, debug: Boolean = false) {
        if (debug) println("printWorlds: fileName = $fileName")
        try {
            // Don't care if it already exists or not
            File(fileName).createNewFile()
            printGame()
        } catch (io: IOException) {
            // "Permission denied" shows up here
            println("IOException, is $fileName read only?\n$io")
        } catch (e: Exception) {
            println("Can't open $fileName for writing: $e")
        }
    }

    /**
     * Get the list of worlds, artifacts, and fleets; filling in worldList, etc
     *
     * A world has the following form, we're given that all on 1 line:
     * W9 (58,76,122) [MARS] (Captured,Metal=1,Mines=1,Population=28,Limit=78,
     *      Turns=1) V97:Vegan Sphinx
     *    F15[MARS]=0 (Captured)
     *    F108[MARS]=8 (Moved)
     *
     * If initializeDB is true we're initializing the database
     *
     * I've seen 3 things after the world list.
     *      turn 1 uses "=========="
     *      turns 2-3 use "Orders="
     *      The rest use "Players met"
     */

    private fun getWorlds(reader: BufferedReader, initializeDB: Boolean, debugGetWorlds: Boolean = false) {
        while(true) {
            val line = getRecord(reader)
            if(line.startsWith("Players you") ||
                (line.startsWith("Orders="))  ||
                (line.startsWith("=========")))
                return

            worldManager.parseWorld(line, initializeDB, debugGetWorlds) ?: break
        }
    }

    /**
     * Given a player's record extract the name, player type, and all the totals.
     * Look for something like "[MARS]: Pirate"
     */

    private fun getName(record: String, debugNames: Boolean = false) {
        if (debugNames) println("getName: record $record")

        val twoParts = record.split("(", limit = 2)
        val nameAndType = parsePlayerName(twoParts[0])
        myNames[nameAndType.first] = nameAndType.second

        if(twoParts.size > 1) {
            // Now get everything else.  Note twoParts[1] ends with an unpaired ')'
            val scanner = Scanner(twoParts[1]).useDelimiter(",")
            while (scanner.hasNext()) {
                val token = scanner.next().trim()
                if (debugNames) println("getNames: token $token)")
                when {
                    // @formatter:off
                    token.startsWith("Worlds=")        -> totalWorlds   += simpleInt(token)
                    token.startsWith("Keys=")          -> totalKeys     += simpleInt(token)
                    token.startsWith("Ships=")         -> totalShips    += simpleInt(token)
                    token.startsWith("Mines=")         -> totalMines    += simpleInt(token)
                    token.startsWith("Industry=")      -> totalIndustry += simpleInt(token)
                    // And a bunch 'o crap I don't care about
                    token.startsWith("Score=")         -> { /* ignore */ }
                    token.startsWith("Gifts=")         -> { /* ignore */ }
                    token.startsWith("People=")        -> { /* ignore */ }
                    token.startsWith("Converts=")      -> { /* ignore */ }
                    token.startsWith("Artifacts=")     -> { /* ignore */ }
                    token.startsWith("Ally/Loader=")   -> { /* ignore */ }
                    token.startsWith("Met=")           -> { /* ignore */ }
                    token.startsWith("Robots=")        -> { /* ignore */ }
                    else                                       -> { println("unknown token $token") }
                    // @formatter:on
                }
            }
        }
    }

    /**
     *  Input is something like "[CERES]: Pirate", extract the player name and type
     *  and add them to the map.  Unfortunately there is no comma between the
     *  player type and the next word and spaces have been removed, so the type
     *  ends up for example PirateScore=56.
     *
     *  Looks like the extra word is always either "Score=" or "Worlds=".
     *  TODO: fix in a generic way
     */
    private val nameRegex = Regex("""^\[([A-Z]+)]: ?([A-Za-z]+)""")
    private fun parsePlayerName(line: String): Pair <String, String> {
        val fudgeIt = line.replace("Score=", " ")
                                  .replace("Worlds=", " ")
        val match = nameRegex.find(fudgeIt) ?: run {
            println("Error parsing player name: $line")
            exitProcess(-2)
        }
        val(player, type) = match.destructured
        return Pair(player, type)
    }

    /**
     * Given a line like "Game SW-BM/123, Turn 3, [CERES]" extract game name and
     * turn number.
     *
     * If the Game and Turn line isn't found returns emptyGame
     *
     * returns Pair<Game ID, turn>
     */

    private fun getGameInfo(line: String, debugGame: Boolean = false) : GameInfo {
        if (debugGame) println("getGameInfo: line = $line")

        val gamePattern = Regex("Game\\s+(\\S+),.+Turn\\s+(\\S+)")
        val match = gamePattern.find(line) ?: run {
            println("getGameInfo: Malformed input $line")
            return GameInfo()
            //exitProcess(-2)        // should I exit() instead?
        }
        val(id, turn) = match.destructured
        return GameInfo(id, turn.filter {it.isDigit() }.toInt())
    }

    /**
     * Read a record.  Records are groups of lines separated by a blank line.
     * Returned strings have leading and trailing whitespace removed
     *
     * Files from FBI have 0xc2 0xa0 in them, which is a Unicode non breakable
     * space (&nbsp). I can't figure out how to get my scanner to deal with them,
     * so strip them out.
     *
     * returns an empty string on EOF
     * Otherwise returns the record
     */

    private fun getRecord(source: BufferedReader, debug: Boolean = false): String {
        val lines = mutableListOf<String>()
        var line = source.readLine()

        // Skip leading empty lines
        while (line != null && line.isBlank()) {
            line = source.readLine()
        }

        // Read until a blank line or EOF is encountered
        while (line != null && line.isNotBlank()) {
            lines.add(line)
            line = source.readLine()
        }
        if (lines.isEmpty()) return ""

        var recordText = lines.joinToString(" ")
            .replace('\u00a0', ' ') // Strip non-breaking spaces

        // Handle ignorable Unicode byte-order markers cleanly
        recordText = recordText.removePrefix("\uFEFF").trim()

        if (debug) println("getRecord: returning $recordText")
        return recordText
    }

    /**
     * Read the database, parse the turn, save the new database and a world list.
     */
    fun updateDatabase(dataIn: String, turnIn: String, dataOut: String) {
        parseDatabase(dataIn)
        parseTurn(   turnIn)
        saveDatabase( dataOut)
    }
    // Turns out that "[foo]" is a kdoc link that gives "Cannot resolve symbol 'foo'".
    // Supposedly wrapping it in backticks (`) fixes that, but it no workee.
    // I don't want to change my example text, nor can I figure out how to disable
    // the message.
    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/35623642035090-Disable-markdown-link-warnings
    // So, here are some links
    @Suppress
    private val ENIF  = "this"
    @Suppress
    private val foo   = "is"
    @Suppress
    private val CERES = "so"
    @Suppress
    private val MARS  = "stupid"

} // End of class

/**
 * The current working directory is different between IntelliJ and the executable.
 * When running from the command line the current working directory is "."
 * When running from IntelliJ it's "src"
 *
 * There's probably a configuration in IntellJ for this.....
 * @param file I use the turn file.
 */

private fun getPrefix(file: String, debug: Boolean = false) : String {
    if(debug) println("Working Directory: ${System.getProperty("user.dir")}")
    val prefixList = listOf(".", "src")
    for(prefix in prefixList) {
        if(File("$prefix/$file").exists())
            return prefix
    }

    println("Can't find file $file from dirctory ${System.getProperty("user.dir")}")
    exitProcess(-1)
}


private fun helpMe() {
    // TODO: work on this message
    println("""
        Keeps a database of worlds visited with the first and last turn seen.
        Tells you which worlds changed owners.
        For mapping, if worlds A and B connect to C, but you can't see C,
        it shows C connects to A and B
        Take an input database (output of this program for previous turns) and
        a turn name.  Results are stored in a file.
        
        Arguments:
          -t   turn name,
          -d   database to use, default Database.txt
          -o   output to use,   default database
          
        How I run it:
        Before running for the first time delete your database ("Database.txt")
        Then, for every turn:
            SwParser.jar -t "turn name"
   """)
}

private fun getCommandLineArguments(args: Array<String>,
                                    options: MutableMap<String, String>,
                                    debug: Boolean = false) {

    if (debug) {
        println("args: <${args.contentToString()}>")
        //print("args: " + args + "\n")
        println("options: $options")
    }

    var flag: Char
    val g = GetOpt(args, "ht:d:o:")
    g.optErr = true
    while ((g.getopt().also { flag = it.toChar() }) != -1) {
        when (flag) {
            't' -> options["turnName"]    = g.optArgGet()
            'd' -> options["database"]    = g.optArgGet()
            'o' -> options["output"]      = g.optArgGet()
            'h' -> {
                helpMe()
                exitProcess(0)
            }
            else -> {
                helpMe()
                exitProcess(-1)
            }
        }
    }

    if(options.getValue("turnName").isEmpty()) {
        println("You must provide an input file (-t)\n")
        helpMe()
        exitProcess(-2)
    }

    if(options.getValue("database").isEmpty())
        options["database"] = "Database.txt"

    if(options.getValue("output").isEmpty())
        options["output"] = options.getValue("database")


    if (debug) println("parse results: $options")
}

fun main(args: Array<String>) {
    var prefix: String
    var databaseIn: String
    var turnIn: String
    var resultsFile: String

    // Test cases I run:
    //
    //      turnIn = "testData/turnin.txt"
    //      databaseIn = "testData/dbin.txt"
    //      resultsFile = "results.txt"
    //      diff -b results.txt testData/expectedResults.txt
    //
    //      This one isn't included for release because it's my current game.
    //      turnIn = "testData/_turnin.txt"
    //      databaseIn = "testData/_dbin.txt"
    //      resultsFile = "results.txt"
    //      diff -b results.txt testData/_results.txt

    val options = mutableMapOf(
        "turnName"    to "",
        "database"    to "",
        "output"      to "",
    )

    getCommandLineArguments(args, options)

    prefix      = getPrefix(options.getValue("turnName"))
    turnIn      = "$prefix/" + options.getValue("turnName")
    databaseIn  = "$prefix/" + options.getValue("database")
    resultsFile = "$prefix/" + options.getValue("output")

    val sp = SwParser()
    println("Version ${sp.getVersionString()}")
    sp.updateDatabase(databaseIn, turnIn, resultsFile)
}
