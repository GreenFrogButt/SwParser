import Utilities.addLineBreaks
import Utilities.getOwner
import Utilities.simpleInt
import java.io.PrintStream
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.mutableMapOf

/**
 * Holy cow, it's a class now!  In Kotlin no less!!
 *
 * Started from my 3rd Java program I started some 10-15 years ago.
 */

// W16 (78,152,210) [] (B*L*A*C*K H*O*L*E)
// The parser strips the space, but I want the space when I print it.
private const val blackHoleStringParse:String = """B*L*A*C*KH*O*L*E"""
private const val blackHoleStringPrint:String = "B*L*A*C*K H*O*L*E"


data class World (
    var number: Int = 0,     // world number
    var first: Int = 0,      // turn world first seen
    var last: Int = 0,      // last turn world was seen
    var connections: MutableSet<Int> = TreeSet(),
    var owner: String = "",
    var convertOwner: String = "",
    var converts: Int = 0,
    var robots: Int = 0,
    var industry: Int = 0,
    var usable: Int = 0,
    var metal: Int = 0,
    var mines: Int = 0,
    var pop: Int = 0,
    var turns: Int = 0,
    var limit: Int = 0,
    var iships: Int = 0,
    var pships: Int = 0,
    var plunder: String = "",
    var cg: Int = 0,
    var busted: Boolean = false,
    var deaths: String = "",
    var blackHole: Boolean = false,
    var artifacts: List<Artifact> = listOf(),
    var fleets: List<Fleet> = listOf(),
    var departed: List<MovedFleet> = listOf()
) {
    /*
     * I'm testing by running this against the Java program's output.  There is
     * some crap making this output look like the Java output that I'd like to fix
     * some day.  I labeled this javaBuffoonary
     *
     * The mismatches left that differ between the owner being null or "" when the world
     * is in the database but not the turn (W165 and W212 in my test cases)  is due to
     * a bug in the Java version I don't care to replicate.
     */
    enum class JbFlag {
        LAST_EQUALS_ZERO,
        DEFAULT_OWNER_NULL,
        PRINT_USABLE_INDUSTRY,
        USE_POP_LIMIT,
    }

    val javabuffoonary = EnumSet.of(
        JbFlag.DEFAULT_OWNER_NULL,
        JbFlag.PRINT_USABLE_INDUSTRY,
        JbFlag.LAST_EQUALS_ZERO,
        JbFlag.USE_POP_LIMIT,
    )


    init {
        //@Suppress("SENSELESS_COMPARISON")
        if(JbFlag.DEFAULT_OWNER_NULL in javabuffoonary)
            owner = "null"
    }


    /**
     * Return the world as a String without fleet or artifact info.
     */
    private fun printWorld(): String = buildString {
        append("W$number ")
        append(connections.joinToString(separator = ",", prefix = "(", postfix = ")"))

        @Suppress("SENSELESS_COMPARISON")
        if (JbFlag.USE_POP_LIMIT in javabuffoonary && (limit == 0) ){
            // TODO:  If this works think of other times limit could be 0.
            //        Like BUSTED worlds.
            append(" [null] ")
        } else
            append(" [$owner] ")

        if (convertOwner.isNotEmpty()) append("C[$convertOwner] ")
        append("(First=$first")

        // use last >= for regression testing, change to last > 0 when done.
        if (JbFlag.LAST_EQUALS_ZERO in javabuffoonary || (last > 0) )
            append(",Last=$last")

        if (blackHole) append("," + blackHoleStringPrint)
        else {
            if (busted) append(",BUSTED")
            if (industry > 0) {
                append(",Industry=$industry")
                @Suppress("SENSELESS_COMPARISON")
                if (JbFlag.PRINT_USABLE_INDUSTRY in javabuffoonary)
                    if (usable != industry) append("/$usable")
            }
            if (metal > 0) append(",Metal=$metal")
            if (mines > 0) append(",Mines=$mines")

            if (pop + converts + robots > 0) {
                append(",Population=")

                // Population is a bit trickey
                //      pop   robots   converts   result
                //      34      0       0           34
                //      87      0       9           87/9C
                //      0       6       0           6R
                //      0       0       127         127C

                if (pop > 0) {
                    append("$pop")
                    if (converts > 0) append("/${converts}C")
                } else if (robots > 0) append("${robots}R")
                else if (converts > 0) append("${converts}C")
            }

            if (limit > 0) append(",Limit=$limit")
            if (turns > 0) append(",Turns=$turns")
            if (iships > 0) append(",I-Ships=$iships")
            if (pships > 0) append(",P-Ships=$pships")
            if (plunder.isNotEmpty()) append(",Plunder=$plunder")
            if (cg > 0) append(",CG-Unload=$cg")

        }
        append(")${System.lineSeparator()}")
    }

    /**
     * Return the world only, with lines less than 77 chars.
     */
    //override fun toString(): String {
    override fun toString(): String {
        val world = printWorld()
        return addLineBreaks(world)
    }

    // Print the world with it's fleet and artifact info
    // I don't like the way this looks but I've wasted enough time on it.
    fun printEverything(): String {
        val result = StringBuilder(addLineBreaks(printWorld()))

        // Artifacts get printed right after the world, one after another
        if(artifacts.isNotEmpty()) {
            val arts = ArtifactManager(artifacts)

            // The 2 spaces are lame-ass formatting
            val artString = "  $arts"
            val artFormatted = addLineBreaks(artString, indent = 2, delimiter = "V")
            result.append(artFormatted + "\n")
        }

        // Fleets get printed on their own lines
        for (fleet in fleets)
            result.append("    $fleet\n")

        // Leaving fleets are all on 1 line
        if (departed.isNotEmpty()) {
            // Notice the space acting as some lame ass formatting
            val leaving = departed.joinToString(separator = " ", prefix = " (", postfix = ")")
            result.append(leaving)
        }
        return result.toString()
    }
}

