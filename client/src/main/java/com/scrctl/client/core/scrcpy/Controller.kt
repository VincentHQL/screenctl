package com.scrctl.client.core.scrcpy

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class TouchPoint(
    val pointerId: Long,
    val x: Int,
    val y: Int,
    val pressure: Float,
)

class Controller(
    private val channel: AgentChannel,
    private val callbacks: Callbacks = Callbacks.NONE,
) : AutoCloseable {

    interface Callbacks {
        fun onEnded(controller: Controller, error: Boolean) = Unit
        fun onDisplayRotationChanged(controller: Controller, rotation: Int) = Unit

        companion object {
            val NONE = object : Callbacks {}
        }
    }

    constructor(socketName: String) : this(LocalSocket().apply {
        connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
    }.let { socket ->
        AgentChannel(socket.inputStream, socket.outputStream, socket)
    })

    private val clipboardResponses = LinkedBlockingQueue<String>()
    private val output = DataOutputStream(BufferedOutputStream(channel.outputStream))
    private val queue = ControllerMessageQueue(CONTROL_MSG_QUEUE_LIMIT)
    private val inputManager = InputManager(
        tag = TAG,
        enqueue = ::pushMessage,
        warnIfClamped = ::warnIfClamped,
    )
    private val receiver = Receiver(
        tag = TAG,
        input = DataInputStream(BufferedInputStream(channel.inputStream)),
        controllerProvider = { this },
        callbacksProvider = { callbacks },
        clipboardResponses = clipboardResponses,
    )

    private val controllerThread = Thread({ runController() }, "scrctl-ctl")
    private val receiverThread = Thread({ runReceiver() }, "scrctl-ctl-recv")

    @Volatile
    private var stopped = false

    @Volatile
    private var closeRequested = false

    @Volatile
    private var endedReported = false

    init {
        Log.d(TAG, "Starting controller threads")
        controllerThread.start()
        receiverThread.start()
    }

    fun injectKeycode(action: Int, keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        inputManager.injectKeycode(action, keycode, repeat, metaState)
    }

    fun keyDown(keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        inputManager.keyDown(keycode, repeat, metaState)
    }

    fun keyUp(keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        inputManager.keyUp(keycode, repeat, metaState)
    }

    fun pressKeycode(keycode: Int, metaState: Int = 0) {
        inputManager.pressKeycode(keycode, metaState)
    }

    fun pressHome() = inputManager.pressHome()

    fun pressBack() = inputManager.pressBack()

    fun pressAppSwitch() = inputManager.pressAppSwitch()

    fun pressPower() = inputManager.pressPower()

    fun pressVolumeUp() = inputManager.pressVolumeUp()

    fun pressVolumeDown() = inputManager.pressVolumeDown()

    fun pressMenu() = inputManager.pressMenu()

    fun injectText(text: String) = inputManager.injectText(text)

    fun injectTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int,
        buttons: Int,
    ) {
        inputManager.injectTouchEvent(
            action = action,
            pointerId = pointerId,
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pressure = pressure,
            actionButton = actionButton,
            buttons = buttons,
        )
    }

    fun injectMultiTouchEvent(
        action: Int,
        actionIndex: Int,
        pointers: List<TouchPoint>,
        screenWidth: Int,
        screenHeight: Int,
        actionButton: Int,
        buttons: Int,
    ) {
        inputManager.injectMultiTouchEvent(
            action = action,
            actionIndex = actionIndex,
            pointers = pointers,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            actionButton = actionButton,
            buttons = buttons,
        )
    }

    fun injectScrollEvent(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int,
    ) {
        inputManager.injectScrollEvent(
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            hScroll = hScroll,
            vScroll = vScroll,
            buttons = buttons,
        )
    }

    fun backOrScreenOn(action: Int) = inputManager.backOrScreenOn(action)

    fun pressBackOrScreenOn() = inputManager.pressBackOrScreenOn()

    fun expandNotificationPanel() = inputManager.expandNotificationPanel()

    fun expandSettingsPanel() = inputManager.expandSettingsPanel()

    fun collapsePanels() = inputManager.collapsePanels()

    fun getClipboard(copyKey: Int = ControlMessage.CopyKey.NONE): String? {
        inputManager.getClipboard(copyKey)
        return clipboardResponses.poll(CLIPBOARD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    fun setClipboard(sequence: Long, text: String, paste: Boolean) = inputManager.setClipboard(sequence, text, paste)

    fun setDisplayPower(on: Boolean) = inputManager.setDisplayPower(on)

    fun rotateDevice() = inputManager.rotateDevice()

    fun uhidCreate(
        id: Int,
        vendorId: Int,
        productId: Int,
        name: String,
        reportDesc: ByteArray,
    ) = inputManager.uhidCreate(id, vendorId, productId, name, reportDesc)

    fun uhidInput(id: Int, data: ByteArray) = inputManager.uhidInput(id, data)

    fun uhidDestroy(id: Int) = inputManager.uhidDestroy(id)

    fun openHardKeyboardSettings() = inputManager.openHardKeyboardSettings()

    fun startApp(name: String) = inputManager.startApp(name)

    fun resetVideo() = inputManager.resetVideo()

    fun setVideoEnabled(enabled: Boolean) = inputManager.setVideoEnabled(enabled)

    fun setAudioEnabled(enabled: Boolean) = inputManager.setAudioEnabled(enabled)

    private fun runController() {
        var error = false
        try {
            while (!stopped) {
                val msg = queue.dequeue { stopped } ?: break
                output.writeByte(msg.type)
                output.write(msg.payload)
                output.flush()
            }
        } catch (e: IOException) {
            Log.d(TAG, "Controller thread stopped by IO close", e)
            error = false
        } catch (t: Throwable) {
            Log.e(TAG, "Controller thread failed", t)
            error = !closeRequested
        } finally {
            stopped = true
            signalQueue()
            Log.d(TAG, "Controller thread ended, error=$error")
            reportEnded(error)
        }
    }

    private fun runReceiver() {
        var error = false
        try {
            receiver.run { stopped }
        } catch (e: IOException) {
            Log.d(TAG, "Controller receiver stopped by IO close", e)
            error = false
        } catch (t: Throwable) {
            Log.e(TAG, "Controller receiver failed", t)
            error = !closeRequested
        } finally {
            stopped = true
            signalQueue()
            Log.d(TAG, "Controller receiver thread ended, error=$error")
            reportEnded(error)
        }
    }

    private fun pushMessage(msg: ControllerOutgoingMessage) {
        if (!enqueueMessage(msg)) {
            throw IllegalStateException("发送控制消息失败(type=${msg.type}): 控制器已停止")
        }
    }

    private fun enqueueMessage(msg: ControllerOutgoingMessage): Boolean {
        val enqueued = queue.enqueue(msg) { stopped }
        if (enqueued) {
            return true
        }

        if (stopped) {
            Log.w(TAG, "Drop message type=${msg.type}: controller stopped")
        } else if (msg.droppable) {
            Log.w(TAG, "Drop droppable control message type=${msg.type}: queue full size=${queue.size()}")
        } else {
            Log.w(TAG, "Queue full but keeping non-droppable message type=${msg.type}, size=${queue.size()}")
        }
        return false
    }

    private fun signalQueue() {
        queue.signalAll()
    }

    private fun reportEnded(error: Boolean) {
        if (closeRequested || endedReported) {
            return
        }
        endedReported = true
        Log.i(TAG, "Controller ended, error=$error")
        runCatching { callbacks.onEnded(this, error) }
    }

    private fun warnIfClamped(label: String, value: Int) {
        val clamped = ControlMessage.clampToUnsignedShort(value)
        if (clamped != value) {
            Log.w(TAG, "Clamp $label from $value to $clamped")
        }
    }

    override fun close() {
        Log.d(TAG, "Closing controller")
        closeRequested = true
        stopped = true
        signalQueue()

        runCatching { channel.close() }

        runCatching { controllerThread.interrupt() }
        runCatching { receiverThread.interrupt() }

        runCatching { controllerThread.join() }
        runCatching { receiverThread.join() }
        Log.i(TAG, "Controller closed")
    }

    private companion object {
        private const val TAG = "ScrcpyController"
        private const val CONTROL_MSG_QUEUE_LIMIT = 60
        private const val CLIPBOARD_TIMEOUT_MS = 5_000L
    }
}

internal data class ControllerOutgoingMessage(
    val type: Int,
    val payload: ByteArray,
    val droppable: Boolean,
)

internal class ControllerMessageQueue(
    private val limit: Int,
) {
    private val queue = ArrayDeque<ControllerOutgoingMessage>()
    private val lock = Object()

    fun enqueue(message: ControllerOutgoingMessage, isStopped: () -> Boolean): Boolean {
        synchronized(lock) {
            if (isStopped()) {
                return false
            }

            if (queue.size < limit) {
                queue.addLast(message)
                lock.notifyAll()
                return true
            }

            if (!message.droppable) {
                queue.addLast(message)
                lock.notifyAll()
                return true
            }

            return false
        }
    }

    fun dequeue(isStopped: () -> Boolean): ControllerOutgoingMessage? {
        synchronized(lock) {
            while (!isStopped() && queue.isEmpty()) {
                lock.wait()
            }
            if (isStopped()) {
                return null
            }
            return queue.removeFirst()
        }
    }

    fun size(): Int {
        synchronized(lock) {
            return queue.size
        }
    }

    fun signalAll() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }
}

