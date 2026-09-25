package com.uci.pkbe.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.uci.pkbe.ApiClient
import com.uci.pkbe.Config
import com.uci.pkbe.DeviceIdentity
import com.uci.pkbe.core.network.NetworkService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providesInterceptor(
        @ApplicationContext context: Context
    ): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Device-Id", DeviceIdentity.deviceId(context))
                .build()

            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(
        interceptor: Interceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String {
        return Config.baseUrl
    }

    @Provides
    fun provideGson(): Gson {
        //return GsonBuilder().registerTypeHierarchyAdapter(Date::class.java, RemoteDateTypeAdapter).create()
        return GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(
        @Named("baseUrl") baseUrl: String,
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit.Builder {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
    }

    @Provides
    @Singleton
    fun providesNetworkService(
        retrofitBuilder: Retrofit.Builder,
        client: OkHttpClient,
        @Named("baseUrl") baseUrl: String
    ): NetworkService {
        val networkService = retrofitBuilder
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return networkService.create(NetworkService::class.java)
    }

}