/**
 * Tools for parsing a world and maintaining a database of worlds seen
 * If a FleetManager isn't provided then every world will have it's own
 * fleet and artifact manager, which isn't what I want.  Or I could make
 * WorldManager a singleton, which I might do.
 */
class WorldManager(private val fleetManager: FleetManager) {
    private var turnNumber = 0
    private val artifactManager = fleetManager.artifactManager
    private var worldList = mutableMapOf<Int, World>()
    private var changedOwners = mutableListOf<ChangedOwner>()
    private var totalMetal = 0

    // I play multi which means I have 3 names.  These are those names.
    private var myNames = mapOf<String, String>()   // map name to player type
    fun setMyNames(names: MutableMap<String, String>) {
        myNames = names.toMap()
    }

    /**
     * Set the turn number.  This class doesn't really know anything about turns.
     * @param number The turn number
     */
    fun setTurnNumber(number: Int) { turnNumber = number }

    /**
     * Clear the list of changed owners
     */
    fun clearChangedOwners() { changedOwners.clear() }

    /**
     * Get the list of changed owners
     */
    fun getChangedOwners(): List<ChangedOwner> { return changedOwners.toList() }

    /**
     * Print all worlds in the database to stdout.  Does not print artifacts,
     * fleets, nor moving fleets
     *
     * TODO: Had a game where some worlds only had 1 connection.  Dig it up and
     *      fix the "connectoins.size > 1" line.
     */
    fun printWorlds(destination: PrintStream) {
        for(world in worldList.toSortedMap().values) {
            if(world.connections.size > 1)
                destination.println(world)
        }
    }

    /**
     * Set total metal to 0.  Don't want the metal in the database included in
     * the total but we have no way of knowing if we're initialiazing the
     * database or parsing a turn.
     */
    fun clearTotalMetal() { totalMetal = 0 }

    /**
     * Get how much metal is available.  Metal is available if it's on one of
     * my worlds, not on a homeworld, and has more metal than industry
     */
    fun getTotalMetal(): Int { return totalMetal }

    /**
     * Given a world's ID, return the world.
     *
     * @param id    which world is asked for
     * @return the world, or null there is no information on the world.
     */

    fun getWorldById(id: Int) : World? = worldList[id]

