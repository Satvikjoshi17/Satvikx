package com.satvik.satvikx.di

import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.download.DownloadRepositoryImpl
import com.satvik.satvikx.data.repository.StreamRepository
import com.satvik.satvikx.data.repository.StreamRepositoryImpl
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
    abstract fun bindStreamRepository(
        streamRepositoryImpl: StreamRepositoryImpl
    ): StreamRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        downloadRepositoryImpl: DownloadRepositoryImpl
    ): DownloadRepository
}
