package io.github.abhishekcs194.printdeck.data

import io.github.abhishekcs194.printdeck.print.system.PrintJobSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The imposed document waiting to be printed.
 *
 * Passed through a holder rather than a navigation argument: it carries a file
 * handle and a page count, which do not belong in a route, and serialising them
 * only to parse them straight back would be work done for the framework rather
 * than the app.
 */
@Singleton
class PendingJob @Inject constructor() {
    var spec: PrintJobSpec? = null
        private set

    fun offer(spec: PrintJobSpec) {
        this.spec = spec
    }

    fun clear() {
        spec = null
    }
}