    /**
     * Given a world's data in a record, parse it and update worldList.
     *
     * From the rulebook, the printout of a world is:
     *
     *  1)  World Number
     *  2)  List of Connections
     *  3)  Owner of this world, [] if no owner
     *  4)  (if applicable) Owner of converts: C[NAME]
     *  5)  The number of industry, metal, mines, population, population limit,
     *      Iships, and Pships at the world. (If industry is two numbers with
     *      a "/", then the first number is actual industry, and the second
     *      number is the amount of industry usable this turn. If population
     *      is 25/5C for instance, that means that there are 25 population, of
     *      which 5 are converts. If population is followed by an "R", that
     *      means they are robots.)
     *  6)  If a PBB has been dropped at this world during the game so far,
     *      "BUSTED" will appear. If population (not robots) has died,
     *      "DEATHS=xx" will appear. If "Deaths" has two numbers, the one
     *      after the "/" is how many converts died. "Turns=" is the Turns
     *      number. If the world has been plundered so far this game, the
     *      word "Plunder=x" will appear with "x" being the number of times
     *      it has been plundered. If there are two numbers, the one after
     *      the "/" is how many turns before the world recovers from the
     *      plunder. "CG-Unloaded=x" tells you how many times Consumer Goods
     *      have been unloaded on this world. If the world was captured this
     *      turn, you are told who lost it and who captured it.
     *
     *  Turns out save/load is trivial if I print the output of this program
     *  to look like a normal turn and use parseWorld to read that save
     *  file.  Which means I have to look for the Last= field, which I
     *  added and is not in the rulebook.
     *
     *  Useful to know the first time a world was seen, so now we have a
     *  First= field.  This is also not in the rulebook.
     *
     * @param input: A string containing the data to parse.  Ex:
     *      "W78 (16,21,193) [CERES] (Metal=1,Mines=1,Population=17,Limit=48,Turns=2)"
     * @param initializeDB: true if we're reading the database file
     * @return the world parsed, or null on error
     */

    fun parseWorld(input: String, initializeDB: Boolean, debug: Boolean = false): World? {
        if (debug) println("parseWorld: $input")
        if(input.isEmpty()) return null

        // There is a problem when a ship ambushes, "I-Ships=4(Ambush)" means
        // I can have nested parens.  I can either write a regex to look for
        // nested parens, or just remove the stupid things. This will bite me in
        // the ass someday....
        // Uh oh, "I-Ships=0(AF137))" is a thing.  Need a generic solution.
        // TODO: What does it look like when home ships shoot converts?
        val scanner = Scanner(input.replace("(Ambush)", "")
                                    .replace("\\([AC]F\\d+\\)".toRegex(), "")
            )

        val worldNumber = getWorldID(scanner.next(), false)
        if (worldNumber == 0)
            return null

        parseConnections(worldList, worldNumber, scanner.next(), false)

        val world = worldList.getOrElse(worldNumber) { World() }

        // If the world gets PBB'd everything goes to 0, but they aren't
        // shown on the printout.  Set to 0 now, then if the fields are
        // encountered on the turn they'll be updated to the correct values.
        // TODO: Look for "BUSTED" -> W158 (95,219,246) [] (Lost by [FAFNIR],BUSTED,Population=0,Deaths=100)
        //       Will also need to modify fleets.kt to look for "F213[EARTH]=126 (D)"

        if(!initializeDB) {
            if (world.first == 0) world.first = turnNumber
            world.converts = 0
            world.convertOwner = ""
            world.robots = 0
            world.industry = 0
            world.usable = 0
            world.metal = 0
            world.mines = 0
            world.pop = 0
            world.turns = 0
            world.limit = 0
            world.iships = 0
            world.pships = 0
            world.deaths = ""
            world.plunder = ""
            // blackHole nor busted get reset.  Should they?
        }

        val owner = getOwner(scanner.next())
        if (world.owner != owner) {
            changedOwners.add(ChangedOwner(worldNumber, world.owner, owner))
            world.owner = owner
        }

        // Get the convert's owner, if any.
        if (scanner.hasNext(Pattern.compile("C\\[\\S+]"))) {
            val token = scanner.next()
            if (debug) println("parseWorld: found converts token $token")
            world.convertOwner = token.substring(2, token.length - 1)
        }

        // Now get the body, which is everything between the parens
        scanner.useDelimiter("""\)""")
        val body = scanner.next() + ")"   // scanner.next() doesn't include matching ')'
        parseBody(world, body)

        // Clear out the last paren
        scanner.useDelimiter("\\s+")
        if (scanner.hasNext("[)]")) {
            scanner.next()
        }

        world.artifacts = artifactManager.parseArtifactList(scanner, LocationType.WORLD, world.number)
        world.fleets = fleetManager.parseFleetList(world.number, scanner)
        if(debug) println("World $world")
        world.departed = parseDepartedFleets(world.number, scanner)

        // Don't update 'last time seen' when initializing the database
        if (!initializeDB) {
            world.last = turnNumber

            // This was tricky, how it works:
            //     1.  When initializing the database set this to 0
            //     2.  While initializing the database if First= is found this
            //         gets set
            //     3.  When parsing the turn, when a new world is found this
            //         field is still 0.
            //     4.  When done parsing the world, if this is 0 set it to turn.
            if (world.first == 0) world.first = turnNumber
        }

        worldList[world.number] = world
        return world
    }

