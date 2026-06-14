import Utilities.addLineBreaks
import Utilities.getOwner
import Utilities.hasMatch
import Utilities.simpleInt
import java.util.*
import java.util.regex.Pattern


data class Fleet(
    var id: Int = 0,
    var owner: String = "Unknown",
    var size: Int = 0,
    var location: Int = 0,
    var moved: Boolean = false,
    var cargo: Int = 0,
    var giftee: String = "",
    var atPeace:Boolean = false,
    var captured:Boolean = false,
    var target:String = "",        // AH, AP, AF200, etc
    var lostBy: String = "",
    var droppedPBB: Boolean = false,
    var builtPBB: Boolean = false,
    var hasPBB: Boolean = false,
    var artifacts: List<Artifact> = listOf(),
    var fleetsLeaving: List<Fleet> = listOf()
) {
    // Print fleet as shown on Starweb turns
    override fun toString(): String {
        val sb = StringBuilder("F$id[$owner]=$size")

        val fleetStuff = StringBuilder()

        // @formatter:off
        if(moved)               fleetStuff.append("Moved,")
        if(cargo > 0)           fleetStuff.append("Cargo=$cargo,")
        if(atPeace)             fleetStuff.append("At-Peace,")
        if(giftee.isNotEmpty()) fleetStuff.append("Gift from [$giftee],")
        if(captured)            fleetStuff.append("Captured,")
        if(lostBy.isNotEmpty()) fleetStuff.append("Lost by [$lostBy],")
        if(target.isNotEmpty()) fleetStuff.append("$target,")
        if(droppedPBB)          fleetStuff.append("(D)")
        if(hasPBB) {
            fleetStuff.append("PLANET-BUSTER")
            if(builtPBB)
                fleetStuff.append("(Built)")
            fleetStuff.append(",")
        }
        // @formatter:on

        // Strip trailing comma, surround with parens
        if(fleetStuff.isNotEmpty())
            sb.append(" (" + fleetStuff.substring(0, fleetStuff.length - 1) + ")")

        if (artifacts.isNotEmpty()) {
            val formatLines = false
            if(formatLines) {
                // I can't make this look good, wasted enough time on it
                val artifactString = StringBuilder(artifacts.toString())
                val maxLength = 77  // TODO: same value used in addLineBreaks()
                val indent = 6
                sb.append("\n" + " ".repeat(indent) +
                    addLineBreaks(artifactString.toString(), maximumLength = maxLength, delimiter = "V", indent = indent))
            } else {
                for (artifact in artifacts) {
                    sb.append(" $artifact")
                }
            }
        }

        return (sb.toString())
    }

    // Print everything known about the fleet
    fun full(): String {
        val sb = StringBuilder(toString())
        sb.append(" is at W$location")
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if( (other == null) || (other !is Fleet) )
            return false

        // @formatter:off
        return( (id            == other.id) ||
                (owner         == other.owner) ||
                (size          == other.size) ||
                (location      == other.location) ||
                (moved         == other.moved) ||
                (cargo         == other.cargo) ||
                (giftee        == other.giftee) ||
                (atPeace       == other.atPeace) ||
                (captured      == other.captured) ||
                (target        == other.target) ||
                (lostBy        == other.lostBy) ||
                (artifacts     == other.artifacts) )
        // @formatter:oon
    }

    override fun hashCode(): Int {
        println("hashCode")
        return Objects.hash(
        id,
                owner,
                size,
                location,
                moved,
                cargo,
                giftee,
                atPeace,
                captured,
                target,
                lostBy,
                artifacts)
    }
}

class FleetManager(val artifactManager: ArtifactManager = ArtifactManager()) {
	private val allFleets = mutableListOf<Fleet>()
    fun getAllFleets() : List<Fleet> { return allFleets.toList() }

