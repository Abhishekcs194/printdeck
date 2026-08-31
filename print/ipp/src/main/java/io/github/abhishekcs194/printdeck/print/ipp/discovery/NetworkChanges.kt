package io.github.abhishekcs194.printdeck.print.ipp.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits whenever the device's network changes.
 *
 * Discovery results are only true of the network they were found on. Moving to
 * another Wi-Fi band, another access point, or off Wi-Fi entirely can put the
 * phone on a different subnet where a previously found printer is simply not
 * reachable — and nothing on screen would say so. Without this the app keeps
 * showing what it found somewhere else and fails when the user acts on it.
 *
 * Note the [awaitClose] here is correct, unlike in a sweep: this is a live
 * subscription that should last as long as someone is collecting it.
 */
class NetworkChanges(private val context: Context) {

    fun changes(): Flow<Unit> = callbackFlow {
        val connectivity = context.getSystemService<ConnectivityManager>()
        if (connectivity == null) {
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            // Both directions matter: joining a network is when a new search
            // becomes worthwhile, and leaving one is when existing results stop
            // being true.
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                trySend(Unit)
            }
        }

        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }
}
