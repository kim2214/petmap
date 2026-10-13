package com.kimdev.petmap.di

import com.kimdev.petmap.core.location.LocationProvider
import com.kimdev.petmap.core.location.LocationProviderImpl
import com.kimdev.petmap.data.local.PetProfileStore
import com.kimdev.petmap.data.local.PetProfileStoreImpl
import com.kimdev.petmap.data.local.RecentPlaceStore
import com.kimdev.petmap.data.local.RecentPlaceStoreImpl
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

    @Binds
    @Singleton
    abstract fun bindRecentPlaceStore(impl: RecentPlaceStoreImpl): RecentPlaceStore

    @Binds
    @Singleton
    abstract fun bindPetProfileStore(impl: PetProfileStoreImpl): PetProfileStore
}