    /**
     * Extract the world for this line.
     *
     * Returns either a world 1..255, or 0 on error
     */
    fun getWorldID(input: String, debug: Boolean = false): Int {
        if (debug) println("getWorldID: input $input")

        var id: Int
        val pattern = Regex("""W([0-9]+)""")
        val result = pattern.find(input)
        id = result?.groups[1]?.value?.toInt() ?: 0
        if (id == 0)
            println("getWorldID: '$input' is not a valid world")

        return id
    }

    /**
     * Fill in the connections: "(13,176,223,251)"  This has to work on worldList
     * because if 3 connects to 8, then 8 also connects to 3.
     *
     * TODO: Sometimes the Black Box will make a temporary connection between worlds,
     *       sometimes it's a 1 way link.  Need to engage my thinky bits on this.
     */

    internal fun parseConnections(
        allWorlds: MutableMap<Int, World>,
        worldNumber : Int,
        input: String,
        debug: Boolean = false
    ) {
        if (debug) println("parseConnections: world = $worldNumber, input = $input")

        val connections = input.substring(1, input.length - 1)
        val scanner = Scanner(connections).useDelimiter((","))

        val world = allWorlds.getOrElse(worldNumber) { World(worldNumber) }

        while (scanner.hasNextInt()) {
            val connectNumber = scanner.nextInt()
            if (debug) println("parseConnections: Got connection $connectNumber")

            // This block can throw an exception, when (if) it does I'll fix it.
            world.connections.add(connectNumber)
            val connectWorld = allWorlds.getOrElse(connectNumber) { World(connectNumber) }
            connectWorld.connections.add(worldNumber)
            if(!allWorlds.contains(connectNumber))
                allWorlds[connectNumber] = connectWorld
        }
        allWorlds[worldNumber] = world
    }

    // Iships and Pships (home ships) can have "P-Ships=3(Ambush).  Just return
    // the value.
    private fun homeShips(input: String): Int {
        val value = input.substringAfter("=").substringBefore("(")
        return value.toIntOrNull() ?: run {
            println("homeShips error with $input")
            -1
        }
    }

    /**
     *  Parse the world body, it looks something like
     *  (First=1,Last=10,Industry=30,Metal=31,Mines=3, Population=100,Limit=100,Turns=3)
     */

