package jake2.qcommon.network.messages.server;

import jake2.qcommon.SZ;
import jake2.qcommon.sizebuf_t;

import java.util.Arrays;

/**
 * Transmits chunk of media (maps, skins, sounds, etc)
 *
 * Fixme: Warning: data.length is limited by signed short (32k)
 */
public class DownloadMessage extends ServerMessage {

    public byte[] data;
    public byte percentage;

    public DownloadMessage() {
        super(ServerMessageType.svc_download);
    }

    public DownloadMessage(byte[] data, byte percentage) {
        this();
        this.data = data;
        this.percentage = percentage;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        if (data != null) {
            buffer.WriteShort(data.length);
            sizebuf_t.WriteByte(buffer, percentage);
            SZ.Write(buffer, data, data.length);
        } else {
            buffer.WriteShort(-1);
            sizebuf_t.WriteByte(buffer, (byte) 0);
        }
    }

    @Override
    public void parse(sizebuf_t buffer) {
        int size = sizebuf_t.ReadShort(buffer);
        percentage = (byte) sizebuf_t.ReadByte(buffer);
        if (size != -1) {
            data = new byte[size];
            sizebuf_t.ReadData(buffer, data, size);
        }
    }

    @Override
    public int getSize() {
        if (data != null)
            return 4 + data.length;
        else
            return 4;
    }

    @Override
    public String toString() {
        return "DownloadMessage{" +
                "percentage=" + percentage +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DownloadMessage that = (DownloadMessage) o;

        if (percentage != that.percentage) return false;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(data);
        result = 31 * result + percentage;
        return result;
    }
}