internal class InputManager(
    private val tag: String,
    private val enqueue: (ControllerOutgoingMessage) -> Unit,
    private val warnIfClamped: (label: String, value: Int) -> Unit,
) {
    fun injectKeycode(action: Int, keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        Log.d(tag, "Enqueue key event: action=$action keycode=$keycode repeat=$repeat metaState=$metaState")
        val payload = ControlMessage.encodeInjectKeycode(action, keycode, repeat, metaState)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.INJECT_KEYCODE, payload, droppable = true))
    }

    fun keyDown(keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        injectKeycode(KeyEvent.ACTION_DOWN, keycode, repeat, metaState)
    }

    fun keyUp(keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        injectKeycode(KeyEvent.ACTION_UP, keycode, repeat, metaState)
    }

    fun pressKeycode(keycode: Int, metaState: Int = 0) {
        keyDown(keycode, repeat = 0, metaState = metaState)
        keyUp(keycode, repeat = 0, metaState = metaState)
    }

    fun pressHome() = pressKeycode(KeyEvent.KEYCODE_HOME)
    fun pressBack() = pressKeycode(KeyEvent.KEYCODE_BACK)
    fun pressAppSwitch() = pressKeycode(KeyEvent.KEYCODE_APP_SWITCH)
    fun pressPower() = pressKeycode(KeyEvent.KEYCODE_POWER)
    fun pressVolumeUp() = pressKeycode(KeyEvent.KEYCODE_VOLUME_UP)
    fun pressVolumeDown() = pressKeycode(KeyEvent.KEYCODE_VOLUME_DOWN)
    fun pressMenu() = pressKeycode(KeyEvent.KEYCODE_MENU)

    fun injectText(text: String) {
        val payload = ControlMessage.encodeUtf8Message(text, ControlMessage.INJECT_TEXT_MAX_LENGTH)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.INJECT_TEXT, payload, droppable = true))
    }

    fun injectTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int,
        buttons: Int,
    ) {
        if (action != MotionEvent.ACTION_MOVE) {
            Log.d(
                tag,
                "Enqueue touch event: action=$action pointerId=$pointerId x=$x y=$y screen=${screenWidth}x$screenHeight pressure=$pressure buttons=$buttons",
            )
        }
        warnIfClamped("touch.screenWidth", screenWidth)
        warnIfClamped("touch.screenHeight", screenHeight)
        val payload = ControlMessage.encodeTouchEvent(
            action = action,
            pointerId = pointerId,
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pressure = pressure,
            actionButton = actionButton,
            buttons = buttons,
        )
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.INJECT_TOUCH_EVENT, payload, droppable = isDroppableTouchAction(action)))
    }

    fun injectMultiTouchEvent(
        action: Int,
        actionIndex: Int,
        pointers: List<TouchPoint>,
        screenWidth: Int,
        screenHeight: Int,
        actionButton: Int,
        buttons: Int,
    ) {
        if (action != MotionEvent.ACTION_MOVE) {
            Log.d(
                tag,
                "Enqueue multi-touch event: action=$action actionIndex=$actionIndex pointers=${pointers.size} screen=${screenWidth}x$screenHeight buttons=$buttons",
            )
        }
        warnIfClamped("touch.screenWidth", screenWidth)
        warnIfClamped("touch.screenHeight", screenHeight)
        val payload = ControlMessage.encodeMultiTouchEvent(
            action = action,
            actionIndex = actionIndex,
            pointers = pointers,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            actionButton = actionButton,
            buttons = buttons,
        )
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.INJECT_MULTI_TOUCH_EVENT, payload, droppable = isDroppableTouchAction(action)))
    }

    private fun isDroppableTouchAction(action: Int): Boolean {
        return action == MotionEvent.ACTION_MOVE
    }

    fun injectScrollEvent(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int,
    ) {
        Log.d(tag, "Enqueue scroll event: x=$x y=$y screen=${screenWidth}x$screenHeight hScroll=$hScroll vScroll=$vScroll buttons=$buttons")
        warnIfClamped("scroll.screenWidth", screenWidth)
        warnIfClamped("scroll.screenHeight", screenHeight)
        val payload = ControlMessage.encodeScrollEvent(x, y, screenWidth, screenHeight, hScroll, vScroll, buttons)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.INJECT_SCROLL_EVENT, payload, droppable = true))
    }

    fun backOrScreenOn(action: Int) {
        require(action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
            "Invalid backOrScreenOn action: $action"
        }
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.BACK_OR_SCREEN_ON, byteArrayOf(action.toByte()), droppable = true))
    }

    fun pressBackOrScreenOn() {
        backOrScreenOn(KeyEvent.ACTION_DOWN)
        backOrScreenOn(KeyEvent.ACTION_UP)
    }

    fun expandNotificationPanel() {
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.EXPAND_NOTIFICATION_PANEL, ControlMessage.EMPTY, droppable = true))
    }

    fun expandSettingsPanel() {
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.EXPAND_SETTINGS_PANEL, ControlMessage.EMPTY, droppable = true))
    }

    fun collapsePanels() {
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.COLLAPSE_PANELS, ControlMessage.EMPTY, droppable = true))
    }

    fun getClipboard(copyKey: Int = ControlMessage.CopyKey.NONE) {
        require(copyKey in ControlMessage.CopyKey.NONE..ControlMessage.CopyKey.CUT) { "Invalid copyKey: $copyKey" }
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.GET_CLIPBOARD, byteArrayOf(copyKey.toByte()), droppable = true))
    }

    fun setClipboard(sequence: Long, text: String, paste: Boolean) {
        val payload = ControlMessage.encodeSetClipboard(sequence, text, paste)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.SET_CLIPBOARD, payload, droppable = true))
    }

    fun setDisplayPower(on: Boolean) {
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.SET_DISPLAY_POWER, byteArrayOf(if (on) 1 else 0), droppable = true))
    }

    fun rotateDevice() {
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.ROTATE_DEVICE, ControlMessage.EMPTY, droppable = true))
    }

    fun uhidCreate(id: Int, vendorId: Int, productId: Int, name: String, reportDesc: ByteArray) {
        val payload = ControlMessage.encodeUhidCreate(id, vendorId, productId, name, reportDesc)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.UHID_CREATE, payload, droppable = false))
    }

    fun uhidInput(id: Int, data: ByteArray) {
        val payload = ControlMessage.encodeUhidInput(id, data)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.UHID_INPUT, payload, droppable = true))
    }

    fun uhidDestroy(id: Int) {
        val payload = ControlMessage.encodeUnsignedShort(id)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.UHID_DESTROY, payload, droppable = false))
    }

    fun openHardKeyboardSettings() {
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.OPEN_HARD_KEYBOARD_SETTINGS, ControlMessage.EMPTY, droppable = true))
    }

    fun startApp(name: String) {
        val payload = ControlMessage.encodeTinyUtf8Message(name, 255)
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.START_APP, payload, droppable = true))
    }

    fun resetVideo() {
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.RESET_VIDEO, ControlMessage.EMPTY, droppable = true))
    }

    fun setVideoEnabled(enabled: Boolean) {
        Log.d(tag, "Queue video enabled=$enabled")
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.SET_VIDEO_ENABLED, byteArrayOf(if (enabled) 1 else 0), droppable = true))
    }

    fun setAudioEnabled(enabled: Boolean) {
        Log.d(tag, "Queue audio enabled=$enabled")
        enqueue(ControllerOutgoingMessage(ControlMessage.Type.SET_AUDIO_ENABLED, byteArrayOf(if (enabled) 1 else 0), droppable = true))
    }
}

