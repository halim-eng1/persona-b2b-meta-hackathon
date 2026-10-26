package com.example.persona.core.common

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CancellationException

suspend fun <T> safeCall(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> T
): Resource<T> = withContext(dispatcher) {
    try {
        Resource.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppException) {
        Resource.Error(e)
    } catch (e: IOException) {
        Resource.Error(AppException.NetworkUnavailable(e))
    } catch (e: SQLiteException) {
        Resource.Error(AppException.DatabaseError("unknown", e))
    } catch (e: Exception) {
        Resource.Error(AppException.Unknown(e))
    }
}
