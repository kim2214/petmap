package com.kimdev.petmap.di

import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.LocationProviderImpl
import com.kimdev.petmap.data.local.RecentSearchStore
import com.kimdev.petmap.data.local.RecentSearchStoreImpl
import com.kimdev.petmap.data.repository.PlaceRepositoryImpl
import com.kimdev.petmap.domain.repository.PlaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(impl: PlaceRepositoryImpl): PlaceRepository

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: LocationProviderImpl): LocationProvider

    @Binds
    @Singleton
    abstract fun bindRecentSearchStore(impl: RecentSearchStoreImpl): RecentSearchStore
}