    /**
     * Parse a single fleet.  Keep in mind this program sees a world as a single
     * long line, not a bunch of short lines like the turnsheet.
     *
     * Sample inputs:
     *    F3[]=4
     *    F16[HATYSA]=6
     *    F25[CERES]=32 V88:Arcturian Stardust
     *    F72[MARS]=12 (Moved)
     *    F110[HATYSA]=6 (Cargo=12)
     *    F112[MARS]=6 (Moved,Cargo=6)
     *    F125[CERES]=32 (Moved)  V41:Platinum Shekel
     *    F19[CERES]=1 (Gift from [MARS])
     *    F189[URANUS]=1 (Moved,At-Peace)
     *    F192[URANUS]=7 (Moved,Cargo=5,At-Peace)
     *    F202[MARS]=3 (Moved,Cargo=3,At-Peace)V2:Nebula Scrolls, Vol. II
     *    F226[CERES]=14 (Captured,Lost by [NEPTUNE],AH)
     *    // TODO: find where Iships, pships, or a fleet are the target
     *    (F34[HATYSA]-->W122)
     *    (F45[MARS]-->W143 F68[HATYSA]-->W143 F222[HATYSA]-->W143 F241[CERES]-->W39)
     *
     *  Assuming the input is "F112[MARS]=6 (Moved,Cargo=6)":
     *      scanner.next() will return "F112[MARS]=6"
     *
     *
     *  @param location where the fleet is
     *  @param scanner  scanner.next() will return "(Moved,Cargo=6)"
     *  @return a Fleet, or null if scanner.hasNext() isn't a fleet
     */

    internal fun parseSingleFleet(location: Int, scanner: Scanner): Fleet? {
        if(!scanner.hasNext("F\\d+.*"))
            return null

        val fleet = Fleet(location = location)
        val token = scanner.next()
        val parts = token.split("[")
        fleet.id = parts[0].substring(1).toInt()

        // parts[1] is something like "HATYSA]=2"
        val moreParts = parts[1].split("=")
        fleet.owner = moreParts[0].substring(0, moreParts[0].length - 1)
        fleet.size = moreParts[1].toInt()

        val pattern = Pattern.compile("^\\(F\\d+\\[.*")
        if (!scanner.hasNext() ||        // Last fleet at world has no body
            scanner.hasNext(pattern) )   // or moving fleet "(F34[someone]-->Wxxx)"
            return fleet                // means we're done

        parseFleetBody(fleet, scanner)
        fleet.artifacts = artifactManager.parseArtifactList(scanner, LocationType.FLEET, fleet.id)

        allFleets.add(fleet)
        return fleet
    }

    /**
     * Parse the fleet body '(Moved)', '(Cargo=6,At-Peace)', etc.  It is assumed
     * moving fleets ('(Fnnn[owner]-->Wnnn') has already been filtered out.
     * Planet busters are fun: F4[ATRIA]=24 (Moved,PLANET-BUSTER(Built))
     */
    private fun parseFleetBody(fleet: Fleet, scanner: Scanner) {
        if (scanner.hasNext("[(].*")) {
            // The fleet has a body.  Read in the whole thing, remove the parens,
            // and process each entry.
            scanner.useDelimiter("\\)")
            val body = scanner.next()
            val tokens = body.substring(2, body.length).split(",")

            for (token in tokens) {
                // @formatter:off
                if      (hasMatch("Moved", token))            fleet.moved = true
                else if (hasMatch("Cargo=", token))           fleet.cargo = simpleInt(token)
                else if (hasMatch("AH|AP|AI|AF123", token))   fleet.target = token
                else if (hasMatch("At-Peace", token))         fleet.atPeace = true
                else if (hasMatch("Captured", token))         fleet.captured = true
                else if (hasMatch("D", token))                fleet.droppedPBB = true
                else if (hasMatch("Gift", token)) {
                    val words = token.split(" ")
                    fleet.giftee = getOwner(words[2])
                }
                else if (hasMatch("Lost", token)){
                    val words = token.split(" ")
                    fleet.lostBy = getOwner(words[2])
                }
                else if (hasMatch("PLANET-BUSTER", token)){
                    fleet.hasPBB = true
                    if(hasMatch("Built", token)) {
                        fleet.builtPBB = true
                        scanner.next()  // Slurp up the extra ')'
                    }
                }
                // @formatter:on
            }
        }

        // Clear out the last delimiter if needed
        scanner.useDelimiter("\\s+")
        if(scanner.hasNext("[)]")) {
            scanner.next()
        }
    }