    private fun parseBody(world: World, input: String, debug: Boolean = false) {
        if (debug) println("parseBody: $input")

        // Strip enclosing parens and all whitespace
        val line = input.substring(2, input.length - 1).filterNot { it.isWhitespace() }
        val scanner = Scanner(line).useDelimiter(",")

        while (scanner.hasNext()) {
            val token = scanner.next()
            if (debug) println("parseBody: token $token)")

            when {
                // @formatter:off   Turn IntelliJ's formatter off for a bit
                token.startsWith("First=")     ->    world.first = simpleInt(token)
                token.startsWith("Last=")      ->    world.last = simpleInt(token)
                token.startsWith("Industry=")  ->    parseIndustry(world, token)
                token.startsWith("Mines=")     ->    world.mines = simpleInt(token)
                token.startsWith("Metal=")     ->    world.metal = simpleInt(token)
                token.startsWith("Population=")->    parsePop(world, token)
                token.startsWith("Turns=")     ->    world.turns = simpleInt(token)
                token.startsWith("Limit=")     ->    world.limit = simpleInt(token)
                token.startsWith("I-Ships=")   ->    world.iships = homeShips(token)
                token.startsWith("P-Ships=")   ->    world.pships = homeShips(token)
                token.startsWith("Plunder=")   ->    parsePlunder(world, token)
                token.startsWith("CG-Unload=") ->    world.cg = simpleInt(token)
                token.startsWith("BUSTED")     ->    world.busted = true
                token.startsWith("Deaths")     ->    world.deaths = token
                token.startsWith(blackHoleStringParse) ->   world.blackHole = true
                token.startsWith("Captured")   ->   { /* ignore */ }
                token.startsWith("Lostby")     ->   { /* ignore */ }
                // I need to make this stand out, yet I don't want to exitProcess().  Hmmm
                else -> println("parseBody: unknown token $token")
                // @formatter:on
            }
        }

        // Be nice if I could limit this to worlds with 3 of a homeworld.  Wonder
        // if I still have my graph theory textbook?
        if ((world.industry < 30) &&                // it's not a homeworld
            (myNames.contains(world.owner)) &&      // it's my world
            (world.metal > world.industry)          // it's got surplus metal
        ) {
            totalMetal += world.metal - world.industry
        }

        if(world.converts == 0) world.convertOwner = ""
    }

    /**
     * Parse the list of departed fleets.
     *    "(F19[MARS]-->W13)" or
     *    "(F19[MARS]-->W13 F45[MARS]-->W13 F68[HATYSA]-->W13 F222[HATYSA]-->W13)"
     */

    fun parseDepartedFleets(currentWorld: Int, scannerIn: Scanner, debug: Boolean = false) : List<MovedFleet> {
        val leavingFleets = mutableListOf<MovedFleet>()

        if(scannerIn.hasNext()) {
            // Grab the rest of the record and strip the surrounding parens.
            val line = scannerIn.nextLine()
            val leaving = line.replace(Regex("[()]"), "").trim()
            if(debug) {
                println("parseDepartedFleets: input $line")
                println("                no parens: $leaving")
            }

            val scanner = Scanner(leaving)
            while(scanner.hasNext()) {
                val data = scanner.next().split(Regex("^F|\\[|]-->W"))
                val mf = MovedFleet(data[1].toInt(), data[2], currentWorld, data[3].toInt())
                leavingFleets.add(mf)
            }
        }
        return leavingFleets.toList()
    }


    // Parse a token like "Plunder=3" or "Plunder=1/2".
    // For now just keep it as a string.
    private fun parsePlunder(world: World, line: String) {
        val count = line.split('=')
        world.plunder = count[1]
    }

    /**
     *  Parse "Industry=30" or "Industry=30/2" and set the world's industry and
     *  usable. If all industry is usable then usable == industry, otherwise
     *  usable < industry.
     */
    private fun parseIndustry(world: World, line: String) {
        val chunks = line.substringAfter("=").split("/")
        world.industry = chunks[0].toInt()
        world.usable = if (chunks.size > 1) chunks[1].toInt() else world.industry
    }

    /**
     * Parse a population string.  Possibilities
     *      34      34 pop
     *      127C    127 converts
     *      6R      6 Robots
     *      87/9C   87 pop, 9 converts.
     *
     *      I could easily make this also handle "Industry=30/2"
     */

    // This catches all 4 cases
    //  (\d+)$                34
    //  (\d+)C$               127C
    //  (\d+)R$               6R
    //  (\d+)/(\d+)C$ 87/9C   87/9C
    //
    //or one readable regex:
    //
    //  ^(\d+)(?:/(\d+)C|([CR]))?$
    // Figure out how to use it in tokenPatt

    private val tokenPatt = Regex(
        "(\\d+)"            // 1 or more digits
                + "(?:([/CR])"       // Optional '/', 'C', or 'R'
                + "(?:(\\d+)"        // optional more digits
                // This gives "Unnecessary non-capturing group" when it works.
                // All my attempts at fixing it makes it not work.
                + "(?:(C)"          // optional trailing C
                + ")?)?)?"          // close optional clauses
    )

