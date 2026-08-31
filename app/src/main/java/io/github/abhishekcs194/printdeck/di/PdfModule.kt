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
import javax.inject.Singleton

/**
 * Reading, imposing and rendering documents.
 *
 * These are plain classes rather than Hilt-annotated ones on purpose: the
 * modules they live in have no dependency on Hilt, so they stay usable from
 * tests and from any other consumer without dragging a DI framework along.
 */
@Module
@InstallIn(SingletonComponent::class)
object PdfModule {

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
}
