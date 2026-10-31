package com.calendaralarm.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bindings from the domain interfaces to their implementations are added here as each
 * implementation lands (see issue #15).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
