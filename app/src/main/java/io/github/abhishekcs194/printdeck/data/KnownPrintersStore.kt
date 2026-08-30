package io.github.abhishekcs194.printdeck.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.abhishekcs194.printdeck.print.ipp.discovery.DiscoverySource
import io.github.abhishekcs194.printdeck.print.ipp.discovery.Ipv4Subnet
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterEndpoint
import io.github.abhishekcs194.printdeck.print.ipp.discovery.parseIpv4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.printerStore by preferencesDataStore(name = "known_printers")

/**
 * Remembers where printers were found.
 *
 * This is what makes the second use of a printer instant. The first search may
 * have to widen through several networks to find it; afterwards the address is
 * tried directly, and the subnet it lived on is searched first. Without this
 * every launch pays the full cost of the widest search.
 *
 * Addresses are stored rather than any identity, because a domestic printer is
 * usually on a DHCP lease with no reservation and moves on its own. A remembered
 * address is a good first guess, never a guarantee.
 */
@Singleton
class KnownPrintersStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val endpointsKey = stringSetPreferencesKey("endpoints")
    private val subnetsKey = stringSetPreferencesKey("subnets")

    /** Previously seen printers, as endpoints ready to re-probe. */
    val knownEndpoints = context.printerStore.data.map { preferences ->
        preferences[endpointsKey].orEmpty().mapNotNull(::parseEndpoint)
    }

    /** Subnets a printer has been found on, searched before anything else. */
    val knownSubnets = context.printerStore.data.map { preferences ->
        preferences[subnetsKey].orEmpty().mapNotNull { cidr ->
            runCatching { Ipv4Subnet.parse(cidr) }.getOrNull()
        }
    }

    suspend fun currentEndpoints() = knownEndpoints.first()

    suspend fun currentSubnets() = knownSubnets.first()

    suspend fun remember(printer: PrinterEndpoint) {
        val subnet = runCatching {
            Ipv4Subnet.containing(parseIpv4(printer.address), SUBNET_PREFIX).asCidr()
        }.getOrNull()

        context.printerStore.edit { preferences ->
            // Most recent first, then capped. Sets have no order of their own,
            // so the list is built deliberately before being stored - otherwise
            // "keep the newest few" would evict arbitrary entries.
            preferences[endpointsKey] = (
                listOf(printer.encode()) +
                    preferences[endpointsKey].orEmpty()
                        .filterNot { it.startsWith("${printer.address}|${printer.port}|") }
                )
                .take(MAX_REMEMBERED)
                .toSet()

            if (subnet != null) {
                preferences[subnetsKey] = (
                    listOf(subnet) + preferences[subnetsKey].orEmpty().filterNot { it == subnet }
                    )
                    .take(MAX_REMEMBERED)
                    .toSet()
            }
        }
    }

    suspend fun forget(printer: PrinterEndpoint) {
        context.printerStore.edit { preferences ->
            preferences[endpointsKey] = preferences[endpointsKey].orEmpty()
                .filterNot { it.startsWith("${printer.address}|${printer.port}|") }
                .toSet()
        }
    }

    // A flat encoding rather than JSON: three fields, and a serialisation
    // dependency for that would be more moving parts than the problem has.
    private fun PrinterEndpoint.encode() =
        listOf(address, port.toString(), resourcePath, name.orEmpty()).joinToString("|")

    private fun parseEndpoint(encoded: String): PrinterEndpoint? {
        val parts = encoded.split('|')
        if (parts.size < ENCODED_FIELDS) return null
        val port = parts[FIELD_PORT].toIntOrNull() ?: return null
        return PrinterEndpoint(
            address = parts[FIELD_ADDRESS],
            port = port,
            source = DiscoverySource.REMEMBERED,
            resourcePath = parts[FIELD_RESOURCE_PATH],
            name = parts.getOrNull(FIELD_NAME)?.ifEmpty { null },
        )
    }

    private companion object {
        const val SUBNET_PREFIX = 24
        const val MAX_REMEMBERED = 8

        // Field positions in the encoded form. Named because positional parsing
        // with bare indices is where this kind of encoding quietly goes wrong.
        const val FIELD_ADDRESS = 0
        const val FIELD_PORT = 1
        const val FIELD_RESOURCE_PATH = 2
        const val FIELD_NAME = 3

        /** Name is optional, so a record is valid with the first three present. */
        const val ENCODED_FIELDS = 3
    }
}
