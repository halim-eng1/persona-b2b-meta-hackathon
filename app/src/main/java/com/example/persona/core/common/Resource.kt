package com.example.persona.core.common

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(
        val exception: AppException,
        val data: Nothing? = null
    ) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error
    val isLoading get() = this is Loading

    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }
}

fun <T> Resource<T>.getOrThrow(): T = when (this) {
    is Resource.Success -> data
    is Resource.Error -> throw exception
    is Resource.Loading -> error("Resource is still loading")
}
