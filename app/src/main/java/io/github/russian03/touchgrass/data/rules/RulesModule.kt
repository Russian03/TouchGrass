package io.github.russian03.touchgrass.data.rules

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RulesModule {
    @Binds
    abstract fun ruleSource(impl: RuleRepository): RuleSource
}
