package com.genymobile.scrcpy.control;

public final class DeviceMessage {

    public static final int TYPE_CLIPBOARD = 0;
    public static final int TYPE_ACK_CLIPBOARD = 1;
    public static final int TYPE_UHID_OUTPUT = 2;
    public static final int TYPE_GPS_OPEN = 3;
    public static final int TYPE_DISPLAY_ROTATION = 4;

    private int type;
    private String text;
    private long sequence;
    private int id;
    private int rotation;
    private byte[] data;

    private DeviceMessage() {
    }

    public static DeviceMessage createClipboard(String text) {
        DeviceMessage event = new DeviceMessage();
        event.type = TYPE_CLIPBOARD;
        event.text = text;
        return event;
    }

    public static DeviceMessage createAckClipboard(long sequence) {
        DeviceMessage event = new DeviceMessage();
        event.type = TYPE_ACK_CLIPBOARD;
        event.sequence = sequence;
        return event;
    }

    public static DeviceMessage createUhidOutput(int id, byte[] data) {
        DeviceMessage event = new DeviceMessage();
        event.type = TYPE_UHID_OUTPUT;
        event.id = id;
        event.data = data;
        return event;
    }

    public static DeviceMessage createGpsOpen() {
        DeviceMessage event = new DeviceMessage();
        event.type = TYPE_GPS_OPEN;
        return event;
    }

    public static DeviceMessage createDisplayRotation(int rotation) {
        DeviceMessage event = new DeviceMessage();
        event.type = TYPE_DISPLAY_ROTATION;
        event.rotation = rotation;
        return event;
    }

    public int getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public long getSequence() {
        return sequence;
    }

    public int getId() {
        return id;
    }

    public int getRotation() {
        return rotation;
    }

    public byte[] getData() {
        return data;
    }
}
