package io.github.abhishekcs194.printdeck.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.abhishekcs194.printdeck.pdf.engine.ImageToPdfConverter
import io.github.abhishekcs194.printdeck.pdf.engine.ImpositionEngine
import io.github.abhishekcs194.printdeck.pdf.engine.PdfDocumentReader
import io.github.abhishekcs194.printdeck.pdf.engine.PdfPreviewRenderer
import io.github.abhishekcs194.printdeck.print.ipp.IppClient
import io.github.abhishekcs194.printdeck.print.ipp.discovery.MdnsDiscovery
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkScanner
import io.github.abhishekcs194.printdeck.print.ipp.discovery.NetworkTopology
import io.github.abhishekcs194.printdeck.print.ipp.discovery.PrinterDiscovery
import javax.inject.Singleton

/**
 * Wiring for the PDF and printing modules.
 *
 * These types are plain classes rather than Hilt-annotated ones on purpose: the
 * modules they live in have no dependency on Hilt, so they stay usable from
 * tests and from any other consumer without dragging a DI framework along.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePdfDocumentReader(@ApplicationContext context: Context): PdfDocumentReader =
        PdfDocumentReader(context)

    @Provides
    @Singleton
    fun provideImageToPdfConverter(@ApplicationContext context: Context): ImageToPdfConverter =
        ImageToPdfConverter(context)

    @Provides
    @Singleton
    fun provideImpositionEngine(@ApplicationContext context: Context): ImpositionEngine =
        ImpositionEngine(context)

    @Provides
    @Singleton
    fun providePreviewRenderer(): PdfPreviewRenderer = PdfPreviewRenderer()

    @Provides
    @Singleton
    fun provideNetworkTopology(@ApplicationContext context: Context): NetworkTopology =
        NetworkTopology(context)

    @Provides
    @Singleton
    fun provideNetworkScanner(): NetworkScanner = NetworkScanner()

    @Provides
    @Singleton
    fun provideMdnsDiscovery(@ApplicationContext context: Context): MdnsDiscovery =
        MdnsDiscovery(context)

    @Provides
    @Singleton
    fun provideIppClient(): IppClient = IppClient()

    @Provides
    @Singleton
    fun providePrinterDiscovery(
        mdns: MdnsDiscovery,
        scanner: NetworkScanner,
        topology: NetworkTopology,
    ): PrinterDiscovery = PrinterDiscovery(mdns, scanner, topology)
}
