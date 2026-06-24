package com.kimdev.petmap.core.common

/**
 * 네트워크/DB 작업 결과를 표현하는 공통 래퍼.
 * UI 는 이 상태를 받아 로딩/성공/실패 분기를 처리한다.
 */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>
}
