import java.util.Objects
import java.util.Scanner

/**
 * Parse fleets that moved from a world.  Examples:
 *     (F34[HATYSA]-->W9)
 * and
 *      (F3[NEPTUNE]-->W241 F16[NEPTUNE]-->W241 F31[NEPTUNE]-->W241
 *      F209[NEPTUNE]-->W214 F212[NEPTUNE]-->W241 F245[NEPTUNE]-->W172
 *      F249[URANUS]-->W241)
 */

data class MovedFleet(
    private var id: Int,
    private var owner: String,
    private var source: Int = 0,        // world where fleet was seen leaving
    private var destination: Int
) {
    override fun equals(other: Any?): Boolean {
        if ((other == null) || (other !is MovedFleet))
            return false

        return (id == other.id)
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun toString(): String {
        return "F$id[$owner]-->W$destination"
    }
    // See SwParser.kt for the explanation, search for "stupid"
    // @Suppress
    // noinspection PrivatePropertyName
    // noinspection HATYSA
    private val HATYSA    = "this"
    @Suppress
    private val NEPTUNE    = "is"
    @Suppress
    private val URANUS     = "stupid"
    @Suppress("unused")
    private val notUsed = "$HATYSA$NEPTUNE$URANUS"
}

class MovedFleets {
    /**
     * Parse a single fleet and it's destination.
     *     "F34[HATYSA]-->W9"
     *
     * @return MovedFleet, or null on error
     */

    internal fun parseSingleMovedFleet(location: Int, token: String): MovedFleet? {
        if (token.isEmpty()) return null
        val mfPattern = Regex("""F(\d+)\[([A-Z]+)\]-->W(\d+)""")
        val match = mfPattern.find(token) ?: run {
            println("parseMovedFleet: Malformed input \"$token\"\n")
            return null
        }

        val (id, owner, destination) = match.destructured
        return MovedFleet(id.toInt(), owner, location, destination.toInt())
    }

    /**
     * Parses a line of moved fleets.  Example:
     *      "(F3[NEPTUNE]-->W241 F16[NEPTUNE]-->W241)"
     *
     * @param location World ID the fleet is seen at
     * @scanner scanner.next() will return '(F\d+'.
     * @return list of fleets that moved
     */

    // TODO: duplicated in World.kt.  Pick one or the other.
    fun parseMovedFleets(location: Int = 0, scanner: Scanner) : List<MovedFleet> {
        val mf = mutableListOf<MovedFleet>()
        if (scanner.hasNext("[(]F\\d+.*")) {
            // The world has moving fleets.  Read in the whole thing, remove the
            // parens, and process each entry.
            scanner.useDelimiter("\\)")
            val theList = scanner.next().substring(1)
            val chunks = theList.split(Regex("\\s+"))
            for(chunk in chunks) {
                val mover = parseSingleMovedFleet(location, chunk)
                if (mover != null)      // If mover is null that's a problem I should handle....
                    mf.add(mover)
            }
        }

        return mf.toList()
    }
    @Suppress
    private val NEPTUNE    = "stupid"
} // end of class

private val dashes = "-".repeat(30)

private fun simpleTest(verbose: Boolean = false) {
    data class SimpleData(val shouldPass: Boolean, val input: String)
    val tests = listOf(
        SimpleData(true, "F34[HATYSA]-->W9"),
        SimpleData(true, "F3[NEPTUNE]-->W241"),
        SimpleData(false, ""),                                         // no fleet provided
        // Changing parseMovedFleet means these tests are now correctly parsed, but the
        // string compare doesn't match.  hmmm.
        SimpleData(false, "(F24[HATYSA]-->W19)"),                       // parens not removed
        SimpleData(false, "F2[NEPTUNE]-->W241 F17[NEPTUNE]-->W142"),   // more than 1 fleet
    )

    println("$dashes simpleTest $dashes")

    val mf = MovedFleets()
    var pass = true
    for(test in tests) {
        if(verbose) println("test: $test")
        val moved = mf.parseSingleMovedFleet(42, test.input)
        if(moved == null) {
            if (test.shouldPass)
                pass = false        // test failed when it was supposed to pass
        } else {
            val success = test.input == moved.toString()
            if (!success && test.shouldPass)
                pass = false
        }
        if(verbose || !pass) {
            println("In : ${test.input}")
            println("Out: $moved")
            if (pass) println("pass") else println("fail")
            println()
        }
    }
    val result = if (pass) "pass" else "fail"
    println("-".repeat(30) + " simpleTest $result " + "-".repeat(30))
}

private fun testMF(verbose: Boolean = false) {
    data class MFTestData(var input: String, var location: Int, var pass: Boolean, var fleets: List<MovedFleet>)
    val tests = listOf(
        MFTestData("(F34[HATYSA]-->W9)", 1, true, listOf(
            MovedFleet(34, "HATYSA", 1, 9))),

        MFTestData("(F2[NEPTUNE]-->W241 F17[MARS]-->W141)", 32, true, listOf(
            MovedFleet(2, "NEPTUNE", 32, 241),
            MovedFleet(17, "MARS", 32, 141))),

        MFTestData("(F3[VENUS]-->W41 F16[JUPITER]-->W141 F31[SATURN]-->W151 " +
                "F209[MOON]-->W224 F212[PLUTO]-->W214 F245[GOOFY]-->W127 " +
                "F249[URANUS]-->W21)", 89, true, listOf(
            MovedFleet(  3, "VENUS", 89, 41),
            MovedFleet( 16, "JUPITER", 89, 141),
            MovedFleet( 31, "SATURN", 89, 151),
            MovedFleet(209, "MOON", 89, 224),
            MovedFleet(212, "PLUTO", 89, 214),
            MovedFleet(245, "GOOFY", 89, 127),
            MovedFleet(249, "URANUS", 32, 21))),

        MFTestData("", 10, false, listOf()),    // No input
        MFTestData("F34[FAIL]-->W-1", 10, false, listOf()),    // no parans
    )

    println("$dashes testMF $dashes")

    val mf = MovedFleets()
    var pass = true
    for (test in tests) {
        var reason = ""
        val scanner = Scanner(test.input)
        val moved = mf.parseMovedFleets(test.location, scanner)

        if ( (moved.size != test.fleets.size) && test.pass) {
            pass = false
            reason = String.format("expected ${test.fleets.size} fleets, got ${moved.size}")
        } else {
            for(fleet in test.fleets) {
                if(!moved.contains(fleet)) {
                    pass = false
                    reason = "fleets don't match"
                }
            }
        }

        if (verbose || reason.isNotEmpty()) {
            println("In      : ${test.input}")
            println("Expected: ${test.fleets}")
            println("Got     : $moved")
            if (reason.isEmpty())
                println("pass")
            else {
                println(reason)
                println("fail")
            }
        }
    }

    val result = if (pass) "pass" else "fail"
    println("$dashes testMF $result $dashes")
}

fun main(args: Array<String>) {
    if(args.isEmpty()) {
        simpleTest()
        testMF()
    }
}