internal class Receiver(
    private val tag: String,
    private val input: DataInputStream,
    private val controllerProvider: () -> Controller,
    private val callbacksProvider: () -> Controller.Callbacks,
    private val clipboardResponses: LinkedBlockingQueue<String>,
) {
    @Throws(IOException::class)
    fun run(isStopped: () -> Boolean) {
        while (!isStopped()) {
            processNextMessage()
        }
    }

    @Throws(IOException::class)
    private fun processNextMessage() {
        when (val type = input.readUnsignedByte()) {
            DeviceMessage.Type.CLIPBOARD -> {
                val data = DeviceMessage.readByteArray(input, input.readInt(), "clipboard")
                clipboardResponses.offer(String(data, StandardCharsets.UTF_8))
            }

            DeviceMessage.Type.ACK_CLIPBOARD -> {
                val sequence = input.readLong()
                Log.d(tag, "Receive clipboard ack sequence=$sequence")
            }

            DeviceMessage.Type.UHID_OUTPUT -> {
                val id = input.readUnsignedShort()
                val size = input.readUnsignedShort()
                skipFully(size)
                Log.v(tag, "Receive UHID output id=$id size=$size")
            }

            DeviceMessage.Type.DISPLAY_ROTATION -> {
                val rotation = input.readUnsignedByte()
                Log.d(tag, "Receive display rotation=$rotation")
                runCatching { callbacksProvider().onDisplayRotationChanged(controllerProvider(), rotation) }
            }

            else -> {
                throw IllegalStateException("未知设备消息类型: $type")
            }
        }
    }

    @Throws(IOException::class)
    private fun skipFully(length: Int) {
        var remaining = length
        val buffer = ByteArray(4096)
        while (remaining > 0) {
            val chunk = minOf(remaining, buffer.size)
            input.readFully(buffer, 0, chunk)
            remaining -= chunk
        }
    }
}