    private fun parsePop(world: World, data: String, debug: Boolean = false) {
        if (debug) println("parsePop: world $world, data $data")

        val matchResult = tokenPatt.find(data) ?: run {
            println("No population found in token $data")
            return
        }

        // Currently will throw an exception on syntax error, nor will it notice
        // the trailing 'C' is missing.
        // TODO: fix
        val (populationStr, delimiterStr, secondNumberStr, _) = matchResult.destructured
        val population = populationStr.toInt()

        world.pop = population    // Assume all normal pop
        world.converts = 0
        world.robots = 0

        if(delimiterStr.isNotEmpty()) {
            when(delimiterStr) {
                ""  -> {}
                "R" -> { world.pop = 0; world.robots = population }
                "C" -> { world.pop = 0; world.converts = population }
                "/" -> { world.converts = secondNumberStr.toInt() }
                else -> {println("parsePop: Syntax error with $data") }
            }
        }
    }

    // See SwParser.kt for the explanation, search for 'stupid'.
    @Suppress
    private val NAME    = "this"
    @Suppress
    private val CERES   = "is not"
    @Suppress
    private val MARS    = "a"
    @Suppress
    private val HATYSA  = "link"
} // end of WorldManager class

internal fun testWorldParse(verbose: Boolean = false): Boolean {
    // I really need to automate this...  Right now I eyeball it, which only
    // works if I know what I'm looking for.
    //
    // When worlds are printed "Last=" will always be 0.  This is normal in this
    // test as the turnNumber was never set
    //
    // I get a lot of "duplicate artifact" errors that I don't care about.
    println("=========================== testWorldParse ===============================================")
    data class TestType(val runMe: Boolean, val input: String)

    val tests = listOf(
        TestType(false,       // A "clean" world
            "W47 (32,136) [MARS] (Industry=1,Metal=6,Mines=3,Population=42,Limit=85, " +
                    "Turns=6,I-Ships=2,P-Ships=3)"),

        TestType(false,       // some converts
        "W42 (38,236) [MARS] C[IOTA] (Industry=1,Metal=6,Mines=3,Population=42/8C,Limit=85, " +
                "Turns=6,I-Ships=2,P-Ships=3)"),

        TestType(false,       // A 100% converted world with an artifact
    "W9 (58,76,122) [MARS] C[IOTA] (Metal=2,Mines=1,Population=73C,Limit=78,Turns=4,P-Ships=8) V97:Vegan Sphinx)"),

        TestType(true,       // World with an artifact and a fleet with an artifact
            "W47 (32,136) [MARS] (Population=42,Limit=85, Turns=6) V21:Ignoble Reward" +
                    "   F72[BARNEY]=42 (Moved) V36:Stinky Butt"),

        TestType(false,       // Lots of artifacts
    "W19 (48,67,212) [MARS] (First=3,Last=8,Metal=2,Mines=1,Population=73,Limit=78,Turns=4,P-Ships=8) V79:Vegan Throne " +
               "V4:Nebula Scrolls, Vol. IIII  V51:Titanium Throne  V64:Blessed Throne  V27:Ancient Throne  V98:Arcturian Throne "),

        TestType(true,       // PBB dropped
    "W182 (19,122,146) [] (Lost by [KOCHAB],BUSTED,Population=0,Deaths=110C) " +
                "F146[ATRIA]=21 (D,Cargo=21) " +
                "(F180[ATRIA]-->W146)"),

        TestType(false,       // A world with robots and fleets
    "W39 (13,56,163) [HATYSA] (Industry=1/0,Metal=7,Mines=3,Population=94R,Limit=94, " +
           "Turns=6,I-Ships=5) " +
                "F21[CERES]=7 (Moved) " +
                "F26[CERES]=16 (Moved) " +
                "F61[CERES]=12 (Moved) "),

        TestType(true,       // Fleets with artifacts
        "W47 (32,136) [MARS] (Industry=1,Metal=6,Mines=3,Population=42,Limit=85) " +
                "F34[CERES]=23  V16:Platinum Doodie " +
                "F204[CERES]=67  V3:Nebula Scrolls, Vol. III  V26:Titanium Doodie " +
                                "V47:Blessed Moonstone  V73:Ancient Thing  V89:Klingon Stardust "),

        TestType(true,       // A world with moving fleets
            "W3 (13,176,223,251) [HATYSA] (Last=5,Metal=8,Mines=4,Population=115,Limit=115,Turns=5) " +
                    "(F19[MARS]-->W13 " +
                    "F45[MARS]-->W120 F68[HATYSA]-->W155 F222[HATYSA]-->W231 " +
                    "F183[FRODO]-->W381 F190[BILBO]-->W90)"),

        TestType(true,       // A world with everything
            "W20 (163,247,252) [CERES] (First=1,Last=24,Industry=30,Metal=84,Mines=3,Population=100, " +
                   "Limit=100,Turns=6,Plunder=4/2,CG-Unload=1)  V2:Nebula Scrolls, Vol. II  V15:Titanium Crown " +
                   "V46:Blessed Shekel  V72:Ancient Moonstone  V88:Arcturian Stardust " +
                   "F37[CERES]=13 (Moved,Cargo=13) " +
                   "F125[CERES]=32 (Moved)  V41:Platinum Shekel " +
                   "F204[CERES]=67  V3:Nebula Scrolls, Vol. III  V16:Titanium Doodie " +
                       "V47:Blessed Moonstone  V73:Ancient Thing  V89:Klingon Stardust " +
                   "F243[HATYSA]=8 (Moved,Cargo=15) " +
                   "F15[MARS]=2 (Moved,Cargo=2) " +
                   "(F134[HATYSA]-->W247 F141[HATYSA]-->W247 F206[HATYSA]-->W247 " +
                   "F250[CERES]-->W163 F252[HATYSA]-->W247) " )
    )
    val wm = WorldManager(FleetManager())
    for (test in tests) {
        if(!test.runMe)
            continue
        val result = wm.parseWorld(test.input, initializeDB = false, false)
        if(verbose) {
            println("In : ${test.input}\n")
            if(result == null)
                println("Error parsing")
            else {
                //println("printWorld: ${result.printWorld()}")
                //println("toString(): $result")
                println("Result:\n${result.printEverything()}")
                println("            -------")
                println()
            }
        }
    }
    return true     // I really need to automate this.
}

