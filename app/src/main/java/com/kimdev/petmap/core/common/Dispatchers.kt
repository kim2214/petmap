package com.kimdev.petmap.core.common

import javax.inject.Qualifier

/** CPU 바운드 작업용 디스패처(Dispatchers.Default) 주입 한정자. 테스트에서 교체 가능하게 한다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
