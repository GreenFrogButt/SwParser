/**
 * This list makes it easier to update the map.
 */
data class ChangedOwner(private val worldNumber: Int,
                        private val oldOwner: String,
                        private val newOwner: String
) {

    // I'm making it look like the Java version so I can just
    // compare results for testing.  When it works I can print
    // this how I want.
    // I called this javaBuffoonary elsewhere.

    override fun toString(): String {
        val unknown = ""
        val old = oldOwner.ifEmpty { unknown }
        val new = newOwner.ifEmpty { unknown }

        return(String.format("World %3d was %8s, now %8s", worldNumber, old, new))
    }
}