    internal fun parseFleetList(location: Int, scanner: Scanner): List<Fleet> {
        val foundFleets = mutableListOf<Fleet>()
        do {
            val fleet = parseSingleFleet(location, scanner)
            if(fleet != null) foundFleets.add(fleet)
        } while(fleet != null)

        return foundFleets.toList()
    }

    /**
     * Pretty print fleets
     *
     */
    fun prettyPrintFleets() {
        prettyPrintFleets(allFleets)
    }

    /**
     * Pretty print fleets
     *
     * @param fleets
     */
    fun prettyPrintFleets(fleets: List<Fleet>) {
        for(fleet in fleets)
            println("    $fleet")
    }
} // end of class

// This test fails because I'm futzing with how fleets get printed.
private fun simpleTest(verbose:Boolean = false) {
    val myName = "simpleTest"
    val simpleTests = listOf(
        "F2[MARS]=3 (Moved,Cargo=3,At-Peace,Gift from [JUPITER]) V3:Nebula Scrolls, Vol. III",
        "F3[]=4",
        "F25[CERES]=32 V88:Arcturian Stardust",
        "F72[MARS]=12 (Moved)",
        "F125[CERES]=32 (Moved) V41:Platinum Shekel",
        "F19[CERES]=1 (Gift from [MARS])",
        "F189[URANUS]=1 (Moved,At-Peace,AP)",
        "F192[URANUS]=7 (Moved,Cargo=5,At-Peace,AI)",
        "F202[MARS]=3 (Moved,Cargo=3,At-Peace,AF123) V2:Nebula Scrolls, Vol. II",
        "F226[CERES]=14 (Captured,Lost by [NEPTUNE],AH)",
        // TODO: find where Iships, pships, or a fleet are the target",
        "F7[FRODO]=12 V41:Platinum Shekel V88:Arcturian Stardust"
    )

    var pass = true
    if(verbose)
        println("$myName.  The output should be identical to the input.")

    for(test in simpleTests) {
        val fm = FleetManager()
        val scanner = Scanner(test)

        if(verbose) {
            println("-".repeat(50))
            println("Input:   $test")
        }
        val fleet = fm.parseSingleFleet(12, scanner)
        fleet?.let {
            if (test != fleet.toString()) {
                pass = false
                println("=".repeat(50))
                println("$myName: Fail.\nExpected $test\nGot $fleet")
                println("a: $test")
                println("b: $fleet")
            }
            if(verbose)
                println("Output:  $fleet")
        }
    }
    if(pass) println("SimpleTest pass")
    else     println("SimpleTest fail")
}

private fun testFleetList(verbose: Boolean = false) {
    //==========================================================================
    data class TestType(val input: String, val fleets: List<Fleet>)

    val tests = listOf(
        TestType("F125[CERES]=32 (Moved) F110[HATYSA]=6 (Cargo=12)", listOf(
            Fleet(id = 125, owner = "CERES", size = 32, moved = true),
            Fleet(id = 110, owner = "HATYSA", size = 6, cargo = 12))),

        TestType("F25[CERES]=2 V88:Arcturian Stardust", listOf(
            Fleet(id = 25, owner = "CERES", size = 2, artifacts = listOf(
                Artifact(88, "Arcturian Stardust")
            )))),

        TestType("F183[MARS]=1 V38:Arcturian LodeStar V54:Golden Sword " +
                        "F6[CECIL]=23 V47:Vegan Sword V65:Titanium Sepulchre V71:Platinum Moonstone",
                    listOf(
                Fleet(id = 183, owner = "MARS", size = 1, artifacts = listOf(
                    Artifact(38, "Arcturian LodeStar"),
                    Artifact(54, "Golden Sword"))),
                Fleet(id = 6, owner = "CECIL", size = 23, artifacts = listOf(
                    Artifact(23, "Vegan Sword"),
                    Artifact(65, "Titanium Sepulchre"),
                    Artifact(71, "Platinum Moonstone"))),
            ))
    )
    //==========================================================================

    if(verbose)
        println("=".repeat(30) + " testFleetList " + "=".repeat(30))

    val fm = FleetManager()
    var pass = true
    for(test in tests) {
        val expected = test.fleets
        val scanner = Scanner(test.input)
        val fleets = fm.parseFleetList(12, scanner)

        if(verbose) {
            println("-".repeat(50))
            println("In      : ${test.input}")
            println("Expected: ${test.fleets}")
            println("Out     : $fleets")
        }
        if(expected != fleets) {
            println("mismatch")
            pass = false
        }
    }

    val result = if(pass) "pass" else "fail"
    println("testFleetList $result")
}

