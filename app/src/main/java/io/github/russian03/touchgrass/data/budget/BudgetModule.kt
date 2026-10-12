package io.github.russian03.touchgrass.data.budget

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.russian03.touchgrass.core.budget.TimeSource

@Module
@InstallIn(SingletonComponent::class)
abstract class BudgetModule {
    @Binds
    abstract fun timeSource(impl: AndroidTimeSource): TimeSource
}
