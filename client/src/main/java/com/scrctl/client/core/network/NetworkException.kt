package com.scrctl.client.core.network

/**
 * 网络请求异常基类
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * HTTP 错误
     */
    data class HttpError(
        val code: Int,
        val errorBody: String? = null
    ) : NetworkException("HTTP错误: $code${errorBody?.let { " - $it" } ?: ""}") {
        
        val isClientError: Boolean get() = code in 400..499
        val isServerError: Boolean get() = code in 500..599
        
        fun getUserMessage(): String = when {
            code == 401 -> "认证失败，请检查连接配置"
            code == 403 -> "访问被拒绝"
            code == 404 -> "请求的资源不存在"
            code == 408 || code == 504 -> "请求超时，请检查网络连接"
            isClientError -> "请求错误 ($code)"
            isServerError -> "服务器错误 ($code)"
            else -> "网络请求失败 ($code)"
        }
    }
    
    /**
     * 空响应体
     */
    object EmptyResponseBody : NetworkException("服务器返回空响应")
    
    /**
     * JSON 解析错误
     */
    data class JsonParseError(
        val json: String?,
        override val cause: Throwable?
    ) : NetworkException("JSON解析失败", cause) {
        fun getUserMessage(): String = "数据格式错误，请联系管理员"
    }
    
    /**
     * 网络连接错误
     */
    data class ConnectionError(
        override val cause: Throwable
    ) : NetworkException("网络连接失败: ${cause.message}", cause) {
        fun getUserMessage(): String = when {
            cause is java.net.UnknownHostException -> "无法连接到服务器，请检查地址和端口"
            cause is java.net.SocketTimeoutException -> "连接超时，请检查网络"
            cause is java.net.ConnectException -> "连接被拒绝，请确认服务器是否运行"
            else -> "网络连接错误，请检查网络设置"
        }
    }
    
    /**
     * 未知错误
     */
    data class Unknown(
        override val cause: Throwable
    ) : NetworkException("未知错误: ${cause.message}", cause)
}

/**
 * 将通用异常转换为 NetworkException
 */
fun Throwable.toNetworkException(): NetworkException = when (this) {
    is NetworkException -> this
    is java.net.UnknownHostException,
    is java.net.SocketTimeoutException,
    is java.net.ConnectException -> NetworkException.ConnectionError(this)
    is com.google.gson.JsonSyntaxException,
    is com.google.gson.JsonParseException -> NetworkException.JsonParseError(null, this)
    else -> NetworkException.Unknown(this)
}

/**
 * 获取用户友好的错误消息
 */
fun NetworkException.getUserFriendlyMessage(): String = when (this) {
    is NetworkException.HttpError -> getUserMessage()
    is NetworkException.EmptyResponseBody -> "服务器返回了空数据"
    is NetworkException.JsonParseError -> getUserMessage()
    is NetworkException.ConnectionError -> getUserMessage()
    is NetworkException.Unknown -> "发生未知错误: ${message}"
}
