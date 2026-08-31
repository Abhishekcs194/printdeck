package io.github.abhishekcs194.printdeck.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.abhishekcs194.printdeck.print.ipp.IppClient
import io.github.abhishekcs194.printdeck.print.ipp.IppPrinter
import io.github.abhishekcs194.printdeck.print.ipp.discovery.Announcements
import io.github.abhishekcs194.printdeck.print.ipp.discovery.MdnsDiscovery
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkChanges
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkProbe
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkScanner
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkTopology
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterDiscovery
import io.github.abhishekcs194.printdeck.print.ipp.discovery.Topology
import javax.inject.Singleton

/** Finding printers, asking what they can do, and sending jobs to them. */
@Module
@InstallIn(SingletonComponent::class)
object PrintingModule {

    @Provides
    @Singleton
    fun provideTopology(@ApplicationContext context: Context): Topology = NetworkTopology(context)

    @Provides
    @Singleton
    fun provideNetworkProbe(): NetworkProbe = NetworkScanner()

    @Provides
    @Singleton
    fun provideAnnouncements(@ApplicationContext context: Context): Announcements =
        MdnsDiscovery(context)

    @Provides
    @Singleton
    fun provideNetworkChanges(@ApplicationContext context: Context): NetworkChanges =
        NetworkChanges(context)

    @Provides
    @Singleton
    fun providePrinterDiscovery(
        announcements: Announcements,
        probe: NetworkProbe,
        topology: Topology,
    ): PrinterDiscovery = PrinterDiscovery(announcements, probe, topology)

    @Provides
    @Singleton
    fun provideIppClient(): IppClient = IppClient()

    @Provides
    @Singleton
    fun provideIppPrinter(@ApplicationContext context: Context): IppPrinter =
        // Rasterised jobs are large and single-use, so they live in the cache
        // where the system can reclaim them if it needs the space.
        IppPrinter(workingDirectory = context.cacheDir)
}