internal object ControlMessage {
    const val MAX_SIZE = 1 shl 18
    const val INJECT_TEXT_MAX_LENGTH = 300
    const val CLIPBOARD_TEXT_MAX_LENGTH = MAX_SIZE - 14

    val EMPTY = ByteArray(0)

    object Type {
        const val INJECT_KEYCODE = 0
        const val INJECT_TEXT = 1
        const val INJECT_TOUCH_EVENT = 2
        const val INJECT_SCROLL_EVENT = 3
        const val BACK_OR_SCREEN_ON = 4
        const val EXPAND_NOTIFICATION_PANEL = 5
        const val EXPAND_SETTINGS_PANEL = 6
        const val COLLAPSE_PANELS = 7
        const val GET_CLIPBOARD = 8
        const val SET_CLIPBOARD = 9
        const val SET_DISPLAY_POWER = 10
        const val ROTATE_DEVICE = 11
        const val UHID_CREATE = 12
        const val UHID_INPUT = 13
        const val UHID_DESTROY = 14
        const val OPEN_HARD_KEYBOARD_SETTINGS = 15
        const val START_APP = 16
        const val RESET_VIDEO = 17
        const val SET_VIDEO_ENABLED = 20
        const val SET_AUDIO_ENABLED = 21
        const val INJECT_MULTI_TOUCH_EVENT = 22
    }

