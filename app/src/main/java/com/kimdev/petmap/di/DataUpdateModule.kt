package com.kimdev.petmap.di

import com.kimdev.petmap.core.common.Constants
import com.kimdev.petmap.data.remote.DataDownloader
import com.kimdev.petmap.data.remote.UrlDataDownloader
import com.kimdev.petmap.data.update.PlaceDataUpdaterImpl
import com.kimdev.petmap.domain.repository.PlaceDataUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** 원격 데이터 배포 기준 URL (끝은 `/`) */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DataUpdateBaseUrl

@Module
@InstallIn(SingletonComponent::class)
abstract class DataUpdateModule {

    @Binds
    abstract fun bindDataDownloader(impl: UrlDataDownloader): DataDownloader

    @Binds
    @Singleton
    abstract fun bindPlaceDataUpdater(impl: PlaceDataUpdaterImpl): PlaceDataUpdater

    companion object {
        @Provides
        @DataUpdateBaseUrl
        fun provideDataUpdateBaseUrl(): String = Constants.DATA_UPDATE_BASE_URL
    }
}