fun testParseDepartedFleets(debug: Boolean = false): Boolean {
    data class TestType(val count: Int, val input: String)

    val tests = listOf(
        TestType(0, ""),
        TestType(1, "(F19[MARS]-->W13)"),
        TestType(5, "(F91[MARS]-->W13 F54[MARS]-->W14 F86[HATYSA]-->W23 F221[HATYSA]-->W213 F119[FRED]-->W87)"),
    )
    var pass = true
    val wm = WorldManager(FleetManager())

    for(test in tests) {
        val result = wm.parseDepartedFleets(42, Scanner(test.input))
        val badCount = test.count != result.size
        if(debug || badCount) {
            println("Input : ${test.input}")
            val foo = result.toString().replace(",", "")
            //println("Output: $result")
            println("Output: $foo")
            if(badCount) {
                println("Expected ${test.count}, got ${result.size}")
                pass = false
            }
        }
    }
    val result = if(pass) "pass" else "fail"
    println("testParseDepartedFleets $result")
    return pass
}

// this should never be called, just killing some 'value is always false'
// type messages.
// Be nice if I knew what values @Suppress() could take.
private fun killWarnings() {
    val wm = WorldManager(FleetManager())
    val invalidWorld = wm.getWorldById(300)
    val invalidID = wm.getWorldID("foo", true)
    val invalidWP = testWorldParse(false)
    if( (invalidWorld != null) || (invalidID == 300) || invalidWP )
            println("that shouldn't happen")
    wm.parseConnections(mutableMapOf(), 301, "foo", true)
}

fun main(args: Array<String>) {
    if(args.size == 42) killWarnings()
    var pass = testParseDepartedFleets(false)
    if(!testWorldParse(true))
        pass = false

    val result = if(pass) "pass" else "fail"
    println("World selftest $result")
}
