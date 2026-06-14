import java.util.Objects
import java.util.Scanner

/**
 * Specifies whether 'location' refers to a fleet or a world.
 */
enum class LocationType(val type: String) {
    FLEET("F"), WORLD("W"), UNKNOWN("U")
}

data class Artifact(
    var id: Int,
    var name: String,
    var owner: String = "Unknown",
    var where: LocationType = LocationType.UNKNOWN,
    var location: Int = 0
) {
    // Do I want everything, or just the id and name?
    override fun equals(other: Any?): Boolean {
        if( (other == null) || (other !is Artifact) )
            return false

        return this.id == other.id && this.name == other.name
    }

    override fun hashCode(): Int {
        return Objects.hash(id, name)
    }

    override fun toString(): String { return ("V$id:$name") }

    // Print everything known about the artifact
    fun printFull(): String {
        val sb = StringBuilder(toString())
        sb.append(", $owner, ${where.type}$location")
        return sb.toString()
    }
}

class ArtifactManager(listOfArtifacts: List<Artifact> = listOf()) {
    // Store artifacts in a map using it's ID as a key
    private val allArtifacts = mutableMapOf<Int, Artifact>()

    init {
        for(artifact in listOfArtifacts) {
            allArtifacts[artifact.id] = artifact
        }
    }
    /**
     * Return all the artifacts as a string.
     */
    override fun toString(): String {
        val sb = StringBuilder()
        for(key in allArtifacts.keys) {
            sb.append("${allArtifacts[key]} ")
        }
        return sb.toString()
    }

    /**
     * Add an artifact to the list.
     * @param artifact the artifact to add
     */
    fun add(artifact: Artifact) {
        // Ensure it's not already in the list for testing/debugger, later maybe
        // use putOrAdd()
        if(allArtifacts.containsKey(artifact.id)) {
            val existing = allArtifacts[artifact.id]
            println("duplicate artifact.  Existing: $existing, new: $artifact")
        }
        allArtifacts[artifact.id] = artifact
    }

    /**
     * Given an artifact ID return the artifact.  Returns an artifact
     * with an ID of 0 on error.
     */
    fun getByID(id:Int) : Artifact {
        return allArtifacts.getOrElse(id){ Artifact(0, "error")}
    }

    /**
     * Return a list of all the artifacts
     */
    fun getAllArtifacts() : List<Artifact> {
        return allArtifacts.values.toList()
    }

    /**
     * Given a string like "V97:Vegan Sphinx" extract the id and name.
     * In this case 'scanner.next()' will return "V97:Vegan"
     *
     * Some special artifacts have longer names: TREASURE OF POLARIS, SLIPPERS OF VENUS,
     * LESSER OF TWO EVILS, NEBULA SCROLLS (Volumes 1-5)
     *
     * Adds the parsed artifact to the list of artifacts seen.
     *
     * @param scanner  scanner.next() will return e.g. "V97:Vegan"
     * @return an Artifact, or null if scanner.next() won't return an artifact
     */

    internal fun parseSingleArtifact(scanner: Scanner): Artifact? {
        if(!scanner.hasNext("V\\d+:.*"))
            return null

        val token = scanner.next()
        val parts = token.split(":")
        val id= parts[0].substring(1).toInt()
        val sb = StringBuilder(parts[1])

        // Grab the rest of the name.  Stop when hasNext() will return a token indicating
        // the start of an artifact, fleet, or moving fleet
        while(scanner.hasNext() && !scanner.hasNext("V\\d+:.*|F\\d+.*|\\(F\\d+.*"))
            sb.append(" " + scanner.next())

        val artifact =  Artifact(id = id, name = sb.toString())
        add(artifact)
        return artifact
    }

    /**
     * Parse a list of artifacts.
     *
     * @param scanner  data source
     * @param worldOrFleet  world or fleet
     * @param where     id of the world or fleet the list is on.
     * @return A list of artifacts found.  The list is mt if no artifacts are found.
     */
    fun parseArtifactList(scanner: Scanner, worldOrFleet: LocationType, where: Int) : List<Artifact> {
        val foundArtifacts = mutableListOf<Artifact>()
        do {
            val artifact = parseSingleArtifact(scanner)
            artifact?.let {
                artifact.where = worldOrFleet
                artifact.location = where
                foundArtifacts.add(artifact)
            }
        } while(artifact != null)

        return foundArtifacts.toList()
    }
}

