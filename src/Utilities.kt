import kotlin.system.exitProcess

/**
 * Assorted utilities I use
 */
object Utilities {
    val hasMatch = { lookFor: String, input: String ->
        lookFor.toRegex().containsMatchIn(input)
    }

    // The input is the owner surrounded by brackets ("[OWNER]").
    // Remove the brackets
    fun getOwner(input: String): String {
        return input.substring(1, input.length - 1)
    }

    // Given a token like "Metal=3" or "Foo=12" return the number
    fun simpleInt(input: String): Int {
        val words = input.split("=")
        try {
            return words[1].toInt()
        } catch (e: Exception) {
            println("simpleInt error with $input\n$e")
            throw (e)        // decided this is bad and the program should end
            // return -1
        }
    }

    /**
     * When printing a world, if the line is too long wrap on a given boundry.
     * Subsequent parts of the line are indented
     *
     * Needs to be modified to print artifacts and fleets.
     */
    fun addLineBreaks(line: String, maximumLength: Int = 77, indent: Int = 4, delimiter: String = ","): String {
        val newLine = System.lineSeparator() ?: "\n"

        var input = line
        val result = StringBuilder()

        do {
            if (input.length <= maximumLength) {
                result.append(input)
                input = ""
            } else {
                val index = input.lastIndexOf(delimiter, maximumLength)
                if (index < 0) {
                    // Not found, now what?  Exit is good for development, but if
                    // I'm a user I want to see the data also.
                    println("addLineBreaks: Error in <$input>")
                    val user = true
                    if (user) {
                        result.append(input)
                        input = ""
                    } else
                        exitProcess(index)
                }
                // Of course Artifacts work differently
                val fudgeFactor = if (delimiter == "V") index else index + 1
                result.append(input.take(fudgeFactor))
                input = newLine + " ".repeat(indent) + input.substring(fudgeFactor)
            }
        } while (input.isNotEmpty())

        return result.toString()
    }
}

private fun testAddLineBreaks(verbose: Boolean = false) {
    data class TestData(val maxLength: Int, val input: String)

    val tests = listOf(
        // These assume a line length of 77 and you have to eyeball the result
        TestData(80, "W7 (20,116,161,218) [CERES] (First=2,Last=3,Metal=10)"),
        TestData(30, "W18 (20,116,161,218) [CERES] (First=6,Last=8,Metal=10,stuff=abcde,bar=123456)"),
    )

    println("=".repeat(30) + " testAddLineBreaks " + "=".repeat(30))
    var pass = true
    for (test in tests) {
        //val result = addLineBreaks(test.input, test.maxLength)
        val result = "dafuq"
        //val result = addLineBreaks(test.input, test.maxLength).split("\n")

        if (verbose) {
            println("Line length ${test.maxLength}")
            println("In :${test.input}\n")
            println("Out:$result")
        }
        for (line in result.split("\n")) {
            if (line.length > test.maxLength) {
                //println("Line too long at ${test.input}: $line")
                println("Line too long.  Len: ${line.length}  maxLen: ${test.maxLength} line: $line")
                pass = false
            }
        }
    }

    val result = if (pass) "pass" else "fail"
    println("testFleetList $result")
}


fun main() {
    testAddLineBreaks()
}