    object CopyKey {
        const val NONE = 0
        const val COPY = 1
        const val CUT = 2
    }

    fun encodeInjectKeycode(action: Int, keycode: Int, repeat: Int, metaState: Int): ByteArray {
        return ByteArray(13).apply {
            this[0] = action.toByte()
            writeInt(1, keycode)
            writeInt(5, repeat)
            writeInt(9, metaState)
        }
    }

    fun encodeUtf8Message(text: String, maxBytes: Int): ByteArray {
        val textBytes = truncateUtf8(text, maxBytes)
        return ByteArray(4 + textBytes.size).apply {
            writeInt(0, textBytes.size)
            System.arraycopy(textBytes, 0, this, 4, textBytes.size)
        }
    }

    fun encodeTinyUtf8Message(text: String, maxBytes: Int): ByteArray {
        require(maxBytes in 0..0xFF) { "maxBytes must fit in one byte: $maxBytes" }
        val textBytes = truncateUtf8(text, maxBytes)
        return ByteArray(1 + textBytes.size).apply {
            this[0] = textBytes.size.toByte()
            System.arraycopy(textBytes, 0, this, 1, textBytes.size)
        }
    }

    fun encodeTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int,
        buttons: Int,
    ): ByteArray {
        val encodedWidth = clampToUnsignedShort(screenWidth)
        val encodedHeight = clampToUnsignedShort(screenHeight)
        return ByteArray(31).apply {
            this[0] = action.toByte()
            writeLong(1, pointerId)
            writeInt(9, x)
            writeInt(13, y)
            writeShort(17, encodedWidth)
            writeShort(19, encodedHeight)
            writeShort(21, floatToU16FixedPoint(pressure))
            writeInt(23, actionButton)
            writeInt(27, buttons)
        }
    }

    fun encodeScrollEvent(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int,
    ): ByteArray {
        val encodedWidth = clampToUnsignedShort(screenWidth)
        val encodedHeight = clampToUnsignedShort(screenHeight)
        return ByteArray(20).apply {
            writeInt(0, x)
            writeInt(4, y)
            writeShort(8, encodedWidth)
            writeShort(10, encodedHeight)
            writeShort(12, floatToI16FixedPoint((hScroll / 16f).coerceIn(-1f, 1f)))
            writeShort(14, floatToI16FixedPoint((vScroll / 16f).coerceIn(-1f, 1f)))
            writeInt(16, buttons)
        }
    }

    fun encodeSetClipboard(sequence: Long, text: String, paste: Boolean): ByteArray {
        val textBytes = truncateUtf8(text, CLIPBOARD_TEXT_MAX_LENGTH)
        return ByteArray(8 + 1 + 4 + textBytes.size).apply {
            writeLong(0, sequence)
            this[8] = if (paste) 1 else 0
            writeInt(9, textBytes.size)
            System.arraycopy(textBytes, 0, this, 13, textBytes.size)
        }
    }

    fun encodeUhidCreate(
        id: Int,
        vendorId: Int,
        productId: Int,
        name: String,
        reportDesc: ByteArray,
    ): ByteArray {
        val nameBytes = truncateUtf8(name, 127)
        require(reportDesc.size <= 0xFFFF) { "reportDesc too long" }
        return ByteArray(2 + 2 + 2 + 1 + nameBytes.size + 2 + reportDesc.size).apply {
            var offset = 0
            writeShort(offset, id)
            offset += 2
            writeShort(offset, vendorId)
            offset += 2
            writeShort(offset, productId)
            offset += 2
            this[offset] = nameBytes.size.toByte()
            offset += 1
            System.arraycopy(nameBytes, 0, this, offset, nameBytes.size)
            offset += nameBytes.size
            writeShort(offset, reportDesc.size)
            offset += 2
            System.arraycopy(reportDesc, 0, this, offset, reportDesc.size)
            }
            }

    fun encodeMultiTouchEvent(
        action: Int,
        actionIndex: Int,
        pointers: List<TouchPoint>,
        screenWidth: Int,
        screenHeight: Int,
        actionButton: Int,
        buttons: Int,
    ): ByteArray {
        require(pointers.isNotEmpty()) { "pointers must not be empty" }
        require(pointers.size <= 0xFF) { "too many pointers: ${pointers.size}" }
        require(actionIndex in pointers.indices) { "actionIndex out of range: $actionIndex" }
        val encodedWidth = clampToUnsignedShort(screenWidth)
        val encodedHeight = clampToUnsignedShort(screenHeight)
        return ByteArray(15 + pointers.size * 18).apply {
            this[0] = action.toByte()
            this[1] = actionIndex.toByte()
            this[2] = pointers.size.toByte()
            writeShort(3, encodedWidth)
            writeShort(5, encodedHeight)
            writeInt(7, actionButton)
            writeInt(11, buttons)

            var offset = 15
            for (pointer in pointers) {
                writeLong(offset, pointer.pointerId)
                offset += 8
                writeInt(offset, pointer.x)
                offset += 4
                writeInt(offset, pointer.y)
                offset += 4
                writeShort(offset, floatToU16FixedPoint(pointer.pressure))
                offset += 2
            }
        }
    }

    fun encodeUhidInput(id: Int, data: ByteArray): ByteArray {
        require(data.size <= 0xFFFF) { "uhid input too long" }
        return ByteArray(2 + 2 + data.size).apply {
            writeShort(0, id)
            writeShort(2, data.size)
            System.arraycopy(data, 0, this, 4, data.size)
        }
    }

    fun encodeUnsignedShort(value: Int): ByteArray {
        return ByteArray(2).apply {
            writeShort(0, value)
        }
    }

    fun truncateUtf8(value: String, maxBytes: Int): ByteArray {
        if (maxBytes <= 0 || value.isEmpty()) {
            return EMPTY
        }

        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        if (encoded.size <= maxBytes) {
            return encoded
        }

        var size = 0
        var endIndex = 0
        while (endIndex < value.length) {
            val codePoint = Character.codePointAt(value, endIndex)
            val bytes = utf8Length(codePoint)
            if (size + bytes > maxBytes) {
                break
            }
            size += bytes
            endIndex += Character.charCount(codePoint)
        }
        return value.substring(0, endIndex).toByteArray(StandardCharsets.UTF_8)
    }

    fun clampToUnsignedShort(value: Int): Int {
        return value.coerceIn(0, 0xFFFF)
    }

    private fun ByteArray.writeShort(offset: Int, value: Int) {
        this[offset] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 1] = (value and 0xFF).toByte()
    }

    private fun ByteArray.writeInt(offset: Int, value: Int) {
        this[offset] = ((value ushr 24) and 0xFF).toByte()
        this[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 3] = (value and 0xFF).toByte()
    }

    private fun ByteArray.writeLong(offset: Int, value: Long) {
        this[offset] = ((value ushr 56) and 0xFF).toByte()
        this[offset + 1] = ((value ushr 48) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 40) and 0xFF).toByte()
        this[offset + 3] = ((value ushr 32) and 0xFF).toByte()
        this[offset + 4] = ((value ushr 24) and 0xFF).toByte()
        this[offset + 5] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 6] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 7] = (value and 0xFF).toByte()
    }

    private fun floatToU16FixedPoint(value: Float): Int {
        val clamped = value.coerceIn(0f, 1f)
        return (clamped * 0xFFFF).toInt() and 0xFFFF
    }

    private fun floatToI16FixedPoint(value: Float): Int {
        val clamped = value.coerceIn(-1f, 1f)
        val scaled = (clamped * 0x7FFF).toInt()
        return scaled and 0xFFFF
    }

    private fun utf8Length(codePoint: Int): Int {
        return when {
            codePoint <= 0x7F -> 1
            codePoint <= 0x7FF -> 2
            codePoint <= 0xFFFF -> 3
            else -> 4
        }
    }
}

internal object DeviceMessage {
    object Type {
        const val CLIPBOARD = 0
        const val ACK_CLIPBOARD = 1
        const val UHID_OUTPUT = 2
        const val DISPLAY_ROTATION = 4
    }

    @Throws(IOException::class)
    fun readByteArray(input: DataInputStream, length: Int, label: String): ByteArray {
        require(length >= 0) { "Negative $label length: $length" }
        return ByteArray(length).also { input.readFully(it) }
    }
}