private fun testPrettyPrint() {
    val tests = listOf(
        "F2[MARS]=3 (Moved,Cargo=3,At-Peace,Gift from [JUPITER]) V3:Nebula Scrolls, Vol. III",
        "F3[]=4",
        "F25[CERES]=32 V88:Arcturian Stardust",
        "F19[CERES]=1 (Gift from [MARS])",
        "F192[URANUS]=7 (Moved,Cargo=5,At-Peace,AI)",
        "F202[MARS]=3 (Moved,Cargo=3,At-Peace,AF123) V2:Nebula Scrolls, Vol. II",
        "F226[CERES]=14 (Captured,Lost by [NEPTUNE],AH)",
        "F6[CECIL]=23 V47:Vegan Sword V65:Titanium Sepulchre V71:Platinum Moonstone",
        "F4[ATRIA]=39 (D)",     // Sneak a couple PBB tests in.
        "F180[ATRIA]=2 (Moved,PLANET-BUSTER(Built))",
        "F190[ATRIA]=27 (Moved,PLANET-BUSTER)")

    //============================= testPrettyPrint =============================================
    val fm = FleetManager()
    var counter = 1
    for(test in tests) {
        val scanner = Scanner(test)
        fm.parseFleetList(counter++, scanner)
    }
    println("testing prettyPrintFleets()")
    fm.prettyPrintFleets()
    println("using getAllFleets")
    fm.prettyPrintFleets(fm.getAllFleets())
    println("eyeball it, this is purely visual.  If you like it it passes")
}

private fun futzWithParens() {
    val tests = listOf(
        "a b (c d e) f",
        "don't care (stuff 1) crap (stuff 2) end crap",
        "(outer stuff (inner junk) more crap)")

    for(test in tests) {
        //val regex = Pattern.compile("\\((.*?)\\)")
        val regex = Pattern.compile("[(](.*?)[)]")
        val result = mutableListOf<String>()
        val matcher = regex.matcher(test)
        while (matcher.find())
            result.add(matcher.group(1))

        println("Input $test got <$result>")
    }
    //========================= If the above looks good the test passes =================
}

// For whatever reason I don't correctly grok scanner.hasNext().
// It looks like '^' (Start of string) works for the first token in a string,
// but not for subsequent tokens.  That is, for "Foo Bar", when processing
// tokens "^Foo" works but "^Bar" doesn't.
private fun futzWithHasNext() {
    var src = "(F34[HATSYA]-->W122 etc etc"
    val pattern = Pattern.compile("^\\(F\\d+\\[.*")
    var scanner = Scanner(src)

    if(scanner.hasNext(pattern))
        println("found moving fleet")
    else
        println("moving fleet not found")

    println(src)
    println("next: ${scanner.next()}")

    // this is stupid.....
    src = "V88:Arcturian Shekel"
    scanner = Scanner(src)
    if(scanner.hasNext(Pattern.compile("^V\\d+:.*")))
        println("found artifact")
    else
        println("artifact not found")

    println(src)
    println("next: ${scanner.next()}")

    // so so stupid.....
    println("-".repeat(50))
    src = "F12[FRED]=6 (Moved) (F21[owner]-->Wnn)"
    scanner = Scanner(src)
    val token = scanner.next()  // so so stupid
    if(scanner.hasNext("[(].*"))
        println("found body")
    else
        println("body not found")

    println(src)
    println("token: <$token>")
    println("next: <${scanner.next()}>")
}

// There are never any args, I'm easily changing tests and getting rid of 'unused' warnings
fun main(args: Array<String>) {
    var foo = 1
    if(args.isNotEmpty())
        foo = args[0].toInt()

    if(foo == 1) {
        testPrettyPrint()
        simpleTest()
        testFleetList()
    }
    if(foo == 2)
        futzWithHasNext()
    if(foo == 3)
        futzWithParens()
    if(foo == 4) {
        val fleet = Fleet()
        println(fleet.full())
    }
}

