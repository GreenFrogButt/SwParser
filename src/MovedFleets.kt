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

        return (id == other.id && owner == other.owner && destination == other.destination)
    }

    override fun hashCode(): Int {
        return Objects.hash(id, owner, source, destination)
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

/**
 * Parse a single fleet and it's destination.
 *
 * @return MovedFleet, or null on error
 */

class MovedFleets {
    internal fun parseSingleMovedFleet(location:Int, token: String): MovedFleet? {
        try {
            val fields = token.split("[", "]-->W")
            val id = fields[0].trim().substring(1).toInt()
            val owner = fields[1]
            val destination = fields[2].trim().toInt()
            return MovedFleet(id, owner, location, destination)
        } catch(e: Exception) {
            println("parseSingleMovedFleet exception: ${e.message}")
            return null
        }
    }

    /**
     * Parses a line of moved fleets.  Example:
     *      "(F3[NEPTUNE]-->W241 F16[NEPTUNE]-->W241)"
     *
     * @param location World ID the fleet is seen at
     * @scanner scanner.next() will return '(F\d+'.
     * @return list of fleets that moved
     */

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

private fun simpleTest(verbose: Boolean = false) {
    data class SimpleData(val pass: Boolean, val input: String)
    val tests = listOf(
        SimpleData(true, "F34[HATYSA]-->W9"),
        SimpleData(true, "F3[NEPTUNE]-->W241"),
        SimpleData(false, ""),                                         // no fleet provided
        SimpleData(false, "(F34[HATYSA]-->W9)"),                       // parens not removed
        SimpleData(false, "F2[NEPTUNE]-->W241 F17[NEPTUNE]-->W142"),   // more than 1 fleet
    )

    if(verbose) println("-".repeat(30) + " simpleTest " + "-".repeat(30))

    val mf = MovedFleets()
    var pass = true
    for(test in tests) {
        val moved = mf.parseSingleMovedFleet(42, test.input)
        if(moved == null) {
            if(test.pass)
                pass = false        // test failed when it was supposed to pass
        } else {
            val success = test.input == moved.toString()
            if (!success)
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

    println("----------------- testMF -----------------------")

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
    println("-".repeat(30) + " testMF $result " + "-".repeat(30))
}

fun main(args: Array<String>) {
    if(args.isEmpty()) {
        simpleTest()
        testMF()
    }
}