private fun testSingleArtifacts() {
    var pass = true

    val a1 = Artifact(1, "Vegan Tiger", "owner1", LocationType.FLEET, 2)
    val expected = "V1:Vegan Tiger, owner1, F2"
    if(a1.printFull() != expected) {
        println("expected $expected, got ${a1.printFull()}))")
        pass = false
    }

    data class ParseTestType(val input: String, val id: Int, val name: String)

    val parseTests = listOf(
        ParseTestType("V98:Plastic Robot", 98, "Plastic Robot"),
        ParseTestType("V97:Vegan Sphinx", 97, "Vegan Sphinx"),
        ParseTestType("V6:SLIPPERS OF VENUS", 6, "SLIPPERS OF VENUS"),
        ParseTestType("V7:LESSER OF TWO EVILS", 7, "LESSER OF TWO EVILS"),
        ParseTestType("V8:TREASURE OF POLARIS", 8, "TREASURE OF POLARIS"),
        // ensure it doesn't parse multiple artifacts
        ParseTestType("V17:Jims Undies V18: Jims socks", 17, "Jims Undies"),
    )

    val verbose = false

    for(test in parseTests) {
        if (verbose) println(test)

        val arts = ArtifactManager()
        val scanner = Scanner(test.input)
        val artifact = arts.parseSingleArtifact(scanner)
        if (verbose) println("     $artifact")

        val expected = Artifact(test.id, test.name)
        //if ((artifact.id != test.id) || (artifact.name != test.name)) {
        if(artifact != expected) {
            println("Expected: $expected\nGot     : $artifact")
            pass = false
        }
    }
    val result = if(pass) "pass" else "fail"
    println("testSingleArtifact $result")
}

/**
 * Verify it parses lists of artifacts correctly
 */
private fun testArtifactList(verbose: Boolean = false) {
    data class TestType(val input: String, val artifacts: List<Artifact>)
    val tests = listOf(
        TestType("V38:Arcturian Lodestar  V54:Golden Sword", listOf(
                Artifact(38, "Arcturian Lodestar"),
                Artifact(54, "Golden Sword"))),

        TestType("V57:Vegan Sword V65:Titanium Sepulchre V71:Platinum Moonstone " +
            "   V2:Nebula Scrolls, Vol. II V15:Titanium Crown V46:Blessed Shekel " +
                "V72:Ancient Moonstone V88:Arcturian Stardust",
            listOf(
                Artifact(65, "Titanium Sepulchre"),
                Artifact(57, "Vegan Sword"),
                Artifact(71, "Platinum Moonstone"),
                Artifact(2, "Nebula Scrolls, Vol. II"),
                Artifact(15, "Titanium Crown"),
                Artifact(46, "Blessed Shekel"),
                Artifact(72, "Ancient Moonstone"),
                Artifact(88, "Arcturian Stardust"))),
    )

    var pass = true
    //val verbose = false

    for(test in tests) {
        if (verbose) println("\ntestArtifactList input ${test.input}")

        val arts = ArtifactManager()
        val scanner = Scanner(test.input)
        val artifacts = arts.parseArtifactList(scanner, LocationType.WORLD, 42)
        if (verbose) println("out  $arts")

        val expected = test.artifacts
        if(artifacts.size != expected.size) {
            println("testArtifactList input:  ${test.input}\n   expected ${expected.size} artifacts, got ${artifacts.size}")
            pass = false
        }

        if(artifacts != expected) {
            println("Expected: $expected\nGot     : $artifacts")
            pass = false
        }
    }
    val result = if(pass) "pass" else "fail"
    println("testArtifactList $result")
}

private fun testListIn(verbose: Boolean = false) {
    data class TestType(val input: String, val artifacts: List<Artifact>)

    val tests = listOf(
        TestType(
            "V38:Arcturian Lodestar  V54:Golden Sword",
            listOf(Artifact(38, "Arcturian Lodestar"))),

        TestType("V57:Vegan Sword V65:Titanium Sepulchre V71:Platinum Moonstone " +
                        "   V2:Nebula Scrolls, Vol. II V15:Titanium Crown V46:Blessed Shekel " +
                        "V72:Ancient Moonstone V88:Arcturian Stardust",
        listOf(
            Artifact(57, "Vegan Sword"),
            Artifact(65, "Titanium Sepulchre"),
            Artifact(71, "Platinum Moonstone"),
            Artifact(2, "Nebula Scrolls, Vol. II"),
            Artifact(15, "Titanium Crown"),
            Artifact(46, "Blessed Shekel"),
            Artifact(72, "Ancient Moonstone"),
            Artifact(88, "Arcturian Stardust"))),
        )

    val name = "testListIn"
    var pass = true
    for(test in tests) {
        if(verbose) println("\n\nIn: ${test.input}")

        val arts = ArtifactManager(test.artifacts)
        if (verbose) println("out  $arts")

        if(arts.getAllArtifacts().size != test.artifacts.size) {
            println("$name: expected ${test.artifacts.size} artifacts, got ${arts.getAllArtifacts().size}")
            pass = false
        }

        for(artifact in arts.getAllArtifacts()) {
            if(verbose) println("$name: verifying $artifact")
            if(!test.artifacts.contains(artifact)) {
                println("$name: Unexpected artifact $artifact")
                pass = false
            }
        }
    }
    val result = if(pass) "pass" else "fail"
    println("$name $result")
}

// Get rid of IntelliJ warnings, this should never be called
private fun fixUnused() {
    val am = ArtifactManager()
    val junk = am.getByID(42)
    if(junk.id == 43) println()
    testArtifactList(true)
    testListIn(true)
}

// There are never any args
fun main(args: Array<String>) {
    if(args.isNotEmpty()) fixUnused()
    testSingleArtifacts()
    testArtifactList(false)
    testListIn(false)
}

